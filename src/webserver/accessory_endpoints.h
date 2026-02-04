#ifndef WEBSERVER_ACCESSORY_ENDPOINTS_H
#define WEBSERVER_ACCESSORY_ENDPOINTS_H

#include <ESPAsyncWebServer.h>

// Register accessory control endpoints (power, inside cameras)
void registerAccessoryEndpoints(AsyncWebServer& server);

#endif // WEBSERVER_ACCESSORY_ENDPOINTS_H
