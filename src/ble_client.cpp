#include "ble_client.h"
#include "status.h"
#include <BLEDevice.h>
#include <BLEClient.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <Arduino.h>

// ── UUIDs (must match Android VF3GattServer) ─────────────────────────────────
static const char* SERVICE_UUID         = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890";
static const char* CAR_STATUS_CHAR_UUID = "A1B2C3D4-E5F6-7890-ABCD-EF1234567895";

// ── State machine ─────────────────────────────────────────────────────────────
static volatile bool doConnect       = false;
static volatile bool connected       = false;
static BLEAdvertisedDevice* targetDevice = nullptr;

static BLEClient*            bleClient    = nullptr;
static BLERemoteCharacteristic* carStatusChar = nullptr;

static unsigned long lastWriteMs    = 0;
static unsigned long disconnectedAt = 0;

static const unsigned long WRITE_INTERVAL_MS   = 1000UL;
static const unsigned long RECONNECT_DELAY_MS  = 10000UL;

// ── Scan callback ─────────────────────────────────────────────────────────────
class ScanCallback : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) override {
        if (advertisedDevice.haveServiceUUID() &&
            advertisedDevice.isAdvertisingService(BLEUUID(SERVICE_UUID))) {
            Serial.print("[BLE] Found VF3 GATT server: ");
            Serial.println(advertisedDevice.getAddress().toString().c_str());
            BLEDevice::getScan()->stop();
            if (targetDevice) delete targetDevice;
            targetDevice = new BLEAdvertisedDevice(advertisedDevice);
            doConnect = true;
        }
    }
};

static ScanCallback scanCallback;

// ── Client callbacks ──────────────────────────────────────────────────────────
class ClientCallback : public BLEClientCallbacks {
    void onConnect(BLEClient* pClient) override {
        connected = true;
        Serial.println("[BLE] Connected to VF3 GATT server");
    }
    void onDisconnect(BLEClient* pClient) override {
        connected    = false;
        carStatusChar = nullptr;
        disconnectedAt = millis();
        Serial.println("[BLE] Disconnected from VF3 GATT server");
    }
};

static ClientCallback clientCallback;

// ── Connect & discover characteristics ───────────────────────────────────────
static bool connectToServer() {
    if (targetDevice == nullptr) return false;

    Serial.print("[BLE] Connecting to ");
    Serial.println(targetDevice->getAddress().toString().c_str());

    if (bleClient == nullptr) {
        bleClient = BLEDevice::createClient();
        bleClient->setClientCallbacks(&clientCallback);
    }

    if (!bleClient->connect(targetDevice)) {
        Serial.println("[BLE] Connection failed");
        return false;
    }

    // Request large MTU for longer payloads
    bleClient->setMTU(512);

    BLERemoteService* remoteService = bleClient->getService(BLEUUID(SERVICE_UUID));
    if (remoteService == nullptr) {
        Serial.println("[BLE] Service not found");
        bleClient->disconnect();
        return false;
    }

    carStatusChar = remoteService->getCharacteristic(BLEUUID(CAR_STATUS_CHAR_UUID));
    if (carStatusChar == nullptr) {
        Serial.println("[BLE] CarStatus characteristic not found");
        bleClient->disconnect();
        return false;
    }

    Serial.println("[BLE] CarStatus characteristic found — ready to write");
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
    Serial.println("[BLE] Scanning for VF3 GATT server...");
    scan->start(0, true);  // Non-blocking, continuous
}

void handleBleClient() {
    // ── Attempt connection when target found ─────────────────────────────────
    if (doConnect) {
        doConnect = false;
        if (connectToServer()) {
            Serial.println("[BLE] Connection established");
        } else {
            Serial.println("[BLE] Connection attempt failed — will retry");
            disconnectedAt = millis();
        }
    }

    // ── Write car status every second when connected ─────────────────────────
    if (connected && carStatusChar != nullptr) {
        unsigned long now = millis();
        if (now - lastWriteMs >= WRITE_INTERVAL_MS) {
            lastWriteMs = now;
            String payload = getCarStatusCompact();
            carStatusChar->writeValue(
                reinterpret_cast<const uint8_t*>(payload.c_str()),
                payload.length(),
                false   // writeWithoutResponse
            );
        }
        return;
    }

    // ── Re-scan after disconnection delay ────────────────────────────────────
    if (!connected && !doConnect) {
        unsigned long now = millis();
        // disconnectedAt == 0 means we haven't been connected yet — handled by initBleClient
        if (disconnectedAt != 0 && (now - disconnectedAt >= RECONNECT_DELAY_MS)) {
            disconnectedAt = 0;
            Serial.println("[BLE] Re-scanning for VF3 GATT server...");
            BLEScan* scan = BLEDevice::getScan();
            scan->start(0, true);
        }
    }
}
