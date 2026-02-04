#include "dashboard_endpoint.h"
#include <LittleFS.h>

void registerDashboardEndpoint(AsyncWebServer& server) {
  // Root endpoint - Control dashboard (serve from LittleFS)
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/dashboard.html", "text/html");
  });
}
