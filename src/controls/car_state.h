#ifndef CAR_STATE_H
#define CAR_STATE_H

#include "../config.h"
#include "../pins.h"

// Car lock state
enum CarLockState {
  CAR_UNLOCKED = 0,
  CAR_LOCKED = 1
};

extern CarLockState car_lock_state;

void handleCarState();

#endif // CAR_STATE_H
