#include "ble_server.h"
#include "pins.h"
#include "config.h"
#include "time_sync.h"
#include "controls/car_state.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Arduino.h>

// ── UUIDs (must match Android client) ────────────────────────────────────────
static const char* SERVICE_UUID         = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890";
static const char* CAR_STATUS_CHAR_UUID = "A1B2C3D4-E5F6-7890-ABCD-EF1234567895";

// ── Timing constants ──────────────────────────────────────────────────────────
static const unsigned long FULL_INTERVAL_MS = 60000UL; // heartbeat full send every 60 s

// ── BLE state ─────────────────────────────────────────────────────────────────
static BLEServer*         bleServer     = nullptr;
static BLECharacteristic* carStatusChar = nullptr;

static volatile bool     connected     = false;
static volatile uint16_t connId        = 0;
static volatile uint16_t currentMtu    = 23;   // default ATT MTU
static bool              snapshotValid = false;

static unsigned long lastFullSendMs = 0;

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

// ── Send a single notify with the given bytes ─────────────────────────────────
static void notifyOnce(const char* data, size_t len) {
    carStatusChar->setValue((uint8_t*)data, len);
    carStatusChar->notify();
}

// ── Send a payload, chunking by group if MTU is too small ────────────────────
// Max ATT payload = MTU - 3. Default MTU 23 → 20-byte payload, which is too
// small for the full snapshot, so we fall back to one notify per group.
static void sendPayload(const String& payload) {
    if (!connected || !carStatusChar || payload.isEmpty()) return;

    const uint16_t mtu     = currentMtu > 0 ? currentMtu : 23;
    const size_t   maxAttr = (mtu > 3) ? (size_t)(mtu - 3) : 20;

    if (payload.length() <= maxAttr) {
        notifyOnce(payload.c_str(), payload.length());
        return;
    }

    // Too big — split on '|' and send each group as its own "U|<group>" notify.
    // The first '|' is the boundary between the F/U prefix and the first group.
    int sep = payload.indexOf('|');
    if (sep < 0) {
        // Malformed (no separator) — best effort, send a truncated chunk.
        notifyOnce(payload.c_str(), maxAttr);
        return;
    }

    int start = sep + 1;
    while (start < (int)payload.length()) {
        int next = payload.indexOf('|', start);
        int end  = (next < 0) ? (int)payload.length() : next;

        String chunk = "U|";
        chunk += payload.substring(start, end);

        if (chunk.length() <= maxAttr) {
            notifyOnce(chunk.c_str(), chunk.length());
        } else {
            // Single group exceeds MTU — extremely unlikely (groups are <= ~40 bytes,
            // requires MTU < ~43). Send what fits as a last resort.
            notifyOnce(chunk.c_str(), maxAttr);
        }

        // Brief pacing so the BLE stack can flush each notify to the peer.
        delay(10);
        start = end + 1;
    }
}

// ── Server callbacks ──────────────────────────────────────────────────────────
class ServerCallback : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer, esp_ble_gatts_cb_param_t* param) override {
        connId        = param->connect.conn_id;
        currentMtu    = 23;     // resets on every new connection
        connected     = true;
        snapshotValid = false;
        Serial.printf("[BLE] Phone connected (conn_id=%u)\n", (unsigned)connId);
        // Stay non-advertising while connected; resume on disconnect.
    }
    void onDisconnect(BLEServer* pServer) override {
        connected     = false;
        snapshotValid = false;
        currentMtu    = 23;
        Serial.println("[BLE] Phone disconnected — resuming advertising");
        BLEDevice::startAdvertising();
    }
    void onMtuChanged(BLEServer* pServer, esp_ble_gatts_cb_param_t* param) override {
        currentMtu = param->mtu.mtu;
        Serial.printf("[BLE] MTU negotiated = %u\n", (unsigned)currentMtu);
    }
};
static ServerCallback serverCallback;

// ── Public API ────────────────────────────────────────────────────────────────
void initBleServer() {
    BLEDevice::init("VF3-MCU");

    // Allow the phone to negotiate up to a 247-byte MTU (~244-byte ATT payload).
    BLEDevice::setMTU(247);

    bleServer = BLEDevice::createServer();
    bleServer->setCallbacks(&serverCallback);

    BLEService* svc = bleServer->createService(SERVICE_UUID);
    carStatusChar = svc->createCharacteristic(
        CAR_STATUS_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    // CCCD — required so the phone can subscribe to notifications.
    carStatusChar->addDescriptor(new BLE2902());
    svc->start();

    BLEAdvertising* adv = BLEDevice::getAdvertising();
    adv->addServiceUUID(SERVICE_UUID);
    adv->setScanResponse(true);
    adv->setMinPreferred(0x06);
    adv->setMinPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println("[BLE] Advertising as VF3-MCU — waiting for phone to connect");
}

void handleBleServer() {
    if (!connected || !carStatusChar) return;

    unsigned long now = millis();

    // First send after connect (or after a forced resync) → full snapshot.
    if (!snapshotValid) {
        sendPayload(buildFull());
        takeSnapshot();
        lastFullSendMs = now;
        return;
    }

    if (now - lastFullSendMs >= FULL_INTERVAL_MS) {
        // Periodic full resync
        sendPayload(buildFull());
        takeSnapshot();
        lastFullSendMs = now;
    } else {
        // Delta: only send if something changed
        String delta = buildDelta();
        if (!delta.isEmpty()) {
            sendPayload(delta);
        }
    }
}
