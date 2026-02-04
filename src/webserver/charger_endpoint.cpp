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

    // Trigger charger unlock (1 second pulse)
    pcf8575.digitalWrite(VF3_CHARGER_UNLOCK, WRITE_ON);
    delay(1000);
    pcf8575.digitalWrite(VF3_CHARGER_UNLOCK, WRITE_OFF);

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
