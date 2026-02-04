#include "window_control.h"

void handleWindowControl() {
  // Auto close windows when car is locked (on for 30s, then off)
  if (vf3_car_lock == HIGH) {
    // Lock signal detected, start/reset timer
    window_close_timer = millis();
    pcf8575.digitalWrite(VF3_WINDOW_LEFT, WRITE_OFF);
    pcf8575.digitalWrite(VF3_WINDOW_RIGHT, WRITE_OFF);
  } else if (window_close_timer != 0 && millis() - window_close_timer < WINDOW_CLOSE_DURATION) {
    // Keep windows closing for 30 seconds
    pcf8575.digitalWrite(VF3_WINDOW_LEFT, WRITE_OFF);
    pcf8575.digitalWrite(VF3_WINDOW_RIGHT, WRITE_OFF);
  } else {
    // Timer expired or lock not active
    pcf8575.digitalWrite(VF3_WINDOW_LEFT, WRITE_ON);
    pcf8575.digitalWrite(VF3_WINDOW_RIGHT, WRITE_ON);
    window_close_timer = 0;
  }
}
