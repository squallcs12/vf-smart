#include "frunk_endpoint.h"
#include "auth.h"
#include "../pins.h"
#include "../websocket.h"
#include "../pcf8575_io.h"
#include <ArduinoJson.h>

// Rate limiting
static unsigned long last_frunk_unlock = 0;
#define FRUNK_UNLOCK_COOLDOWN 2000  // 2 seconds between operations

void registerFrunkEndpoint(AsyncWebServer& server) {
  // POST /car/frunk/open - Unlock and open front trunk
  server.on("/car/frunk/open", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    // Safety check: prevent operation while in Drive
    if (vf3_gear_drive == HIGH) {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Cannot open front trunk while vehicle is in Drive";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    // Rate limiting check
    if (millis() - last_frunk_unlock < FRUNK_UNLOCK_COOLDOWN) {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Too many requests - wait 2 seconds between operations";

      String output;
      serializeJson(doc, output);
      request->send(429, "application/json", output);
      return;
    }

    // Trigger front trunk unlock (8 second pulse)
    safeDigitalWrite(VF3_FRONT_TRUNK_UNLOCK, WRITE_ON);
    delay(8000);
    safeDigitalWrite(VF3_FRONT_TRUNK_UNLOCK, WRITE_OFF);

    last_frunk_unlock = millis();

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Front trunk unlocked";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
