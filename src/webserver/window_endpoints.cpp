#include "window_endpoints.h"
#include "auth.h"
#include "../pins.h"
#include "../controls/window_control.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerWindowEndpoints(AsyncWebServer& server) {
  // POST /car/windows/close - Close windows (30 second timer)
  server.on("/car/windows/close", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    window_close_timer = millis();
    safeDigitalWrite(VF3_WINDOW_LEFT_UP, WRITE_OFF);
    safeDigitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_OFF);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Windows closing for 30 seconds";
    doc["window_close_active"] = true;
    doc["duration_ms"] = WINDOW_CLOSE_DURATION;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

  // POST /car/windows/stop - Stop window operation
  server.on("/car/windows/stop", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    window_close_timer = 0;
    safeDigitalWrite(VF3_WINDOW_LEFT_UP, WRITE_ON);
    safeDigitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_ON);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Window operation stopped";
    doc["window_close_active"] = false;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

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

    if (side == "left" && state == "on") {
      safeDigitalWrite(VF3_WINDOW_LEFT_DOWN, WRITE_OFF);
      validRequest = true;
    } else if (side == "left" && state == "off") {
      safeDigitalWrite(VF3_WINDOW_LEFT_DOWN, WRITE_ON);
      validRequest = true;
    } else if (side == "right" && state == "on") {
      safeDigitalWrite(VF3_WINDOW_RIGHT_DOWN, WRITE_OFF);
      validRequest = true;
    } else if (side == "right" && state == "off") {
      safeDigitalWrite(VF3_WINDOW_RIGHT_DOWN, WRITE_ON);
      validRequest = true;
    } else if (side == "both" && state == "on") {
      safeDigitalWrite(VF3_WINDOW_LEFT_DOWN, WRITE_OFF);
      safeDigitalWrite(VF3_WINDOW_RIGHT_DOWN, WRITE_OFF);
      validRequest = true;
    } else if (side == "both" && state == "off") {
      safeDigitalWrite(VF3_WINDOW_LEFT_DOWN, WRITE_ON);
      safeDigitalWrite(VF3_WINDOW_RIGHT_DOWN, WRITE_ON);
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

    if (side == "left" && state == "on") {
      safeDigitalWrite(VF3_WINDOW_LEFT_UP, WRITE_OFF);
      validRequest = true;
    } else if (side == "left" && state == "off") {
      safeDigitalWrite(VF3_WINDOW_LEFT_UP, WRITE_ON);
      validRequest = true;
    } else if (side == "right" && state == "on") {
      safeDigitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_OFF);
      validRequest = true;
    } else if (side == "right" && state == "off") {
      safeDigitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_ON);
      validRequest = true;
    } else if (side == "both" && state == "on") {
      safeDigitalWrite(VF3_WINDOW_LEFT_UP, WRITE_OFF);
      safeDigitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_OFF);
      validRequest = true;
    } else if (side == "both" && state == "off") {
      safeDigitalWrite(VF3_WINDOW_LEFT_UP, WRITE_ON);
      safeDigitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_ON);
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
