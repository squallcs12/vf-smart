#include "remote_frunk_control.h"
#include "../pins.h"
#include "../pcf8575_io.h"

// Timer for tracking how long remote unlock button is held
static unsigned long remote_unlock_press_start = 0;
static bool frunk_triggered = false;

// Rate limiting (same as HTTP endpoint)
static unsigned long last_frunk_unlock = 0;
#define FRUNK_UNLOCK_COOLDOWN 2000  // 2 seconds between operations
#define REMOTE_HOLD_DURATION 1000   // 1 second hold required

void handleRemoteFrunkControl() {
  // Detect when remote unlock button is pressed (LOW to HIGH transition)
  static int prev_remote_unlock_press = LOW;

  if (vf3_remote_unlock_press == HIGH && prev_remote_unlock_press == LOW) {
    // Button just pressed - start timer
    remote_unlock_press_start = millis();
    frunk_triggered = false;
    Serial.println("Remote frunk control: Unlock button pressed, timer started");
  }

  // Check if button is still held and 1 second has elapsed
  if (vf3_remote_unlock_press == HIGH &&
      remote_unlock_press_start != 0 &&
      !frunk_triggered &&
      (millis() - remote_unlock_press_start >= REMOTE_HOLD_DURATION)) {

    // Check rate limiting
    if (millis() - last_frunk_unlock >= FRUNK_UNLOCK_COOLDOWN) {
      // Safety check: prevent operation while in Drive
      if (vf3_gear_drive == LOW) {
        Serial.println("Remote frunk control: 1 second hold detected, opening front trunk");

        // Trigger front trunk unlock (8 second pulse)
        safeDigitalWrite(VF3_FRONT_TRUNK_UNLOCK, WRITE_ON);
        delay(8000);
        safeDigitalWrite(VF3_FRONT_TRUNK_UNLOCK, WRITE_OFF);

        last_frunk_unlock = millis();
        frunk_triggered = true;
      } else {
        Serial.println("Remote frunk control: Cannot open front trunk while vehicle is in Drive");
        frunk_triggered = true;  // Prevent retry while still held
      }
    } else {
      Serial.println("Remote frunk control: Rate limit active, skipping");
      frunk_triggered = true;  // Prevent retry while still held
    }
  }

  // Reset timer when button is released
  if (vf3_remote_unlock_press == LOW && prev_remote_unlock_press == HIGH) {
    Serial.println("Remote frunk control: Unlock button released, timer reset");
    remote_unlock_press_start = 0;
    frunk_triggered = false;
  }

  prev_remote_unlock_press = vf3_remote_unlock_press;
}
