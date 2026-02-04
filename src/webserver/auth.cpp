#include "auth.h"
#include "../config.h"

bool authenticateRequest(AsyncWebServerRequest *request) {
  // Check for API key in X-API-Key header
  if (request->hasHeader("X-API-Key")) {
    String headerKey = request->header("X-API-Key");
    if (headerKey == configured_api_key) {
      return true;
    }
  }

  // Check for API key in query parameter
  if (request->hasParam("api_key")) {
    String paramKey = request->getParam("api_key")->value();
    if (paramKey == configured_api_key) {
      return true;
    }
  }

  return false;
}

void sendUnauthorized(AsyncWebServerRequest *request) {
  JsonDocument doc;
  doc["success"] = false;
  doc["message"] = "Unauthorized - Invalid or missing API key";
  String output;
  serializeJson(doc, output);
  request->send(401, "application/json", output);
}
