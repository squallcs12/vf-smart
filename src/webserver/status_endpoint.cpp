#include "status_endpoint.h"
#include "../status.h"

void registerStatusEndpoint(AsyncWebServer& server) {
  // GET /car/status - Return all car status as JSON
  server.on("/car/status", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(200, "application/json", getCarStatusJSON());
  });
}
