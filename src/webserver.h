#ifndef WEBSERVER_H
#define WEBSERVER_H

#include <ESPAsyncWebServer.h>

extern AsyncWebServer server;

bool authenticateRequest(AsyncWebServerRequest *request);
void sendUnauthorized(AsyncWebServerRequest *request);
void setupWebServer();
void setupOnboardingServer();

#endif // WEBSERVER_H
