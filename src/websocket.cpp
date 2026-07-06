#include "websocket.h"
#include "pins.h"
#include "config.h"
#include "time_sync.h"
#include "controls/car_state.h"
#include <Arduino.h>
#include <ArduinoJson.h>
#include <set>
#include <map>

// ── WebSocket endpoint ────────────────────────────────────────────────────────
static AsyncWebSocket ws("/ws");

// ── Authentication ────────────────────────────────────────────────────────────
// The socket is open to anyone, but the server streams NOTHING until a client
// proves it knows the configured API key. Right after connecting, a client must
// send an auth frame as its first message:
//     {"auth":"<api_key>"}
// On success it receives {"auth":"ok"} then the normal status stream; on failure
// it gets {"auth":"failed"} and is disconnected. Clients that don't authenticate
// within WS_AUTH_TIMEOUT_MS are dropped so unauthenticated sockets can't linger.
#define WS_AUTH_TIMEOUT_MS 5000UL

// IDs of clients that have authenticated (only these receive status frames).
static std::set<uint32_t> authedClients;
// Connected-but-not-yet-authenticated clients → their connect time (for timeout).
static std::map<uint32_t, unsigned long> pendingClients;

// Send a text frame to every authenticated client (replaces ws.textAll, which
// would leak status to unauthenticated sockets).
//
// Skip any client whose send queue is already full (or that isn't connected):
// the underlying library would otherwise log "ERROR: Too many messages queued"
// and drop the frame. A backed-up client (slow WiFi, backgrounded app, stale
// socket) just misses this delta and resyncs on the next delta it can accept or
// the 60 s full-frame heartbeat, instead of thrashing the queue at 20 Hz.
static void sendToAuthed(const String& msg) {
    for (uint32_t id : authedClients) {
        AsyncWebSocketClient* c = ws.client(id);
        if (c && !c->queueIsFull()) c->text(msg);
    }
}

// ── Timing constants ──────────────────────────────────────────────────────────
static const unsigned long FULL_INTERVAL_MS = 60000UL; // heartbeat full send every 60 s
// Minimum spacing between status pushes. The control loop ticks at 20 Hz, but
// grpS carries jittery analog values (brake/steering) that would otherwise emit
// a delta almost every tick. Streaming at 20 Hz keeps a slow client's send queue
// and TCP window saturated, which starves the WebSocket PONG reply and trips the
// phone's 5 s ping timeout ("sent ping but didn't receive pong"). Coalescing to
// ~10 Hz keeps the UI responsive while leaving room for control frames.
static const unsigned long SEND_MIN_INTERVAL_MS = 100UL;
// Deadband for the raw 12-bit ADC brake/steering values. analogRead() jitters by
// dozens of counts on the ESP32, which would otherwise fire an S delta almost every
// tick (churning the phone's UI recomposition). Only treat a change this large as
// real. ~40 counts ≈ 1% of the 0-4095 range — well below any actual pedal/wheel move.
static const int SENSOR_ADC_DEADBAND = 40;

static bool          snapshotValid  = false;
static unsigned long lastFullSendMs = 0;
static unsigned long lastSendMs     = 0; // last status frame (full or delta) push

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

// ── Snapshot helper ───────────────────────────────────────────────────────────
// Records the exact current readings as the baseline that buildDelta() compares
// against. Intentionally does NOT apply the brake/steering deadband (or the voltage
// threshold): those live only in the emit decision in buildDelta(). The baseline is
// exact so a real move is measured from where the last frame actually reported.
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

    // S — sensors (brake/steering deadbanded, voltage threshold 0.05 V, to filter ADC noise)
    if (abs(vf3_brake - pBrake) > SENSOR_ADC_DEADBAND ||
        abs(vf3_steering_angle - pSteering) > SENSOR_ADC_DEADBAND ||
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

// Handle the first message from a not-yet-authenticated client: it must be an
// auth frame carrying the configured API key.
static void handleAuthFrame(AsyncWebSocketClient* client, uint8_t* data, size_t len) {
    JsonDocument doc;
    const char* key = "";
    if (!deserializeJson(doc, data, len)) {
        key = doc["auth"] | "";
    }

    if (configured_api_key.length() > 0 && configured_api_key == key) {
        authedClients.insert(client->id());
        pendingClients.erase(client->id());
        Serial.printf("[WS] Client #%u authenticated\n", client->id());
        client->text("{\"auth\":\"ok\"}");
        // Late joiner: hand it an immediate full baseline if the stream is already
        // running. When it isn't (first client), handleWebSocket() sends the first
        // full frame to all authed clients on its next tick.
        if (snapshotValid && !client->queueIsFull()) client->text(buildFull());
    } else {
        Serial.printf("[WS] Client #%u FAILED auth — disconnecting\n", client->id());
        client->text("{\"auth\":\"failed\"}");
        client->close();
        pendingClients.erase(client->id());
    }
}

// ── WebSocket events ──────────────────────────────────────────────────────────
static void onWsEvent(AsyncWebSocket* server, AsyncWebSocketClient* client,
                      AwsEventType type, void* arg, uint8_t* data, size_t len) {
    if (type == WS_EVT_CONNECT) {
        // Connected but NOT trusted yet — stream nothing until it authenticates.
        Serial.printf("[WS] Client #%u connected from %s (awaiting auth)\n",
                      client->id(), client->remoteIP().toString().c_str());
        pendingClients[client->id()] = millis();
    } else if (type == WS_EVT_DISCONNECT) {
        Serial.printf("[WS] Client #%u disconnected\n", client->id());
        authedClients.erase(client->id());
        pendingClients.erase(client->id());
    } else if (type == WS_EVT_DATA) {
        // Only the auth handshake is expected inbound (status is one-way). Handle
        // complete single-frame text messages from unauthenticated clients.
        AwsFrameInfo* info = (AwsFrameInfo*)arg;
        if (info->final && info->index == 0 && info->len == len && info->opcode == WS_TEXT &&
            authedClients.find(client->id()) == authedClients.end()) {
            handleAuthFrame(client, data, len);
        }
    }
}

// ── Public API ────────────────────────────────────────────────────────────────
void setupWebSocket(AsyncWebServer& server) {
    ws.onEvent(onWsEvent);
    server.addHandler(&ws);
    Serial.println("[WS] WebSocket handler registered at /ws (auth required after connect)");
}

bool hasWebSocketClient() {
    // Only authenticated clients count as "a phone is connected".
    return !authedClients.empty();
}

void handleWebSocket() {
    ws.cleanupClients();

    // Drop clients that connected but never authenticated in time.
    unsigned long now = millis();
    for (auto it = pendingClients.begin(); it != pendingClients.end(); ) {
        if (now - it->second > WS_AUTH_TIMEOUT_MS) {
            Serial.printf("[WS] Client #%u auth timeout — disconnecting\n", it->first);
            ws.close(it->first);
            it = pendingClients.erase(it);
        } else {
            ++it;
        }
    }

    if (authedClients.empty()) {
        // No authenticated subscribers — drop the snapshot so the next one that
        // authenticates gets a full frame.
        snapshotValid = false;
        return;
    }

    // First send after the stream (re)starts → full snapshot to all authed clients.
    if (!snapshotValid) {
        sendToAuthed(buildFull());
        takeSnapshot();
        lastFullSendMs = now;
        lastSendMs = now;
        return;
    }

    if (now - lastFullSendMs >= FULL_INTERVAL_MS) {
        // Periodic full resync (heartbeat)
        sendToAuthed(buildFull());
        takeSnapshot();
        lastFullSendMs = now;
        lastSendMs = now;
    } else if (now - lastSendMs >= SEND_MIN_INTERVAL_MS) {
        // Delta: only send if something changed. Throttled to SEND_MIN_INTERVAL_MS
        // so bursty analog changes coalesce instead of flooding the socket. Gating
        // the buildDelta() call (not just the send) lets intervening changes
        // accumulate into the next frame. buildDelta() returns "" if nothing
        // changed, in which case we leave lastSendMs alone so the next real change
        // goes out promptly.
        String delta = buildDelta();
        if (delta.length() > 0) {
            sendToAuthed(delta);
            lastSendMs = now;
        }
    }
}

void broadcastStatus() {
    // Immediate delta push after a command-driven state change. The periodic
    // handleWebSocket() tick would catch it too, but this makes UI feedback snappy.
    if (authedClients.empty() || !snapshotValid) return;
    String delta = buildDelta();
    if (delta.length() > 0) {
        sendToAuthed(delta);
        lastSendMs = millis();
    }
}
