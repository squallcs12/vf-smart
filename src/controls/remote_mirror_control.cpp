#include "remote_mirror_control.h"
#include "../pins.h"
#include "../pcf8575_io.h"

// Double-press detection parameters
#define DOUBLE_PRESS_WINDOW 500  // 500ms window to detect second press
#define MIRROR_PULSE_DURATION 1000  // 1 second pulse for mirror control
#define MIRROR_COOLDOWN 2000  // 2 seconds between operations

// Lock button (close mirrors) state tracking
static int prev_remote_lock_press = LOW;
static int lock_press_count = 0;
static unsigned long last_lock_press_time = 0;
static unsigned long last_mirror_close = 0;

// Unlock button (open mirrors) state tracking
static int prev_remote_unlock_press = LOW;
static int unlock_press_count = 0;
static unsigned long last_unlock_press_time = 0;
static unsigned long last_mirror_open = 0;

void handleRemoteMirrorControl() {
  unsigned long current_time = millis();

  // === LOCK BUTTON (CLOSE MIRRORS) ===
  // Detect rising edge (LOW to HIGH transition) for lock button
  if (vf3_remote_lock_press == HIGH && prev_remote_lock_press == LOW) {
    // Button just pressed
    lock_press_count++;
    last_lock_press_time = current_time;

    Serial.print("Remote mirror control: Lock button press ");
    Serial.print(lock_press_count);
    Serial.println(" detected");

    // Check for double press
    if (lock_press_count >= 2) {
      // Double press detected - close mirrors
      if (current_time - last_mirror_close >= MIRROR_COOLDOWN) {
        Serial.println("Remote mirror control: Double press detected, closing side mirrors");

        safeDigitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_ON);
        delay(MIRROR_PULSE_DURATION);
        safeDigitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_OFF);

        last_mirror_close = current_time;
      } else {
        Serial.println("Remote mirror control: Rate limit active for mirror close");
      }

      // Reset count after action
      lock_press_count = 0;
    }
  }

  // Reset lock press count after timeout
  if (lock_press_count > 0 && (current_time - last_lock_press_time > DOUBLE_PRESS_WINDOW)) {
    Serial.println("Remote mirror control: Lock double-press timeout, resetting count");
    lock_press_count = 0;
  }

  prev_remote_lock_press = vf3_remote_lock_press;

  // === UNLOCK BUTTON (OPEN MIRRORS) ===
  // Detect rising edge (LOW to HIGH transition) for unlock button
  if (vf3_remote_unlock_press == HIGH && prev_remote_unlock_press == LOW) {
    // Button just pressed
    unlock_press_count++;
    last_unlock_press_time = current_time;

    Serial.print("Remote mirror control: Unlock button press ");
    Serial.print(unlock_press_count);
    Serial.println(" detected");

    // Check for double press
    if (unlock_press_count >= 2) {
      // Double press detected - open mirrors
      if (current_time - last_mirror_open >= MIRROR_COOLDOWN) {
        Serial.println("Remote mirror control: Double press detected, opening side mirrors");

        safeDigitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_ON);
        delay(MIRROR_PULSE_DURATION);
        safeDigitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_OFF);

        last_mirror_open = current_time;
      } else {
        Serial.println("Remote mirror control: Rate limit active for mirror open");
      }

      // Reset count after action
      unlock_press_count = 0;
    }
  }

  // Reset unlock press count after timeout
  if (unlock_press_count > 0 && (current_time - last_unlock_press_time > DOUBLE_PRESS_WINDOW)) {
    Serial.println("Remote mirror control: Unlock double-press timeout, resetting count");
    unlock_press_count = 0;
  }

  prev_remote_unlock_press = vf3_remote_unlock_press;
}
