#include "lock_endpoints.h"
#include "auth.h"
#include "../pins.h"
#include "../websocket.h"
#include <ArduinoJson.h>

void registerLockEndpoints(AsyncWebServer& server) {
  // POST /car/lock - Lock the car
  server.on("/car/lock", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    vf3_car_lock = HIGH;
    vf3_car_unlock = LOW;
    safeDigitalWrite(VF3_CAR_LOCK, WRITE_OFF);
    safeDigitalWrite(VF3_CAR_UNLOCK, WRITE_ON);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Car locked";
    doc["car_lock"] = vf3_car_lock;
    doc["car_unlock"] = vf3_car_unlock;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });

  // POST /car/unlock - Unlock the car
  server.on("/car/unlock", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    vf3_car_lock = LOW;
    vf3_car_unlock = HIGH;
    safeDigitalWrite(VF3_CAR_LOCK, WRITE_ON);
    safeDigitalWrite(VF3_CAR_UNLOCK, WRITE_OFF);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Car unlocked";
    doc["car_lock"] = vf3_car_lock;
    doc["car_unlock"] = vf3_car_unlock;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);

    // Broadcast updated status immediately
    broadcastStatus();
  });
}
