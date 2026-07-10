#ifndef STATUS_H
#define STATUS_H

#include <Arduino.h>
#include "config.h"
#include "pins.h"
#include "time_sync.h"
#include "controls/car_state.h"
#include "tpms.h"
#include <ArduinoJson.h>

String getCarStatusJSON();

#endif // STATUS_H
