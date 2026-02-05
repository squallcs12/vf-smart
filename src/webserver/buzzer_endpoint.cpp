#include "buzzer_endpoint.h"
#include "auth.h"
#include "../pins.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerBuzzerEndpoint(AsyncWebServer& server) {
  // POST /car/buzzer - Control buzzer
  server.on("/car/buzzer", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    int duration = 0;

    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }
    if (request->hasParam("duration", true)) {
      duration = request->getParam("duration", true)->value().toInt();
    }

    if (state == "on") {
      safeDigitalWrite(VF3_BUZZER, WRITE_OFF);
    } else if (state == "off") {
      safeDigitalWrite(VF3_BUZZER, WRITE_ON);
    } else if (state == "beep" && duration > 0) {
      safeDigitalWrite(VF3_BUZZER, WRITE_OFF);
      delay(duration);
      safeDigitalWrite(VF3_BUZZER, WRITE_ON);
    } else {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid parameters. Use state='on'/'off' or state='beep' with duration";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Buzzer control executed";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
