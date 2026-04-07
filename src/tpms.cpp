#include "tpms.h"
#include <Preferences.h>

// ── Global tire state ─────────────────────────────────────────────────────────
TpmsTire tpms_fl = {0, 0, 0, true, false, 0, false};
TpmsTire tpms_fr = {0, 0, 0, true, false, 0, false};
TpmsTire tpms_rl = {0, 0, 0, true, false, 0, false};
TpmsTire tpms_rr = {0, 0, 0, true, false, 0, false};

// ── Sensor ID → tire position (FL=0 FR=1 RL=2 RR=3), persisted in NVS ────────
static uint32_t learned_ids[4] = {0, 0, 0, 0};
static Preferences prefs;

static void loadLearnedIds() {
    prefs.begin("tpms", true);
    for (int i = 0; i < 4; i++) {
        char key[8];
        snprintf(key, sizeof(key), "id%d", i);
        learned_ids[i] = prefs.getUInt(key, 0);
    }
    prefs.end();
}

static void saveLearnedIds() {
    prefs.begin("tpms", false);
    for (int i = 0; i < 4; i++) {
        char key[8];
        snprintf(key, sizeof(key), "id%d", i);
        prefs.putUInt(key, learned_ids[i]);
    }
    prefs.end();
}

static TpmsTire* tireForId(uint32_t id) {
    if (id == 0) return nullptr;
    for (int i = 0; i < 4; i++) {
        if (learned_ids[i] == id) {
            TpmsTire* tires[4] = {&tpms_fl, &tpms_fr, &tpms_rl, &tpms_rr};
            return tires[i];
        }
    }
    // Auto-learn: assign to next free slot
    for (int i = 0; i < 4; i++) {
        if (learned_ids[i] == 0) {
            learned_ids[i] = id;
            saveLearnedIds();
            const char* names[] = {"FL", "FR", "RL", "RR"};
            Serial.printf("TPMS: Learned sensor 0x%06X → %s\n", id, names[i]);
            TpmsTire* tires[4] = {&tpms_fl, &tpms_fr, &tpms_rl, &tpms_rr};
            return tires[i];
        }
    }
    return nullptr; // All 4 slots filled, unknown sensor
}

// ── Pulse ring buffer (ISR → main loop) ──────────────────────────────────────
// Each entry: bits[30:0] = width in μs, bit[31] = level AFTER the edge
#define PULSE_BUF_SIZE 512

static volatile uint32_t pulse_buf[PULSE_BUF_SIZE];
static volatile uint16_t buf_head = 0;
static uint16_t buf_tail = 0;

static volatile bool rf_level = false;  // current signal level, toggled in ISR

static void IRAM_ATTR rfISR() {
    static uint32_t last_us = 0;
    uint32_t now = micros();
    uint32_t width = now - last_us;
    last_us = now;

    rf_level = !rf_level;  // toggle: rf_level = NEW level after this edge

    if (width >= 30 && width <= 25000) {
        // Store: width + new level in bit 31
        pulse_buf[buf_head] = (width & 0x7FFFFFFFU) | (rf_level ? 0x80000000U : 0U);
        buf_head = (buf_head + 1) % PULSE_BUF_SIZE;
    }
}

// ── Pulse classifier ──────────────────────────────────────────────────────────
// Nominal 9600 baud Manchester: T=104μs, T/2=52μs
// Tolerances ±50%: SHORT = 26–80μs, LONG = 81–170μs, GAP = >4000μs

#define SHORT_MIN 26
#define SHORT_MAX 80
#define LONG_MIN  81
#define LONG_MAX  170
#define GAP_MIN   4000

enum PulseType { P_SHORT, P_LONG, P_GAP, P_NOISE };

static PulseType classify(uint32_t us) {
    if (us >= GAP_MIN)                      return P_GAP;
    if (us >= LONG_MIN && us <= LONG_MAX)   return P_LONG;
    if (us >= SHORT_MIN && us <= SHORT_MAX) return P_SHORT;
    return P_NOISE;
}

// ── Manchester decoder state ──────────────────────────────────────────────────
#define MAX_BITS 96

static uint8_t  bit_buf[MAX_BITS];
static int      bit_count    = 0;
static bool     in_preamble  = false;
static int      preamble_cnt = 0;
static bool     half_pending = false;
static bool     pending_half = false;  // value of pending half-bit (true = H)

static void resetDecoder() {
    bit_count    = 0;
    in_preamble  = false;
    preamble_cnt = 0;
    half_pending = false;
}

// Push a half-bit. If two halfs are ready, decode one Manchester bit.
// Returns false on Manchester violation (resets decoder).
static bool pushHalf(bool level_high) {
    if (!half_pending) {
        half_pending  = true;
        pending_half  = level_high;
        return true;
    }

    bool h0 = pending_half;
    bool h1 = level_high;
    half_pending = false;

    if (h0 == true  && h1 == false) {
        // HIGH then LOW → bit '0'
        if (bit_count < MAX_BITS) bit_buf[bit_count++] = 0;
        return true;
    }
    if (h0 == false && h1 == true) {
        // LOW then HIGH → bit '1'
        if (bit_count < MAX_BITS) bit_buf[bit_count++] = 1;
        return true;
    }

    // Manchester violation (H,H or L,L) — reset
    resetDecoder();
    return false;
}

// ── Packet decoder ────────────────────────────────────────────────────────────

static void updateTire(uint32_t id, float kpa, int8_t temp, bool bat_ok, bool alarm) {
    TpmsTire* t = tireForId(id);
    if (!t) return;

    t->sensorId     = id;
    t->pressureKpa  = kpa;
    t->tempC        = temp;
    t->batteryOk    = bat_ok;
    t->alarm        = alarm;
    t->lastUpdateMs = millis();
    t->valid        = true;

    Serial.printf("TPMS: 0x%06X → %.1f kPa  %d°C  bat=%s  alarm=%s\n",
                  id, kpa, temp, bat_ok ? "OK" : "LOW", alarm ? "YES" : "NO");
}

// Try to interpret 8 bytes as a TPMS packet.
// Generic Chinese TPMS format (most common):
//   Bytes 0-2: Sensor ID
//   Byte  3:   Flags (bit7=battery_low, bit6=pressure_alarm)
//   Byte  4:   Pressure (raw)
//   Byte  5:   Temperature (raw)
//   Bytes 6-7: Ignored / CRC
static bool tryParseBytes(const uint8_t* b, int n) {
    if (n < 6) return false;

    uint32_t id    = ((uint32_t)b[0] << 16) | ((uint32_t)b[1] << 8) | b[2];
    uint8_t  flags = b[3];
    uint8_t  raw_p = b[4];
    uint8_t  raw_t = b[5];

    if (id == 0 || id == 0xFFFFFF) return false;

    // Try common pressure encodings and accept the first that lands in range
    float kpa = -1;
    float candidates[] = {
        raw_p * 1.724f,   // 0.25 PSI units
        raw_p * 2.0f,     // kPa / 2
        raw_p * 0.862f,   // 0.125 PSI units
        (float)raw_p,     // kPa directly
    };
    for (float c : candidates) {
        if (c >= 80 && c <= 400) { kpa = c; break; }
    }
    if (kpa < 0) return false;

    // Try common temperature encodings
    int8_t temp = (int8_t)(raw_t - 50);
    if (temp < -40 || temp > 125) {
        temp = (int8_t)raw_t;
        if (temp < -40 || temp > 125) return false;
    }

    bool bat_ok = !(flags & 0x80);
    bool alarm  =  (flags & 0x40);

    updateTire(id, kpa, temp, bat_ok, alarm);
    return true;
}

// Convert bit buffer to bytes and try all 8 bit offsets
static void tryDecodePacket() {
    if (bit_count < 48) return;

    uint8_t bytes[8];
    int     max_offset = min(bit_count - 48, 7);

    for (int offset = 0; offset <= max_offset; offset++) {
        int n_bytes = min((bit_count - offset) / 8, 8);
        for (int b = 0; b < n_bytes; b++) {
            bytes[b] = 0;
            for (int i = 0; i < 8; i++) {
                if (bit_buf[offset + b * 8 + i]) {
                    bytes[b] |= (1 << (7 - i));
                }
            }
        }
        if (tryParseBytes(bytes, n_bytes)) return;

        // Also try bit-inverted (some sensors invert Manchester polarity)
        for (int b = 0; b < n_bytes; b++) bytes[b] ^= 0xFF;
        if (tryParseBytes(bytes, n_bytes)) return;
    }
}

// ── Process one pulse from the ring buffer ────────────────────────────────────
static void processPulse(uint32_t raw) {
    uint32_t  width      = raw & 0x7FFFFFFFU;
    bool      new_level  = (raw >> 31) & 1;
    bool      pulse_high = !new_level;   // level that just ENDED
    PulseType pt         = classify(width);

    if (pt == P_GAP) {
        tryDecodePacket();
        resetDecoder();
        return;
    }

    if (pt == P_NOISE) {
        resetDecoder();
        return;
    }

    // Preamble: at least 8 consecutive SHORT pulses before entering data mode
    if (!in_preamble) {
        if (pt == P_SHORT) {
            preamble_cnt++;
            if (preamble_cnt >= 8) {
                in_preamble  = true;
                half_pending = false;
                bit_count    = 0;
            }
        } else {
            // LONG pulse with no preamble yet — could be a sync from a longer preamble
            if (preamble_cnt >= 4) {
                in_preamble  = true;
                half_pending = false;
                bit_count    = 0;
            } else {
                preamble_cnt = 0;
            }
        }
        return;
    }

    // Data mode: Manchester decode
    if (pt == P_SHORT) {
        pushHalf(pulse_high);
    } else { // P_LONG: two half-bits of same level
        if (!pushHalf(pulse_high)) return;
        pushHalf(pulse_high);
    }
}

// ── Calibration API ──────────────────────────────────────────────────────────

void tpmsResetAll() {
    for (int i = 0; i < 4; i++) learned_ids[i] = 0;
    saveLearnedIds();
    tpms_fl = tpms_fr = tpms_rl = tpms_rr = {0, 0, 0, true, false, 0, false};
    Serial.println("TPMS: All sensor assignments cleared");
}

void tpmsSwap(int posA, int posB) {
    if (posA < 0 || posA > 3 || posB < 0 || posB > 3 || posA == posB) return;
    uint32_t tmp = learned_ids[posA];
    learned_ids[posA] = learned_ids[posB];
    learned_ids[posB] = tmp;
    saveLearnedIds();

    TpmsTire* tires[4] = {&tpms_fl, &tpms_fr, &tpms_rl, &tpms_rr};
    TpmsTire tmpTire = *tires[posA];
    *tires[posA] = *tires[posB];
    *tires[posB] = tmpTire;

    const char* names[] = {"FL", "FR", "RL", "RR"};
    Serial.printf("TPMS: Swapped %s ↔ %s\n", names[posA], names[posB]);
}

uint32_t tpmsGetLearnedId(int pos) {
    if (pos < 0 || pos > 3) return 0;
    return learned_ids[pos];
}

String tpmsCalibrationJSON() {
    JsonDocument doc;
    const char* names[] = {"fl", "fr", "rl", "rr"};
    JsonObject sensors = doc["sensors"].to<JsonObject>();
    for (int i = 0; i < 4; i++) {
        JsonObject s = sensors[names[i]].to<JsonObject>();
        if (learned_ids[i] != 0) {
            char id_str[10];
            snprintf(id_str, sizeof(id_str), "0x%06X", learned_ids[i]);
            s["id"]      = id_str;
            s["learned"] = true;
        } else {
            s["id"]      = "0x000000";
            s["learned"] = false;
        }
    }
    String out;
    serializeJson(doc, out);
    return out;
}

// ── Public API ────────────────────────────────────────────────────────────────

void initTpms() {
    loadLearnedIds();

    rf_level = (digitalRead(TPMS_RF_PIN) == HIGH);
    pinMode(TPMS_RF_PIN, INPUT);
    attachInterrupt(digitalPinToInterrupt(TPMS_RF_PIN), rfISR, CHANGE);

    Serial.println("TPMS: RF receiver initialized on GPIO " + String(TPMS_RF_PIN));
    for (int i = 0; i < 4; i++) {
        const char* names[] = {"FL", "FR", "RL", "RR"};
        if (learned_ids[i] != 0) {
            Serial.printf("TPMS: %s → sensor 0x%06X\n", names[i], learned_ids[i]);
        }
    }
}

void handleTpms() {
    while (buf_tail != buf_head) {
        uint32_t entry = pulse_buf[buf_tail];
        buf_tail = (buf_tail + 1) % PULSE_BUF_SIZE;
        processPulse(entry);
    }
}
