#include "vehicle_accessories_endpoints.h"
#include "auth.h"
#include "../pins.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerVehicleAccessoriesEndpoints(AsyncWebServer& server) {
  // POST /car/side-mirrors - Control side mirrors open/close
  server.on("/car/side-mirrors", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String action = "";
    if (request->hasParam("action", true)) {
      action = request->getParam("action", true)->value();
    }

    if (action == "open") {
      // Pulse OPEN pin for 1 second
      safeDigitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_ON);
      delay(1000);
      safeDigitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_OFF);

      JsonDocument doc;
      doc["success"] = true;
      doc["message"] = "Side mirrors opening";
      doc["action"] = "open";

      String output;
      serializeJson(doc, output);
      request->send(200, "application/json", output);
    } else if (action == "close") {
      // Pulse CLOSE pin for 1 second
      safeDigitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_ON);
      delay(1000);
      safeDigitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_OFF);

      JsonDocument doc;
      doc["success"] = true;
      doc["message"] = "Side mirrors closing";
      doc["action"] = "close";

      String output;
      serializeJson(doc, output);
      request->send(200, "application/json", output);
    } else {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid action. Use 'open' or 'close'";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
