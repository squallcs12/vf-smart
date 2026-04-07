#ifndef TPMS_H
#define TPMS_H

#include <Arduino.h>
#include "pins.h"

#define TPMS_STALE_MS 300000UL  // Mark tire data stale after 5 minutes

struct TpmsTire {
    uint32_t      sensorId;
    float         pressureKpa;
    int8_t        tempC;
    bool          batteryOk;
    bool          alarm;
    unsigned long lastUpdateMs;
    bool          valid;        // Has received at least one reading

    bool isStale() const {
        return valid && (millis() - lastUpdateMs > TPMS_STALE_MS);
    }
};

// Global state — FL / FR / RL / RR
extern TpmsTire tpms_fl, tpms_fr, tpms_rl, tpms_rr;

void initTpms();
void handleTpms();

// Calibration API (0=FL, 1=FR, 2=RL, 3=RR)
void     tpmsResetAll();
void     tpmsSwap(int posA, int posB);
uint32_t tpmsGetLearnedId(int pos);
String   tpmsCalibrationJSON();

#endif // TPMS_H
