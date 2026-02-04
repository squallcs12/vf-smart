#ifndef WEBSERVER_AUTH_H
#define WEBSERVER_AUTH_H

#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>

// Authentication functions
bool authenticateRequest(AsyncWebServerRequest *request);
void sendUnauthorized(AsyncWebServerRequest *request);

#endif // WEBSERVER_AUTH_H
