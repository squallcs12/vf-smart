#ifndef WEBSERVER_LOCK_ENDPOINTS_H
#define WEBSERVER_LOCK_ENDPOINTS_H

#include <ESPAsyncWebServer.h>

// Register lock/unlock endpoints
void registerLockEndpoints(AsyncWebServer& server);

#endif // WEBSERVER_LOCK_ENDPOINTS_H
