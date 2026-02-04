#include "car_state.h"

// Global car lock state - default is UNLOCKED on boot
CarLockState car_lock_state = CAR_UNLOCKED;

void handleCarState() {
  static int prev_lock_pin = LOW;
  static int prev_unlock_pin = LOW;

  // Detect lock button pulse (rising edge: LOW to HIGH)
  if (vf3_car_lock == HIGH && prev_lock_pin == LOW) {
    car_lock_state = CAR_LOCKED;
    Serial.println("Car state: LOCKED");
  }

  // Detect unlock button pulse (rising edge: LOW to HIGH)
  if (vf3_car_unlock == HIGH && prev_unlock_pin == LOW) {
    car_lock_state = CAR_UNLOCKED;
    Serial.println("Car state: UNLOCKED");
  }

  prev_lock_pin = vf3_car_lock;
  prev_unlock_pin = vf3_car_unlock;
}
