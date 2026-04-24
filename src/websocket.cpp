// WebSocket removed. Car status is sent via BLE delta protocol (see ble_client.cpp).
#include "websocket.h"

// No-op: BLE delta in handleBleClient() picks up changes on the next loop tick.
void broadcastStatus() {}
