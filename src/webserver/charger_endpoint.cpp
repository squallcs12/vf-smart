#include "charger_endpoint.h"
#include "auth.h"
#include "../pins.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerChargerEndpoint(AsyncWebServer& server) {
  // POST /car/charger-unlock - Manually unlock charger port
  server.on("/car/charger-unlock", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    // Trigger charger unlock: pull pin 1s, then push pin 1s (native GPIO)
    digitalWrite(VF3_CHARGER_UNLOCK_PULL, WRITE_ON);
    delay(1000);
    digitalWrite(VF3_CHARGER_UNLOCK_PULL, WRITE_OFF);
    digitalWrite(VF3_CHARGER_UNLOCK_PUSH, WRITE_ON);
    delay(1000);
    digitalWrite(VF3_CHARGER_UNLOCK_PUSH, WRITE_OFF);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Charger port unlocked";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
