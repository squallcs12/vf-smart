#ifndef WINDOW_CONTROL_H
#define WINDOW_CONTROL_H

#include "../config.h"
#include "../pins.h"

// Failsafe cap on how long a single window relay may stay energized from a
// press-and-hold command. The app sends "on" on press and "off" on release
// (like a physical window switch); if that release is ever lost — WiFi drop,
// app killed, phone asleep mid-hold — the relay would otherwise stay powered
// and stall the motor at the end of travel. Full window travel is ~5 s, so 12 s
// is a comfortable margin that still protects the motor. Mirrors the Android
// HoldControlButton's own release safety net.
#define WINDOW_MAX_HOLD_MS 12000

void handleWindowControl();

// Drive one window relay for a hold command: moving=true energizes it (the
// window rolls), moving=false stops it. Tracks the activation time so
// handleWindowControl() can auto-stop a relay whose "off" release never arrived.
// Use this from the window endpoints instead of writing the relay directly.
void moveWindow(uint8_t pin, bool moving);

#endif // WINDOW_CONTROL_H
