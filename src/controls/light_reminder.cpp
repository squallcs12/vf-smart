#include "light_reminder.h"

void handleLightReminder() {
  // Only remind if:
  // 1. Light reminder is enabled
  // 2. Time is synced
  // 3. It's nighttime (6pm - 6am)
  // 4. Gear is in Drive (D)
  // 5. Normal light is off

  // Guard clause: Light reminder disabled
  if (!light_reminder_enabled) {
    last_light_reminder = 0;
    return;
  }

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
  pcf8575.digitalWrite(VF3_BUZZER, WRITE_OFF);
  delay(LIGHT_REMINDER_BEEP_DURATION);
  pcf8575.digitalWrite(VF3_BUZZER, WRITE_ON);

  // Update last reminder time
  last_light_reminder = current_time;

  Serial.println("Light reminder: Please turn on headlights!");
}
