#include <Arduino.h>
#include <ESPAsyncWebServer.h>
#include "webserver.h"
#include "webserver/status_endpoint.h"
#include "webserver/lock_endpoints.h"
#include "webserver/accessory_endpoints.h"
#include "webserver/window_endpoints.h"
#include "webserver/buzzer_endpoint.h"
#include "webserver/charger_endpoint.h"
#include "webserver/light_reminder_endpoint.h"
#include "webserver/configure_endpoints.h"
#include "webserver/dashboard_endpoint.h"
#include "webserver/onboarding_endpoints.h"

AsyncWebServer server(80);

void setupWebServer() {
  // Setup WebSocket
  ws.onEvent(onWebSocketEvent);
  server.addHandler(&ws);

  // Register all endpoint modules
  registerStatusEndpoint(server);
  registerLockEndpoints(server);
  registerAccessoryEndpoints(server);
  registerWindowEndpoints(server);
  registerBuzzerEndpoint(server);
  registerChargerEndpoint(server);
  registerLightReminderEndpoint(server);
  registerConfigureEndpoints(server);
  registerDashboardEndpoint(server);

  // Handle 404
  server.onNotFound([](AsyncWebServerRequest *request){
    request->send(404, "text/plain", "Not found");
  });

  server.begin();
  Serial.println("Web server started on port 80");
}

void setupOnboardingServer() {
  // Register onboarding endpoints
  registerOnboardingEndpoints(server);

  server.begin();
  Serial.println("Onboarding web server started on port 80");
}
