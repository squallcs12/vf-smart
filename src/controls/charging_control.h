#ifndef CHARGING_CONTROL_H
#define CHARGING_CONTROL_H

#include "../config.h"
#include "../pins.h"

void handleChargingControl();

// Trigger the two-phase charger-unlock pulse sequence (pull then push).
// Non-blocking; the sequence is advanced by handleChargingControl().
void startChargerUnlock();

#endif // CHARGING_CONTROL_H
