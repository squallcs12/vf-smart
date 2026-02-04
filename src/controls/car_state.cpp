#include "car_state.h"

// Global car lock state - default is UNLOCKED on boot
CarLockState car_lock_state = CAR_UNLOCKED;

// Buzzer timer for window open alert
static unsigned long buzzer_timer = 0;
static const unsigned long BUZZER_DURATION = 1000; // 1 second beep

void handleCarState() {
  static int prev_lock_pin = LOW;
  static int prev_unlock_pin = LOW;
  static CarLockState prev_car_lock_state = CAR_UNLOCKED;

  // Detect lock button pulse (rising edge: LOW to HIGH)
  if (vf3_car_lock == HIGH && prev_lock_pin == LOW) {
    car_lock_state = CAR_LOCKED;
    Serial.println("Car state: LOCKED");

    // Check if windows are open when transitioning from UNLOCKED to LOCKED
    if (prev_car_lock_state == CAR_UNLOCKED) {
      if (vf3_window_left_state == HIGH || vf3_window_right_state == HIGH) {
        // Windows are open - trigger buzzer alert
        Serial.println("Car state: Windows open while locking - buzzer alert!");
        pcf8575.digitalWrite(VF3_BUZZER, WRITE_ON);
        buzzer_timer = millis();
      }
    }
  }

  // Detect unlock button pulse (rising edge: LOW to HIGH)
  if (vf3_car_unlock == HIGH && prev_unlock_pin == LOW) {
    car_lock_state = CAR_UNLOCKED;
    Serial.println("Car state: UNLOCKED");
  }

  // Handle buzzer timer (turn off after duration)
  if (buzzer_timer != 0 && millis() - buzzer_timer >= BUZZER_DURATION) {
    pcf8575.digitalWrite(VF3_BUZZER, WRITE_OFF);
    buzzer_timer = 0;
    Serial.println("Car state: Buzzer alert ended");
  }

  prev_lock_pin = vf3_car_lock;
  prev_unlock_pin = vf3_car_unlock;
  prev_car_lock_state = car_lock_state;
}
