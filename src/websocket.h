#ifndef WEBSOCKET_H
#define WEBSOCKET_H

#include <ESPAsyncWebServer.h>
#include "config.h"
#include "pins.h"
#include "status.h"
#include <ArduinoJson.h>

extern AsyncWebSocket ws;

void onWebSocketEvent(AsyncWebSocket *server, AsyncWebSocketClient *client, AwsEventType type, void *arg, uint8_t *data, size_t len);
void handleWebSocketMessage(AsyncWebSocketClient *client, uint8_t *data, size_t len);
void broadcastStatus();

#endif // WEBSOCKET_H
