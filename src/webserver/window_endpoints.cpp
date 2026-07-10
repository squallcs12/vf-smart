#include "window_endpoints.h"
#include "auth.h"
#include "../pins.h"
#include "../controls/window_control.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerWindowEndpoints(AsyncWebServer& server) {
  // POST /car/windows/down - Control windows down
  server.on("/car/windows/down", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String side = "";
    String state = "";

    if (request->hasParam("side", true)) {
      side = request->getParam("side", true)->value();
    }
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    bool validRequest = false;

    bool moving = (state == "on");
    if ((state == "on" || state == "off") &&
        (side == "left" || side == "right" || side == "both")) {
      if (side == "left" || side == "both")  moveWindow(VF3_WINDOW_LEFT_DOWN, moving);
      if (side == "right" || side == "both") moveWindow(VF3_WINDOW_RIGHT_DOWN, moving);
      validRequest = true;
    }

    if (!validRequest) {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid parameters. Use side='left'/'right'/'both' and state='on'/'off'";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Window down control updated";
    doc["side"] = side;
    doc["state"] = state;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

  // POST /car/windows/up - Control windows up (close)
  server.on("/car/windows/up", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String side = "";
    String state = "";

    if (request->hasParam("side", true)) {
      side = request->getParam("side", true)->value();
    }
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    bool validRequest = false;

    bool moving = (state == "on");
    if ((state == "on" || state == "off") &&
        (side == "left" || side == "right" || side == "both")) {
      if (side == "left" || side == "both")  moveWindow(VF3_WINDOW_LEFT_UP, moving);
      if (side == "right" || side == "both") moveWindow(VF3_WINDOW_RIGHT_UP, moving);
      validRequest = true;
    }

    if (!validRequest) {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid parameters. Use side='left'/'right'/'both' and state='on'/'off'";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Window up control updated";
    doc["side"] = side;
    doc["state"] = state;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
