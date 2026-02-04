#include "onboarding_endpoints.h"
#include "../config.h"
#include "../storage.h"
#include <Arduino.h>
#include <LittleFS.h>

void registerOnboardingEndpoints(AsyncWebServer& server) {
  // Onboarding home page with configuration form (serve from LittleFS)
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request){
    // Redirect to onboarding.html with current values and reconfig flag
    String redirectUrl = "/onboarding.html?current_ssid=" + configured_ssid;
    redirectUrl += "&current_password=" + configured_password;
    redirectUrl += "&current_api_key=" + configured_api_key;
    redirectUrl += "&reconfig=" + String(is_configured ? "true" : "false");
    request->redirect(redirectUrl);
  });

  // Serve the actual HTML file
  server.on("/onboarding.html", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(LittleFS, "/onboarding.html", "text/html");
  });

  // Handle configuration submission
  server.on("/configure", HTTP_POST, [](AsyncWebServerRequest *request){
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
      String html = "<!DOCTYPE html><html><head>";
      html += "<meta charset='UTF-8'>";
      html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
      html += "<title>VF3 Smart - Error</title>";
      html += "<style>";
      html += "body { font-family: Arial; max-width: 500px; margin: 50px auto; padding: 20px; text-align: center; }";
      html += ".error { background: #ffebee; color: #c62828; padding: 20px; border-radius: 5px; margin: 20px 0; }";
      html += "a { color: #e71e2c; text-decoration: none; font-weight: bold; }";
      html += "</style>";
      html += "</head><body>";
      html += "<h1>Configuration Error</h1>";
      html += "<div class='error'>Invalid configuration. API Key must be at least 8 characters.</div>";
      html += "<a href='/'>Go Back</a>";
      html += "</body></html>";
      request->send(400, "text/html", html);
      return;
    }

    // Save configuration
    saveConfiguration(ssid, password, api_key);

    // Success page with restart instruction
    String html = "<!DOCTYPE html><html><head>";
    html += "<meta charset='UTF-8'>";
    html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
    html += "<title>VF3 Smart - Success</title>";
    html += "<style>";
    html += "body { font-family: Arial; max-width: 500px; margin: 50px auto; padding: 20px; text-align: center; }";
    html += "h1 { color: #4caf50; }";
    html += ".success { background: #e8f5e9; color: #2e7d32; padding: 20px; border-radius: 5px; margin: 20px 0; }";
    html += ".info { background: #fff3e0; color: #e65100; padding: 15px; margin: 20px 0; border-radius: 5px; }";
    html += "button { background: #e71e2c; color: white; padding: 15px 30px; border: none; cursor: pointer; font-size: 16px; margin-top: 20px; }";
    html += "button:hover { background: #c51a26; }";
    html += "</style>";
    html += "</head><body>";
    html += "<h1>Configuration Saved!</h1>";
    html += "<div class='success'>";
    html += "Your VF3 Smart device has been configured successfully.<br><br>";
    html += "<strong>SSID:</strong> " + ssid + "<br>";
    html += "<strong>API Key:</strong> " + api_key + " (save this securely!)";
    html += "</div>";
    html += "<div class='info'>The device will restart in 3 seconds. After restart, connect to the WiFi network you configured.</div>";
    html += "<script>setTimeout(function(){ window.location.href='/restart'; }, 3000);</script>";
    html += "</body></html>";
    request->send(200, "text/html", html);
  });

  // Restart endpoint
  server.on("/restart", HTTP_GET, [](AsyncWebServerRequest *request){
    String html = "<!DOCTYPE html><html><head>";
    html += "<meta charset='UTF-8'>";
    html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
    html += "<title>VF3 Smart - Restarting</title>";
    html += "<style>";
    html += "body { font-family: Arial; max-width: 500px; margin: 50px auto; padding: 20px; text-align: center; }";
    html += ".info { background: #e3f2fd; color: #0d47a1; padding: 20px; border-radius: 5px; margin: 20px 0; }";
    html += "</style>";
    html += "</head><body>";
    html += "<h1>Restarting...</h1>";
    html += "<div class='info'>Device is restarting. Please wait 10 seconds then connect to your configured WiFi network.</div>";
    html += "</body></html>";
    request->send(200, "text/html", html);

    // Restart after 1 second
    delay(1000);
    ESP.restart();
  });

  // Handle 404
  server.onNotFound([](AsyncWebServerRequest *request){
    request->send(404, "text/plain", "Not found - Please visit / for setup");
  });
}
