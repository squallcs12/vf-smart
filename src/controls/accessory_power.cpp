#include "accessory_power.h"

static int prev_accessory_power = HIGH;  // Track previous state to detect changes
static unsigned long mirror_timer = 0;   // Timer for mirror operation (1 second pulse)

void handleAccessoryPower() {
  // Turn off accessory power when car is locked
  if (vf3_car_lock == HIGH) {
    pcf8575.digitalWrite(SELF_ACCESSORY_POWER, WRITE_ON);
    self_accessory_power = LOW;  // Logical OFF state
  }

  // Turn on accessory power when car is unlocked
  if (vf3_car_unlock == HIGH) {
    pcf8575.digitalWrite(SELF_ACCESSORY_POWER, WRITE_OFF);
    self_accessory_power = HIGH;  // Logical ON state
  }

  // Detect accessory power state change
  if (self_accessory_power != prev_accessory_power) {
    if (self_accessory_power == HIGH) {
      // Accessory power turned ON - enable related accessories
      Serial.println("Accessory power ON - enabling accessories");

      // Turn on inside cameras
      pcf8575.digitalWrite(SELF_INSIDE_CARMERAS, WRITE_ON);
      self_inside_cameras = WRITE_ON;

      // Start opening side mirrors (1 second pulse)
      pcf8575.digitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_ON);
      mirror_timer = millis();

      Serial.println("Inside cameras ON - Opening mirrors");

    } else {
      // Accessory power turned OFF - disable related accessories
      Serial.println("Accessory power OFF - disabling accessories");

      // Turn off inside cameras
      pcf8575.digitalWrite(SELF_INSIDE_CARMERAS, WRITE_OFF);
      self_inside_cameras = WRITE_OFF;

      // Start closing side mirrors (1 second pulse)
      pcf8575.digitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_ON);
      mirror_timer = millis();

      Serial.println("Inside cameras OFF - Closing mirrors");
    }

    prev_accessory_power = self_accessory_power;
  }

  // Handle mirror timer (turn off after 1 second)
  if (mirror_timer > 0 && (millis() - mirror_timer) >= 1000) {
    pcf8575.digitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_OFF);
    pcf8575.digitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_OFF);
    mirror_timer = 0;
    Serial.println("Mirror operation completed");
  }
}
