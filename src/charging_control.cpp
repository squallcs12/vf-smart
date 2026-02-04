#include "charging_control.h"

static int prev_charging_status = -1;  // Track previous state (-1 = unknown at boot)
static unsigned long charger_unlock_timer = 0;  // Timer for charger unlock pulse

void handleChargingControl() {
  // Detect charging status change
  if (vf3_charging_status != prev_charging_status && prev_charging_status != -1) {
    // Charging stopped (transitioned from HIGH to LOW)
    if (prev_charging_status == HIGH && vf3_charging_status == LOW) {
      Serial.println("Charging stopped - unlocking charger port");

      // Trigger charger unlock (1 second pulse)
      pcf8575.digitalWrite(VF3_CHARGER_UNLOCK, WRITE_ON);
      charger_unlock_timer = millis();
    }

    prev_charging_status = vf3_charging_status;
  }

  // Initialize prev_charging_status on first run
  if (prev_charging_status == -1) {
    prev_charging_status = vf3_charging_status;
  }

  // Handle charger unlock timer (turn off after 1 second)
  if (charger_unlock_timer > 0 && (millis() - charger_unlock_timer) >= 1000) {
    pcf8575.digitalWrite(VF3_CHARGER_UNLOCK, WRITE_OFF);
    charger_unlock_timer = 0;
    Serial.println("Charger port unlocked");
  }
}
