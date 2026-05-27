// WebSocket removed. Car status is sent via BLE delta protocol (see ble_server.cpp).
#include "websocket.h"

// No-op: BLE delta in handleBleServer() picks up changes on the next loop tick.
void broadcastStatus() {}
