#include "ble_client.h"
#include "pins.h"
#include "config.h"
#include "time_sync.h"
#include "controls/car_state.h"
#include <BLEDevice.h>
#include <BLEClient.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <Arduino.h>

// ── UUIDs (must match Android VF3GattServer) ─────────────────────────────────
static const char* SERVICE_UUID         = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890";
static const char* CAR_STATUS_CHAR_UUID = "A1B2C3D4-E5F6-7890-ABCD-EF1234567895";

// ── Timing constants ──────────────────────────────────────────────────────────
static const unsigned long FULL_INTERVAL_MS    = 60000UL; // heartbeat full send every 60 s
static const unsigned long RECONNECT_DELAY_MS  = 10000UL; // wait 10 s before re-scan

// ── BLE state ─────────────────────────────────────────────────────────────────
static volatile bool doConnect       = false;
static volatile bool connected       = false;
static BLEAdvertisedDevice* targetDevice = nullptr;
static BLEClient*            bleClient    = nullptr;
static BLERemoteCharacteristic* carStatusChar = nullptr;

static unsigned long lastFullSendMs  = 0;
static unsigned long disconnectedAt  = 0;
static bool snapshotValid            = false;

// ── Previous-value snapshot (per group) ──────────────────────────────────────
// Sensors
static int   pBrake = -1, pSteering = -1, pGear = -1;
static float pVoltage = -1.0f;
// Doors
static int   pDoorFl = -1, pDoorFr = -1, pDoorTrunk = -1, pDoorLocked = -1;
// Windows
static int   pWinL = -1, pWinR = -1;
// Seats
static int   pSeatFlo = -1, pSeatFro = -1, pSeatFlb = -1, pSeatFrb = -1;
// Lights
static int   pDemi = -1, pNormal = -1;
// Proximity
static int   pProxL = -1, pProxR = -1;
// Controls
static int   pBp = -1, pAp = -1, pCam = -1, pCl = -1, pCu = -1;
// Misc
static int   pCharging = -1, pLockState = -1;
static bool  pWca = false;
static long  pWcrSecs = -1;
static bool  pLr = false, pNight = false;

// ── Group builders ────────────────────────────────────────────────────────────
static String grpS() {
    char buf[40];
    snprintf(buf, sizeof(buf), "S:%d,%d,%.2f,%d",
             vf3_brake, vf3_steering_angle, vf3_battery_voltage, vf3_gear_drive);
    return String(buf);
}
static String grpD() {
    char buf[24];
    snprintf(buf, sizeof(buf), "D:%d,%d,%d,%d",
             vf3_door_fl, vf3_door_fr, vf3_door_trunk, vf3_door_locked);
    return String(buf);
}
static String grpW() {
    char buf[16];
    snprintf(buf, sizeof(buf), "W:%d,%d", vf3_window_left_state, vf3_window_right_state);
    return String(buf);
}
static String grpE() {
    char buf[24];
    snprintf(buf, sizeof(buf), "E:%d,%d,%d,%d",
             vf3_seat_fl, vf3_seat_fr, vf3_seatbelt_fl, vf3_seatbelt_fr);
    return String(buf);
}
static String grpL() {
    char buf[12];
    snprintf(buf, sizeof(buf), "L:%d,%d", vf3_demi_light, vf3_normal_light);
    return String(buf);
}
static String grpP() {
    char buf[12];
    snprintf(buf, sizeof(buf), "P:%d,%d", vf3_proximity_rear_l, vf3_proximity_rear_r);
    return String(buf);
}
static String grpC() {
    char buf[24];
    snprintf(buf, sizeof(buf), "C:%d,%d,%d,%d,%d",
             vf3_brake_pressed, self_accessory_power, self_inside_cameras,
             vf3_car_lock, vf3_car_unlock);
    return String(buf);
}
static String grpX() {
    bool wca = (window_close_timer != 0);
    long wcrSecs = wca
        ? (long)((WINDOW_CLOSE_DURATION - (millis() - window_close_timer)) / 1000UL)
        : 0L;
    int ls = (car_lock_state == CAR_LOCKED) ? 1 : 0;
    char buf[32];
    snprintf(buf, sizeof(buf), "X:%d,%d,%d,%ld,%d,%d",
             vf3_charging_status, ls, wca ? 1 : 0, wcrSecs,
             light_reminder_enabled ? 1 : 0, isNightTime() ? 1 : 0);
    return String(buf);
}

// ── Build full payload ────────────────────────────────────────────────────────
static String buildFull() {
    return String("F|") + grpS() + "|" + grpD() + "|" + grpW() + "|" +
           grpE() + "|" + grpL() + "|" + grpP() + "|" + grpC() + "|" + grpX();
}

// ── Snapshot helpers ──────────────────────────────────────────────────────────
static void takeSnapshot() {
    pBrake    = vf3_brake;
    pSteering = vf3_steering_angle;
    pVoltage  = vf3_battery_voltage;
    pGear     = vf3_gear_drive;

    pDoorFl     = vf3_door_fl;
    pDoorFr     = vf3_door_fr;
    pDoorTrunk  = vf3_door_trunk;
    pDoorLocked = vf3_door_locked;

    pWinL = vf3_window_left_state;
    pWinR = vf3_window_right_state;

    pSeatFlo = vf3_seat_fl;
    pSeatFro = vf3_seat_fr;
    pSeatFlb = vf3_seatbelt_fl;
    pSeatFrb = vf3_seatbelt_fr;

    pDemi   = vf3_demi_light;
    pNormal = vf3_normal_light;

    pProxL = vf3_proximity_rear_l;
    pProxR = vf3_proximity_rear_r;

    pBp  = vf3_brake_pressed;
    pAp  = self_accessory_power;
    pCam = self_inside_cameras;
    pCl  = vf3_car_lock;
    pCu  = vf3_car_unlock;

    bool wca = (window_close_timer != 0);
    pCharging  = vf3_charging_status;
    pLockState = (car_lock_state == CAR_LOCKED) ? 1 : 0;
    pWca       = wca;
    pWcrSecs   = wca ? (long)((WINDOW_CLOSE_DURATION - (millis() - window_close_timer)) / 1000UL) : 0L;
    pLr        = light_reminder_enabled;
    pNight     = isNightTime();

    snapshotValid = true;
}

// ── Build delta payload (only changed groups) ─────────────────────────────────
// Returns empty String if nothing changed.
static String buildDelta() {
    if (!snapshotValid) return String();

    bool wca = (window_close_timer != 0);
    long wcrSecs = wca
        ? (long)((WINDOW_CLOSE_DURATION - (millis() - window_close_timer)) / 1000UL)
        : 0L;
    int ls    = (car_lock_state == CAR_LOCKED) ? 1 : 0;
    bool night = isNightTime();

    String out = "U";
    bool any   = false;

    // S — sensors (voltage threshold 0.05 V to filter ADC noise)
    if (vf3_brake != pBrake || vf3_steering_angle != pSteering ||
        vf3_gear_drive != pGear || fabsf(vf3_battery_voltage - pVoltage) > 0.05f) {
        out += "|"; out += grpS();
        pBrake = vf3_brake; pSteering = vf3_steering_angle;
        pVoltage = vf3_battery_voltage; pGear = vf3_gear_drive;
        any = true;
    }
    // D — doors
    if (vf3_door_fl != pDoorFl || vf3_door_fr != pDoorFr ||
        vf3_door_trunk != pDoorTrunk || vf3_door_locked != pDoorLocked) {
        out += "|"; out += grpD();
        pDoorFl = vf3_door_fl; pDoorFr = vf3_door_fr;
        pDoorTrunk = vf3_door_trunk; pDoorLocked = vf3_door_locked;
        any = true;
    }
    // W — windows
    if (vf3_window_left_state != pWinL || vf3_window_right_state != pWinR) {
        out += "|"; out += grpW();
        pWinL = vf3_window_left_state; pWinR = vf3_window_right_state;
        any = true;
    }
    // E — seats
    if (vf3_seat_fl != pSeatFlo || vf3_seat_fr != pSeatFro ||
        vf3_seatbelt_fl != pSeatFlb || vf3_seatbelt_fr != pSeatFrb) {
        out += "|"; out += grpE();
        pSeatFlo = vf3_seat_fl; pSeatFro = vf3_seat_fr;
        pSeatFlb = vf3_seatbelt_fl; pSeatFrb = vf3_seatbelt_fr;
        any = true;
    }
    // L — lights
    if (vf3_demi_light != pDemi || vf3_normal_light != pNormal) {
        out += "|"; out += grpL();
        pDemi = vf3_demi_light; pNormal = vf3_normal_light;
        any = true;
    }
    // P — proximity
    if (vf3_proximity_rear_l != pProxL || vf3_proximity_rear_r != pProxR) {
        out += "|"; out += grpP();
        pProxL = vf3_proximity_rear_l; pProxR = vf3_proximity_rear_r;
        any = true;
    }
    // C — controls
    if (vf3_brake_pressed != pBp || self_accessory_power != pAp ||
        self_inside_cameras != pCam || vf3_car_lock != pCl || vf3_car_unlock != pCu) {
        out += "|"; out += grpC();
        pBp = vf3_brake_pressed; pAp = self_accessory_power;
        pCam = self_inside_cameras; pCl = vf3_car_lock; pCu = vf3_car_unlock;
        any = true;
    }
    // X — misc (wcr changes every second while window is closing, so compare by second)
    if (vf3_charging_status != pCharging || ls != pLockState ||
        wca != pWca || wcrSecs != pWcrSecs ||
        light_reminder_enabled != pLr || night != pNight) {
        out += "|"; out += grpX();
        pCharging = vf3_charging_status; pLockState = ls;
        pWca = wca; pWcrSecs = wcrSecs;
        pLr = light_reminder_enabled; pNight = night;
        any = true;
    }

    return any ? out : String();
}

// ── Write payload over BLE ────────────────────────────────────────────────────
static void writePayload(const String& payload) {
    if (!carStatusChar || payload.isEmpty()) return;
    if (bleClient && !bleClient->isConnected()) {
        connected     = false;
        carStatusChar = nullptr;
        snapshotValid = false;
        return;
    }
    carStatusChar->writeValue(
        reinterpret_cast<const uint8_t*>(payload.c_str()),
        payload.length(),
        false // WRITE_NO_RESPONSE
    );
}

// ── BLE scan callback ─────────────────────────────────────────────────────────
class ScanCallback : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice device) override {
        if (device.haveServiceUUID() &&
            device.isAdvertisingService(BLEUUID(SERVICE_UUID))) {
            Serial.print("[BLE] Found VF3 GATT server: ");
            Serial.println(device.getAddress().toString().c_str());
            BLEDevice::getScan()->stop();
            if (targetDevice) delete targetDevice;
            targetDevice = new BLEAdvertisedDevice(device);
            doConnect = true;
        }
    }
};
static ScanCallback scanCallback;

// ── BLE client callbacks ──────────────────────────────────────────────────────
class ClientCallback : public BLEClientCallbacks {
    void onConnect(BLEClient*) override {
        Serial.println("[BLE] Connected");
    }
    void onDisconnect(BLEClient*) override {
        connected     = false;
        carStatusChar = nullptr;
        snapshotValid = false;
        disconnectedAt = millis();
        Serial.println("[BLE] Disconnected — will re-scan in 10 s");
    }
};
static ClientCallback clientCallback;

// ── Connect to server ─────────────────────────────────────────────────────────
static bool connectToServer() {
    if (!targetDevice) return false;

    if (!bleClient) {
        bleClient = BLEDevice::createClient();
        bleClient->setClientCallbacks(&clientCallback);
    }

    if (!bleClient->connect(targetDevice)) {
        Serial.println("[BLE] Connection failed");
        return false;
    }
    bleClient->setMTU(512);

    BLERemoteService* svc = bleClient->getService(BLEUUID(SERVICE_UUID));
    if (!svc) {
        Serial.println("[BLE] Service not found");
        bleClient->disconnect();
        return false;
    }

    carStatusChar = svc->getCharacteristic(BLEUUID(CAR_STATUS_CHAR_UUID));
    if (!carStatusChar) {
        Serial.println("[BLE] CAR_STATUS characteristic not found");
        bleClient->disconnect();
        return false;
    }

    connected = true;
    Serial.println("[BLE] CAR_STATUS characteristic ready");
    return true;
}

// ── Public API ────────────────────────────────────────────────────────────────
void initBleClient() {
    BLEDevice::init("VF3-MCU");
    BLEScan* scan = BLEDevice::getScan();
    scan->setAdvertisedDeviceCallbacks(&scanCallback);
    scan->setActiveScan(true);
    scan->setInterval(100);
    scan->setWindow(99);
    scan->start(0, true); // non-blocking, scan until found
    Serial.println("[BLE] Scanning for VF3 phone GATT server...");
}

void handleBleClient() {
    unsigned long now = millis();

    // ── Connect when scan finds the phone ────────────────────────────────────
    if (doConnect) {
        doConnect = false;
        if (connectToServer()) {
            // Full status on fresh connect, then snapshot so delta tracks changes
            writePayload(buildFull());
            takeSnapshot();
            lastFullSendMs = now;
        } else {
            disconnectedAt = now;
        }
        return;
    }

    // ── Re-scan after disconnect ──────────────────────────────────────────────
    if (!connected && !doConnect && disconnectedAt != 0) {
        if (now - disconnectedAt >= RECONNECT_DELAY_MS) {
            disconnectedAt = 0;
            Serial.println("[BLE] Re-scanning for VF3 phone...");
            BLEDevice::getScan()->clearResults();
            BLEDevice::getScan()->start(0, true);
        }
        return;
    }

    // ── Connected: send full heartbeat or delta ───────────────────────────────
    if (!connected || !carStatusChar) return;

    if (now - lastFullSendMs >= FULL_INTERVAL_MS) {
        // Periodic full resync
        writePayload(buildFull());
        takeSnapshot();
        lastFullSendMs = now;
    } else {
        // Delta: only send if something changed
        String delta = buildDelta();
        if (!delta.isEmpty()) {
            writePayload(delta);
        }
    }
}
