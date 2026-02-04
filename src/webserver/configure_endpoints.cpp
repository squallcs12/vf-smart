#include "configure_endpoints.h"
#include "auth.h"
#include "../config.h"
#include "../storage.h"
#include <Arduino.h>
#include <LittleFS.h>

void registerConfigureEndpoints(AsyncWebServer& server) {
  // Serve static HTML files
  server.on("/configure.html", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/configure.html", "text/html");
  });

  server.on("/error.html", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/error.html", "text/html");
  });

  server.on("/success.html", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/success.html", "text/html");
  });

  server.on("/restart.html", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/restart.html", "text/html");
  });

  // GET /configure - Reconfiguration page (redirect with current values)
  server.on("/configure", HTTP_GET, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    // Redirect to configure.html with current values as URL parameters
    String redirectUrl = "/configure.html?current_ssid=" + configured_ssid;
    redirectUrl += "&current_password=" + configured_password;
    redirectUrl += "&current_api_key=" + configured_api_key;
    request->redirect(redirectUrl);
  });

  // POST /configure - Handle reconfiguration submission
  server.on("/configure", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String ssid = "";
    String password = "";
    String api_key = "";

    if (request->hasParam("ssid", true)) {
      ssid = request->getParam("ssid", true)->value();
    }
    if (request->hasParam("password", true)) {
      password = request->getParam("password", true)->value();
    }
    if (request->hasParam("api_key", true)) {
      api_key = request->getParam("api_key", true)->value();
    }

    // Validate inputs
    if (ssid.length() < 1 || password.length() < 1 || api_key.length() < 8) {
      request->redirect("/error.html?message=Invalid%20configuration.%20API%20Key%20must%20be%20at%20least%208%20characters.&back=/configure");
      return;
    }

    // Save configuration
    saveConfiguration(ssid, password, api_key);

    // Redirect to success page with data
    String redirectUrl = "/success.html?ssid=" + ssid + "&api_key=" + api_key;
    request->redirect(redirectUrl);
  });

  // GET /restart - Restart the device
  server.on("/restart", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/restart.html", "text/html");

    // Restart after 1 second
    delay(1000);
    ESP.restart();
  });
}
