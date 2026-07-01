#include <Arduino.h>
#include <ESPAsyncWebServer.h>
#include "webserver.h"
#include "webserver/status_endpoint.h"
#include "webserver/lock_endpoints.h"
#include "webserver/accessory_endpoints.h"
#include "webserver/window_endpoints.h"
#include "webserver/buzzer_endpoint.h"
#include "webserver/charger_endpoint.h"
#include "webserver/frunk_endpoint.h"
#include "webserver/light_reminder_endpoint.h"
#include "webserver/configure_endpoints.h"
#include "webserver/dashboard_endpoint.h"
#include "webserver/onboarding_endpoints.h"
#include "webserver/vehicle_accessories_endpoints.h"
#include "webserver/ota_endpoint.h"
#include "webserver/tpms_endpoint.h"

AsyncWebServer server(80);

// Log-only handler: registered first so its canHandle() runs for every incoming
// request (matched routes, 404s, and the /ws upgrade). It logs and returns false
// so the real handler still matches and serves the request.
class RequestLogger : public AsyncWebHandler {
public:
  bool canHandle(AsyncWebServerRequest *request) override {
    IPAddress ip = request->client() ? request->client()->remoteIP() : IPAddress();
    Serial.printf("[HTTP] %s %s from %s\n",
                  request->methodToString(),
                  request->url().c_str(),
                  ip.toString().c_str());
    return false;  // never handle — just log
  }
};
static RequestLogger requestLogger;

void setupWebServer() {
  // Log every request (must be added before any real handler)
  server.addHandler(&requestLogger);

  // Register all endpoint modules
  registerStatusEndpoint(server);
  registerLockEndpoints(server);
  registerAccessoryEndpoints(server);
  registerWindowEndpoints(server);
  registerBuzzerEndpoint(server);
  registerChargerEndpoint(server);
  registerFrunkEndpoint(server);
  registerLightReminderEndpoint(server);
  registerConfigureEndpoints(server);
  registerDashboardEndpoint(server);
  registerVehicleAccessoriesEndpoints(server);
  registerOTAEndpoint(server);
  registerTpmsEndpoint(server);

  // Real-time car status stream (full + delta frames, 60 s heartbeat)
  setupWebSocket(server);

  // Handle 404
  server.onNotFound([](AsyncWebServerRequest *request){
    request->send(404, "text/plain", "Not found");
  });

  server.begin();
  Serial.println("Web server started on port 80");
}

void setupOnboardingServer() {
  // Log every request (must be added before any real handler)
  server.addHandler(&requestLogger);

  // Register onboarding endpoints
  registerOnboardingEndpoints(server);

  server.begin();
  Serial.println("Onboarding web server started on port 80");
}
