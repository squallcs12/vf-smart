#include "window_control.h"

void handleWindowControl() {
  // Close windows after 3 car lock pulses
  static int lock_pulse_count = 0;
  static int prev_car_lock = LOW;
  static unsigned long last_pulse_time = 0;
  const unsigned long PULSE_TIMEOUT = 5000;  // 5 seconds timeout between pulses

  // Reset pulse count if timeout exceeded
  if (lock_pulse_count > 0 && (millis() - last_pulse_time) > PULSE_TIMEOUT) {
    lock_pulse_count = 0;
    Serial.println("Window control: Pulse count reset due to timeout");
  }

  // Detect rising edge (LOW to HIGH transition) of car lock signal
  if (vf3_car_lock == HIGH && prev_car_lock == LOW) {
    lock_pulse_count++;
    last_pulse_time = millis();
    Serial.print("Window control: Lock pulse detected, count = ");
    Serial.println(lock_pulse_count);

    // Start window closing after 3 pulses
    if (lock_pulse_count >= 3) {
      Serial.println("Window control: 3 pulses detected, closing windows");
      window_close_timer = millis();
      lock_pulse_count = 0;  // Reset count
    }
  }

  prev_car_lock = vf3_car_lock;

  // Handle window closing timer (30 second duration)
  if (window_close_timer != 0 && millis() - window_close_timer < WINDOW_CLOSE_DURATION) {
    // Keep windows closing for 30 seconds
    pcf8575.digitalWrite(VF3_WINDOW_LEFT_UP, WRITE_OFF);
    pcf8575.digitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_OFF);
  } else if (window_close_timer != 0) {
    // Timer expired, stop windows
    pcf8575.digitalWrite(VF3_WINDOW_LEFT_UP, WRITE_ON);
    pcf8575.digitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_ON);
    window_close_timer = 0;
    Serial.println("Window control: Window closing completed");
  }
}
