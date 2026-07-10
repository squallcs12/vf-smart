#include "window_control.h"

// Per-relay hold tracking. moving_since = millis() when the relay was last
// energized by a hold command, 0 when idle/stopped. Lets handleWindowControl()
// auto-stop any relay whose "off" release was lost, so a held window can never
// stay powered indefinitely.
struct WindowRelay { uint8_t pin; unsigned long moving_since; };
static WindowRelay window_relays[] = {
  { VF3_WINDOW_LEFT_UP,    0 },
  { VF3_WINDOW_RIGHT_UP,   0 },
  { VF3_WINDOW_LEFT_DOWN,  0 },
  { VF3_WINDOW_RIGHT_DOWN, 0 },
};
static const size_t WINDOW_RELAY_COUNT = sizeof(window_relays) / sizeof(window_relays[0]);

void moveWindow(uint8_t pin, bool moving) {
  safeDigitalWrite(pin, moving ? WRITE_OFF : WRITE_ON);
  for (size_t i = 0; i < WINDOW_RELAY_COUNT; i++) {
    if (window_relays[i].pin == pin) {
      window_relays[i].moving_since = moving ? millis() : 0;
      break;
    }
  }
}

// Windows are press-and-hold only (like the physical switches): each side rolls
// while its button is held and stops on release. The only job here is the
// firmware-side backstop for a lost "off" release — auto-stop any relay held
// past WINDOW_MAX_HOLD_MS (see the header).
void handleWindowControl() {
  for (size_t i = 0; i < WINDOW_RELAY_COUNT; i++) {
    if (window_relays[i].moving_since != 0 &&
        millis() - window_relays[i].moving_since >= WINDOW_MAX_HOLD_MS) {
      safeDigitalWrite(window_relays[i].pin, WRITE_ON);
      window_relays[i].moving_since = 0;
      Serial.print("Window control: hold failsafe stopped relay ");
      Serial.println(window_relays[i].pin);
    }
  }
}
