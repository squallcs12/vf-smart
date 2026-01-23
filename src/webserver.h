#ifndef WEBSERVER_H
#define WEBSERVER_H

#include <ESPAsyncWebServer.h>
#include "config.h"
#include "pins.h"
#include "status.h"
#include "storage.h"
#include "websocket.h"
#include <ArduinoJson.h>
#include <WiFi.h>

extern AsyncWebServer server;

bool authenticateRequest(AsyncWebServerRequest *request);
void sendUnauthorized(AsyncWebServerRequest *request);
void setupWebServer();
void setupOnboardingServer();

#endif // WEBSERVER_H
