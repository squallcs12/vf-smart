#include "factory_reset.h"
#include "../pins.h"
#include "../storage.h"

// Factory reset button state
static unsigned long button_press_start = 0;
static bool button_was_pressed = false;
static bool reset_triggered = false;

#define FACTORY_RESET_HOLD_TIME 10000  // 10 seconds in milliseconds

void handleFactoryResetButton() {
  // Read factory reset button (GPIO 0 - BOOT button)
  // Active LOW: button pressed = LOW, button released = HIGH
  int button_state = digitalRead(VF3_FACTORY_RESET_BTN);

  if (button_state == LOW) {
    // Button is pressed
    if (!button_was_pressed) {
      // Button just pressed - start timer
      button_press_start = millis();
      button_was_pressed = true;
      Serial.println("Factory reset button pressed - hold for 10 seconds to reset");
    } else {
      // Button still pressed - check if held long enough
      unsigned long hold_duration = millis() - button_press_start;

      // Print countdown every second
      static unsigned long last_print = 0;
      if (millis() - last_print >= 1000) {
        int seconds_remaining = (FACTORY_RESET_HOLD_TIME - hold_duration) / 1000;
        if (seconds_remaining > 0) {
          Serial.print("Factory reset in ");
          Serial.print(seconds_remaining);
          Serial.println(" seconds... (release to cancel)");
        }
        last_print = millis();
      }

      if (hold_duration >= FACTORY_RESET_HOLD_TIME && !reset_triggered) {
        // Button held for 10 seconds - trigger factory reset
        reset_triggered = true;
        Serial.println("");
        Serial.println("===========================================");
        Serial.println("FACTORY RESET TRIGGERED VIA HARDWARE BUTTON");
        Serial.println("===========================================");

        // Perform factory reset (clears NVS and restarts)
        factoryReset();
      }
    }
  } else {
    // Button is released
    if (button_was_pressed && !reset_triggered) {
      // Button was released before 10 seconds
      unsigned long hold_duration = millis() - button_press_start;
      Serial.print("Factory reset cancelled (button released after ");
      Serial.print(hold_duration / 1000);
      Serial.println(" seconds)");
    }

    // Reset state
    button_was_pressed = false;
    button_press_start = 0;
  }
}
