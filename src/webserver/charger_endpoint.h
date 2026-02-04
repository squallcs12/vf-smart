#ifndef WEBSERVER_CHARGER_ENDPOINT_H
#define WEBSERVER_CHARGER_ENDPOINT_H

#include <ESPAsyncWebServer.h>

// Register charger unlock endpoint
void registerChargerEndpoint(AsyncWebServer& server);

#endif // WEBSERVER_CHARGER_ENDPOINT_H
