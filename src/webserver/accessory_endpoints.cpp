#include "accessory_endpoints.h"
#include "auth.h"
#include "../pins.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerAccessoryEndpoints(AsyncWebServer& server) {
  // POST /car/accessory-power - Toggle accessory power
  server.on("/car/accessory-power", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on") {
      self_accessory_power = HIGH;
      safeDigitalWrite(SELF_ACCESSORY_POWER, WRITE_OFF);
    } else if (state == "off") {
      self_accessory_power = LOW;
      safeDigitalWrite(SELF_ACCESSORY_POWER, WRITE_ON);
    } else if (state == "toggle") {
      self_accessory_power = !self_accessory_power;
      safeDigitalWrite(SELF_ACCESSORY_POWER, self_accessory_power);
    } else {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid state. Use 'on', 'off', or 'toggle'";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Accessory power updated";
    doc["accessory_power"] = self_accessory_power;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

  // POST /car/inside-cameras - Control inside cameras
  server.on("/car/inside-cameras", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on") {
      safeDigitalWrite(SELF_INSIDE_CARMERAS, WRITE_ON);
      self_inside_cameras = WRITE_ON;
    } else if (state == "off") {
      safeDigitalWrite(SELF_INSIDE_CARMERAS, WRITE_OFF);
      self_inside_cameras = WRITE_OFF;
    } else if (state == "toggle") {
      self_inside_cameras = (self_inside_cameras == WRITE_ON) ? WRITE_OFF : WRITE_ON;
      safeDigitalWrite(SELF_INSIDE_CARMERAS, self_inside_cameras);
    } else {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid state. Use 'on', 'off', or 'toggle'";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Inside cameras updated";
    doc["inside_cameras"] = self_inside_cameras;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
