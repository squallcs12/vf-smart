#include "websocket.h"

AsyncWebSocket ws("/ws");

void onWebSocketEvent(AsyncWebSocket *server, AsyncWebSocketClient *client, AwsEventType type, void *arg, uint8_t *data, size_t len) {
  switch (type) {
    case WS_EVT_CONNECT:
      Serial.printf("WebSocket client #%u connected from %s\n", client->id(), client->remoteIP().toString().c_str());
      // Send current status to newly connected client
      client->text(getCarStatusJSON());
      break;

    case WS_EVT_DISCONNECT:
      Serial.printf("WebSocket client #%u disconnected\n", client->id());
      break;

    case WS_EVT_DATA:
      handleWebSocketMessage(client, data, len);
      break;

    case WS_EVT_PONG:
    case WS_EVT_ERROR:
      break;
  }
}

void handleWebSocketMessage(AsyncWebSocketClient *client, uint8_t *data, size_t len) {
  // WebSocket is read-only for monitoring - ignore incoming commands
  // All control commands should use HTTP API endpoints with authentication

  Serial.printf("WebSocket message received from client #%u (ignored - use HTTP API for commands)\n", client->id());

  // Optionally send current status as a courtesy response
  client->text(getCarStatusJSON());
}

void broadcastStatus() {
  if (ws.count() > 0) {
    ws.textAll(getCarStatusJSON());
  }
}
