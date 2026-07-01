#include "charging_control.h"

static int prev_charging_status = -1;  // Track previous state (-1 = unknown at boot)

// Charger unlock runs as a two-phase pulse sequence:
//   phase 1: PULL pin ON for 1s, then OFF
//   phase 2: PUSH pin ON for 1s, then OFF
// charger_unlock_phase: 0 = idle, 1 = pull active, 2 = push active
static int charger_unlock_phase = 0;
static unsigned long charger_unlock_timer = 0;  // Start time of the current phase

void startChargerUnlock() {
  // Begin phase 1: pull pin ON
  digitalWrite(VF3_CHARGER_UNLOCK_PULL, WRITE_ON);
  charger_unlock_phase = 1;
  charger_unlock_timer = millis();
}

void handleChargingControl() {
  // Detect charging status change
  if (vf3_charging_status != prev_charging_status && prev_charging_status != -1) {
    // Charging stopped (transitioned from HIGH to LOW)
    if (prev_charging_status == HIGH && vf3_charging_status == LOW) {
      Serial.println("Charging stopped - unlocking charger port");
      startChargerUnlock();
    }

    prev_charging_status = vf3_charging_status;
  }

  // Initialize prev_charging_status on first run
  if (prev_charging_status == -1) {
    prev_charging_status = vf3_charging_status;
  }

  // Advance the unlock pulse sequence (each phase lasts 1 second)
  if (charger_unlock_phase != 0 && (millis() - charger_unlock_timer) >= 1000) {
    if (charger_unlock_phase == 1) {
      // Phase 1 done: pull OFF, then start phase 2 (push ON)
      digitalWrite(VF3_CHARGER_UNLOCK_PULL, WRITE_OFF);
      digitalWrite(VF3_CHARGER_UNLOCK_PUSH, WRITE_ON);
      charger_unlock_phase = 2;
      charger_unlock_timer = millis();
    } else {
      // Phase 2 done: push OFF, sequence complete
      digitalWrite(VF3_CHARGER_UNLOCK_PUSH, WRITE_OFF);
      charger_unlock_phase = 0;
      charger_unlock_timer = 0;
      Serial.println("Charger port unlocked");
    }
  }
}
