#include "controls.h"

void handleWindowControl() {
  // Auto close windows when car is locked (on for 30s, then off)
  if (vf3_car_lock == HIGH) {
    // Lock signal detected, start/reset timer
    window_close_timer = millis();
    pcfDigitalWrite(VF3_WINDOW_LEFT, WRITE_OFF);
    pcfDigitalWrite(VF3_WINDOW_RIGHT, WRITE_OFF);
  } else if (window_close_timer != 0 && millis() - window_close_timer < WINDOW_CLOSE_DURATION) {
    // Keep windows closing for 30 seconds
    pcfDigitalWrite(VF3_WINDOW_LEFT, WRITE_OFF);
    pcfDigitalWrite(VF3_WINDOW_RIGHT, WRITE_OFF);
  } else {
    // Timer expired or lock not active
    pcfDigitalWrite(VF3_WINDOW_LEFT, WRITE_ON);
    pcfDigitalWrite(VF3_WINDOW_RIGHT, WRITE_ON);
    window_close_timer = 0;
  }
}

void handleAccessoryPower() {
  // Turn off accessory power when car is locked
  if (vf3_car_lock == HIGH) {
    pcfDigitalWrite(SELF_ACCESSORY_POWER, WRITE_ON);
    self_accessory_power = LOW;
  }

  // Turn on accessory power when car is unlocked
  if (vf3_car_unlock == HIGH) {
    pcfDigitalWrite(SELF_ACCESSORY_POWER, WRITE_OFF);
    self_accessory_power = HIGH;
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
  pcfDigitalWrite(VF3_BUZZER, WRITE_OFF);
  delay(LIGHT_REMINDER_BEEP_DURATION);
  pcfDigitalWrite(VF3_BUZZER, WRITE_ON);

  // Update last reminder time
  last_light_reminder = current_time;

  Serial.println("Light reminder: Please turn on headlights!");
}
