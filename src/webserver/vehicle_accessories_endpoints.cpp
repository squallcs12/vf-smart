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

  // POST /car/odo-screen - Control ODO screen on/off/toggle
  server.on("/car/odo-screen", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on") {
      safeDigitalWrite(SELF_ODO_SCREEN, WRITE_ON);
      self_odo_screen = WRITE_ON;
    } else if (state == "off") {
      safeDigitalWrite(SELF_ODO_SCREEN, WRITE_OFF);
      self_odo_screen = WRITE_OFF;
    } else if (state == "toggle") {
      self_odo_screen = (self_odo_screen == WRITE_ON) ? WRITE_OFF : WRITE_ON;
      safeDigitalWrite(SELF_ODO_SCREEN, self_odo_screen);
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
    doc["message"] = "ODO screen updated";
    doc["odo_screen"] = (self_odo_screen == WRITE_ON) ? "on" : "off";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

  // POST /car/armrest - Control armrest on/off/toggle
  server.on("/car/armrest", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on") {
      safeDigitalWrite(SELF_ARMREST, WRITE_ON);
      self_armrest = WRITE_ON;
    } else if (state == "off") {
      safeDigitalWrite(SELF_ARMREST, WRITE_OFF);
      self_armrest = WRITE_OFF;
    } else if (state == "toggle") {
      self_armrest = (self_armrest == WRITE_ON) ? WRITE_OFF : WRITE_ON;
      safeDigitalWrite(SELF_ARMREST, self_armrest);
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
    doc["message"] = "Armrest updated";
    doc["armrest"] = (self_armrest == WRITE_ON) ? "on" : "off";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

  // POST /car/dashcam - Control dashcam on/off/toggle
  server.on("/car/dashcam", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on") {
      safeDigitalWrite(SELF_DASHCAM, WRITE_ON);
      self_dashcam = WRITE_ON;
    } else if (state == "off") {
      safeDigitalWrite(SELF_DASHCAM, WRITE_OFF);
      self_dashcam = WRITE_OFF;
    } else if (state == "toggle") {
      self_dashcam = (self_dashcam == WRITE_ON) ? WRITE_OFF : WRITE_ON;
      safeDigitalWrite(SELF_DASHCAM, self_dashcam);
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
    doc["message"] = "Dashcam updated";
    doc["dashcam"] = (self_dashcam == WRITE_ON) ? "on" : "off";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
