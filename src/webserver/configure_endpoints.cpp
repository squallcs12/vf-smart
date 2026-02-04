#include "configure_endpoints.h"
#include "auth.h"
#include "../config.h"
#include "../storage.h"
#include <Arduino.h>

void registerConfigureEndpoints(AsyncWebServer& server) {
  // GET /configure - Reconfiguration page
  server.on("/configure", HTTP_GET, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String html = "<!DOCTYPE html><html><head>";
    html += "<meta charset='UTF-8'>";
    html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
    html += "<title>VF3 Smart - Reconfigure</title>";
    html += "<style>";
    html += "body { font-family: Arial; max-width: 500px; margin: 50px auto; padding: 20px; }";
    html += "h1 { color: #e71e2c; }";
    html += "h3 { color: #333; border-bottom: 2px solid #e71e2c; padding-bottom: 10px; }";
    html += "input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; border: 1px solid #ddd; border-radius: 5px; }";
    html += "button { background: #e71e2c; color: white; padding: 15px; width: 100%; border: none; cursor: pointer; font-size: 16px; border-radius: 5px; margin-top: 10px; }";
    html += "button:hover { background: #c51a26; }";
    html += ".btn-secondary { background: #666; }";
    html += ".btn-secondary:hover { background: #555; }";
    html += ".info { background: #f0f0f0; padding: 15px; margin: 20px 0; border-radius: 5px; }";
    html += ".warning { background: #fff3e0; color: #e65100; padding: 15px; margin: 20px 0; border-radius: 5px; font-weight: bold; }";
    html += "label { font-weight: bold; display: block; margin-top: 15px; }";
    html += "</style>";
    html += "</head><body>";
    html += "<h1>\xF0\x9F\x94\xA7 VF3 Smart Reconfiguration</h1>";
    html += "<div class='warning'>\xE2\x9A\xA0\xEF\xB8\x8F Warning: Changing these settings will restart the device and may disconnect you.</div>";

    html += "<form action='/configure' method='POST'>";
    html += "<h3>WiFi Configuration</h3>";
    html += "<label>WiFi SSID (Network Name)</label>";
    html += "<input type='text' name='ssid' placeholder='Enter WiFi SSID' value='" + configured_ssid + "' required>";
    html += "<label>WiFi Password</label>";
    html += "<input type='password' name='password' placeholder='Enter WiFi Password' value='" + configured_password + "' required>";
    html += "<label>API Key (for secure control)</label>";
    html += "<input type='text' name='api_key' placeholder='Enter API Key' value='" + configured_api_key + "' required>";
    html += "<div class='info'>API Key must be at least 8 characters. This key is used for HTTP API authentication and as MQTT topic prefix.</div>";

    html += "<button type='submit'>\xF0\x9F\x92\xBE Save Configuration & Restart</button>";
    html += "</form>";
    html += "<button class='btn-secondary' onclick='window.location.href=\"/\"' style='margin-top: 10px;'>\xE2\xAC\x85 Back to Dashboard</button>";
    html += "</body></html>";
    request->send(200, "text/html", html);
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
      html += "<a href='/configure'>Go Back</a>";
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
    html += "</style>";
    html += "</head><body>";
    html += "<h1>\xE2\x9C\x85 Configuration Updated!</h1>";
    html += "<div class='success'>";
    html += "Your VF3 Smart device configuration has been updated successfully.<br><br>";
    html += "<strong>SSID:</strong> " + ssid + "<br>";
    html += "<strong>API Key:</strong> " + api_key + " (save this securely!)";
    html += "</div>";
    html += "<div class='info'>The device will restart in 3 seconds. Please reconnect to the WiFi network: <strong>" + ssid + "</strong></div>";
    html += "<script>setTimeout(function(){ window.location.href='/restart'; }, 3000);</script>";
    html += "</body></html>";
    request->send(200, "text/html", html);
  });

  // GET /restart - Restart the device
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
    html += "<h1>\xF0\x9F\x94\x84 Restarting...</h1>";
    html += "<div class='info'>Device is restarting. Please wait 10 seconds then reconnect to your WiFi network.</div>";
    html += "</body></html>";
    request->send(200, "text/html", html);

    // Restart after 1 second
    delay(1000);
    ESP.restart();
  });
}
