#ifndef WEBSOCKET_H
#define WEBSOCKET_H

// WebSocket removed. Car status is sent via BLE delta protocol (see ble_client.cpp).
// broadcastStatus() kept as a no-op so endpoint code compiles unchanged.
void broadcastStatus();

#endif // WEBSOCKET_H
