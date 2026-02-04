#include "accessory_power.h"

void handleAccessoryPower() {
  // Turn off accessory power when car is locked
  if (vf3_car_lock == HIGH) {
    pcf8575.digitalWrite(SELF_ACCESSORY_POWER, WRITE_ON);
    self_accessory_power = LOW;
  }

  // Turn on accessory power when car is unlocked
  if (vf3_car_unlock == HIGH) {
    pcf8575.digitalWrite(SELF_ACCESSORY_POWER, WRITE_OFF);
    self_accessory_power = HIGH;
  }
}
