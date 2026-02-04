#include "light_reminder_endpoint.h"
#include "auth.h"
#include "../controls/light_reminder.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerLightReminderEndpoint(AsyncWebServer& server) {
  // POST /car/light-reminder - Control light reminder
  server.on("/car/light-reminder", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on" || state == "enable") {
      light_reminder_enabled = true;
    } else if (state == "off" || state == "disable") {
      light_reminder_enabled = false;
      last_light_reminder = 0;  // Reset timer
    } else if (state == "toggle") {
      light_reminder_enabled = !light_reminder_enabled;
      if (!light_reminder_enabled) {
        last_light_reminder = 0;  // Reset timer when disabled
      }
    } else {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid state. Use 'on', 'off', 'enable', 'disable', or 'toggle'";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = light_reminder_enabled ? "Light reminder enabled" : "Light reminder disabled";
    doc["light_reminder_enabled"] = light_reminder_enabled;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
