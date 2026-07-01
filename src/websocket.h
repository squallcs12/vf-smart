#ifndef WEBSOCKET_H
#define WEBSOCKET_H

#include <ESPAsyncWebServer.h>

/**
 * Real-time car status over WebSocket (endpoint: /ws).
 *
 * Authentication: after connecting, a client must send an auth frame as its
 * first message before any status is streamed:
 *     {"auth":"<api_key>"}
 * The server replies {"auth":"ok"} (then begins streaming) or {"auth":"failed"}
 * and disconnects. Clients that don't authenticate within a few seconds are
 * dropped. Unauthenticated clients never receive status frames.
 *
 * Wire format (the same delta protocol the old BLE server used):
 *   Full  (on first frame after auth + every 60 s heartbeat):
 *     "F|S:<vals>|D:<vals>|W:<vals>|E:<vals>|L:<vals>|P:<vals>|C:<vals>|X:<vals>"
 *   Delta (on change only):
 *     "U|<only changed group entries>"
 *
 * Group IDs:
 *   S = sensors    (brake, steering, voltage, gear)
 *   D = doors      (fl, fr, trunk, locked)
 *   W = windows    (left_state, right_state)
 *   E = seats      (flo, fro, flb, frb)
 *   L = lights     (demi, normal)
 *   P = proximity  (rear_l, rear_r)
 *   C = controls   (brake_pressed, acc_power, cameras, car_lock, car_unlock)
 *   X = misc       (charging, lock_state, wca, wcr_secs, lr, is_night)
 */

// Register the /ws WebSocket handler on the given server.
void setupWebSocket(AsyncWebServer& server);

// Drive heartbeat + delta detection. Call once per control-loop tick.
void handleWebSocket();

// True while at least one authenticated client is connected to /ws.
bool hasWebSocketClient();

// Push an immediate delta to authenticated clients (used by command endpoints
// after they change state). Safe no-op when none are connected.
void broadcastStatus();

#endif // WEBSOCKET_H
