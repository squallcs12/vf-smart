#include "controls.h"

void handleWindowControl() {
  // Auto close windows when car is locked (on for 30s, then off)
  if (vf3_car_lock == HIGH) {
    // Lock signal detected, start/reset timer
    window_close_timer = millis();
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
  } else if (window_close_timer != 0 && millis() - window_close_timer < WINDOW_CLOSE_DURATION) {
    // Keep windows closing for 30 seconds
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
  } else {
    // Timer expired or lock not active
    digitalWrite(VF3_WINDOW_LEFT, LOW);
    digitalWrite(VF3_WINDOW_RIGHT, LOW);
    window_close_timer = 0;
  }
}

void handleAccessoryPower() {
  // Turn off accessory power when car is locked
  if (vf3_car_lock == HIGH) {
    digitalWrite(VF3_ACCESSORY_POWER, LOW);
    vf3_accessory_power = LOW;
  }

  // Turn on accessory power when car is unlocked
  if (vf3_car_unlock == HIGH) {
    digitalWrite(VF3_ACCESSORY_POWER, HIGH);
    vf3_accessory_power = HIGH;
  }
}

void handleLightReminder() {
  // Only remind if:
  // 1. Time is synced
  // 2. It's nighttime (6pm - 6am)
  // 3. Gear is in Drive (D)
  // 4. Normal light is off

  // Guard clause: Time not synced yet
  if (!time_synced) {
    return;
  }

  // Guard clause: Not nighttime
  if (!isNightTime()) {
    last_light_reminder = 0;
    return;
  }

  // Guard clause: Not in drive
  if (vf3_gear_drive != HIGH) {
    last_light_reminder = 0;
    return;
  }

  // Guard clause: Normal light is on
  if (vf3_normal_light != LOW) {
    last_light_reminder = 0;
    return;
  }

  // All conditions met, check if enough time has passed since last reminder
  unsigned long current_time = millis();

  if (current_time - last_light_reminder < LIGHT_REMINDER_INTERVAL) {
    return;
  }

  // Trigger reminder beep
  digitalWrite(VF3_BUZZER, HIGH);
  delay(LIGHT_REMINDER_BEEP_DURATION);
  digitalWrite(VF3_BUZZER, LOW);

  // Update last reminder time
  last_light_reminder = current_time;

  Serial.println("Light reminder: Please turn on headlights!");
}
