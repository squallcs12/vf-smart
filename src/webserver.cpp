#include <Arduino.h>
#include <ESPAsyncWebServer.h>
#include "webserver.h"

AsyncWebServer server(80);

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
void setupWebServer() {
  // Setup WebSocket
  ws.onEvent(onWebSocketEvent);
  server.addHandler(&ws);

  // GET /car/status - Return all car status as JSON
  server.on("/car/status", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(200, "application/json", getCarStatusJSON());
  });

  // POST /car/lock - Lock the car
  server.on("/car/lock", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    vf3_car_lock = HIGH;
    vf3_car_unlock = LOW;
    digitalWrite(VF3_CAR_LOCK, HIGH);
    digitalWrite(VF3_CAR_UNLOCK, LOW);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Car locked";
    doc["car_lock"] = vf3_car_lock;
    doc["car_unlock"] = vf3_car_unlock;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

  // POST /car/unlock - Unlock the car
  server.on("/car/unlock", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    vf3_car_lock = LOW;
    vf3_car_unlock = HIGH;
    digitalWrite(VF3_CAR_LOCK, LOW);
    digitalWrite(VF3_CAR_UNLOCK, HIGH);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Car unlocked";
    doc["car_lock"] = vf3_car_lock;
    doc["car_unlock"] = vf3_car_unlock;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

  // POST /car/accessory-power - Toggle accessory power
  server.on("/car/accessory-power", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }

    if (state == "on") {
      vf3_accessory_power = HIGH;
      digitalWrite(VF3_ACCESSORY_POWER, HIGH);
    } else if (state == "off") {
      vf3_accessory_power = LOW;
      digitalWrite(VF3_ACCESSORY_POWER, LOW);
    } else if (state == "toggle") {
      vf3_accessory_power = !vf3_accessory_power;
      digitalWrite(VF3_ACCESSORY_POWER, vf3_accessory_power);
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
    doc["message"] = "Accessory power updated";
    doc["accessory_power"] = vf3_accessory_power;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

  // POST /car/windows/close - Close windows (30 second timer)
  server.on("/car/windows/close", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    window_close_timer = millis();
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Windows closing for 30 seconds";
    doc["window_close_active"] = true;
    doc["duration_ms"] = WINDOW_CLOSE_DURATION;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

  // POST /car/windows/stop - Stop window operation
  server.on("/car/windows/stop", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    window_close_timer = 0;
    digitalWrite(VF3_WINDOW_LEFT, LOW);
    digitalWrite(VF3_WINDOW_RIGHT, LOW);

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Window operation stopped";
    doc["window_close_active"] = false;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

  // POST /car/buzzer - Control buzzer
  server.on("/car/buzzer", HTTP_POST, [](AsyncWebServerRequest *request){
    if (!authenticateRequest(request)) {
      sendUnauthorized(request);
      return;
    }

    String state = "";
    int duration = 0;

    if (request->hasParam("state", true)) {
      state = request->getParam("state", true)->value();
    }
    if (request->hasParam("duration", true)) {
      duration = request->getParam("duration", true)->value().toInt();
    }

    if (state == "on") {
      digitalWrite(VF3_BUZZER, HIGH);
    } else if (state == "off") {
      digitalWrite(VF3_BUZZER, LOW);
    } else if (state == "beep" && duration > 0) {
      digitalWrite(VF3_BUZZER, HIGH);
      delay(duration);
      digitalWrite(VF3_BUZZER, LOW);
    } else {
      JsonDocument doc;
      doc["success"] = false;
      doc["message"] = "Invalid parameters. Use state='on'/'off' or state='beep' with duration";

      String output;
      serializeJson(doc, output);
      request->send(400, "application/json", output);
      return;
    }

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Buzzer control executed";

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

  // POST /car/turn-signal - Control turn signals
  server.on("/car/turn-signal", HTTP_POST, [](AsyncWebServerRequest *request){
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
      digitalWrite(VF3_TURN_SIGNAL_L, HIGH);
      validRequest = true;
    } else if (side == "left" && state == "off") {
      digitalWrite(VF3_TURN_SIGNAL_L, LOW);
      validRequest = true;
    } else if (side == "right" && state == "on") {
      digitalWrite(VF3_TURN_SIGNAL_R, HIGH);
      validRequest = true;
    } else if (side == "right" && state == "off") {
      digitalWrite(VF3_TURN_SIGNAL_R, LOW);
      validRequest = true;
    } else if (side == "both" && state == "off") {
      digitalWrite(VF3_TURN_SIGNAL_L, LOW);
      digitalWrite(VF3_TURN_SIGNAL_R, LOW);
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
    doc["message"] = "Turn signal updated";
    doc["side"] = side;
    doc["state"] = state;

    String output;
    serializeJson(doc, output);
    request->send(200, "application/json", output);
  });

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

  // Root endpoint - Control dashboard
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request){
    String html = "<!DOCTYPE html><html><head>";
    html += "<meta charset='UTF-8'>";
    html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
    html += "<title>VF3 Smart Control</title>";
    html += "<style>";
    html += "body { font-family: Arial; max-width: 600px; margin: 20px auto; padding: 20px; background: #f5f5f5; }";
    html += "h1 { color: #e71e2c; text-align: center; }";
    html += ".section { background: white; padding: 20px; margin: 15px 0; border-radius: 10px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }";
    html += ".section h2 { margin-top: 0; color: #333; font-size: 18px; border-bottom: 2px solid #e71e2c; padding-bottom: 10px; }";
    html += ".button-group { display: flex; gap: 10px; margin: 10px 0; flex-wrap: wrap; }";
    html += "button { flex: 1; min-width: 120px; padding: 15px; border: none; border-radius: 5px; font-size: 16px; cursor: pointer; transition: all 0.3s; }";
    html += "button:active { transform: scale(0.95); }";
    html += ".btn-primary { background: #e71e2c; color: white; }";
    html += ".btn-primary:hover { background: #c51a26; }";
    html += ".btn-secondary { background: #4CAF50; color: white; }";
    html += ".btn-secondary:hover { background: #45a049; }";
    html += ".btn-warning { background: #ff9800; color: white; }";
    html += ".btn-warning:hover { background: #e68900; }";
    html += ".btn-danger { background: #f44336; color: white; }";
    html += ".btn-danger:hover { background: #da190b; }";
    html += ".status { padding: 10px; background: #f0f0f0; border-radius: 5px; margin: 10px 0; font-family: monospace; font-size: 12px; max-height: 300px; overflow-y: auto; }";
    html += ".status-item { margin: 5px 0; }";
    html += ".message { padding: 10px; margin: 10px 0; border-radius: 5px; text-align: center; display: none; }";
    html += ".message.success { background: #d4edda; color: #155724; display: block; }";
    html += ".message.error { background: #f8d7da; color: #721c24; display: block; }";
    html += "input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; border: 1px solid #ddd; border-radius: 5px; }";
    html += ".auth-section { background: #fff3e0; border-left: 4px solid #ff9800; }";
    html += ".auth-section input { margin: 5px 0; }";
    html += ".auth-status { padding: 8px; margin: 10px 0; border-radius: 5px; font-size: 14px; text-align: center; }";
    html += ".auth-status.authenticated { background: #d4edda; color: #155724; }";
    html += ".auth-status.not-authenticated { background: #f8d7da; color: #721c24; }";
    html += "</style>";
    html += "</head><body>";
    html += "<h1>\xF0\x9F\x9A\x97 VF3 Smart Control</h1>";

    html += "<div class='section auth-section'>";
    html += "<h2>\xF0\x9F\x94\x91 Authentication</h2>";
    html += "<input type='text' id='apiKeyInput' placeholder='Enter your API Key' value=''>";
    html += "<button class='btn-primary' onclick='setApiKey()' style='width: 100%;'>Save API Key</button>";
    html += "<div id='authStatus' class='auth-status not-authenticated'>Not authenticated - Please enter your API Key</div>";
    html += "</div>";

    html += "<div class='section'>";
    html += "<h2>\xF0\x9F\x94\x90 Lock / Unlock</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-primary' onclick='sendCommand(\"/car/lock\", \"POST\")'>\xF0\x9F\x94\x92 Lock Car</button>";
    html += "<button class='btn-secondary' onclick='sendCommand(\"/car/unlock\", \"POST\")'>\xF0\x9F\x94\x93 Unlock Car</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>\xE2\x9A\xA1 Accessory Power</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-secondary' onclick='sendCommand(\"/car/accessory-power\", \"POST\", \"state=on\")'>Power ON</button>";
    html += "<button class='btn-danger' onclick='sendCommand(\"/car/accessory-power\", \"POST\", \"state=off\")'>Power OFF</button>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/accessory-power\", \"POST\", \"state=toggle\")'>Toggle</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>\xF0\x9F\xAA\x9F Windows</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-primary' onclick='sendCommand(\"/car/windows/close\", \"POST\")'>Close Windows (30s)</button>";
    html += "<button class='btn-danger' onclick='sendCommand(\"/car/windows/stop\", \"POST\")'>\xE2\x8F\xB9 Stop</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>\xF0\x9F\x94\x94 Buzzer</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/buzzer\", \"POST\", \"state=beep&duration=500\")'>Beep (0.5s)</button>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/buzzer\", \"POST\", \"state=beep&duration=1000\")'>Beep (1s)</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>\xF0\x9F\x9A\xA6 Turn Signals</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/turn-signal\", \"POST\", \"side=left&state=on\")'>\xE2\xAC\x85 Left ON</button>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/turn-signal\", \"POST\", \"side=right&state=on\")'>Right ON \xE2\x9E\xA1</button>";
    html += "<button class='btn-danger' onclick='sendCommand(\"/car/turn-signal\", \"POST\", \"side=both&state=off\")'>All OFF</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>\xF0\x9F\x93\x8A Car Status</h2>";
    html += "<button class='btn-primary' onclick='getStatus()' style='width: 100%;'>\xF0\x9F\x94\x84 Refresh Status</button>";
    html += "<div class='status' id='status'>Click 'Refresh Status' to load car data...</div>";
    html += "</div>";

    html += "<div class='section'>";
    html += "<h2>\xF0\x9F\x94\xA7 Settings</h2>";
    html += "<button class='btn-warning' onclick='goToReconfigure()' style='width: 100%;'>\xF0\x9F\x94\xA7 Reconfigure Device</button>";
    html += "</div>";

    html += "<div id='message' class='message'></div>";

    html += "<script>";
    html += "function getStoredApiKey() {";
    html += "  return localStorage.getItem('vf3_api_key') || '';";
    html += "}";
    html += "function goToReconfigure() {";
    html += "  const apiKey = getStoredApiKey();";
    html += "  if (!apiKey || apiKey.length < 8) {";
    html += "    showMessage('Please enter your API Key first', true);";
    html += "    return;";
    html += "  }";
    html += "  window.location.href = '/configure?api_key=' + encodeURIComponent(apiKey);";
    html += "}";
    html += "function setApiKey() {";
    html += "  const key = document.getElementById('apiKeyInput').value.trim();";
    html += "  if (key.length < 8) {";
    html += "    showMessage('API Key must be at least 8 characters', true);";
    html += "    return;";
    html += "  }";
    html += "  localStorage.setItem('vf3_api_key', key);";
    html += "  updateAuthStatus();";
    html += "  showMessage('API Key saved successfully!', false);";
    html += "}";
    html += "function updateAuthStatus() {";
    html += "  const key = getStoredApiKey();";
    html += "  const statusEl = document.getElementById('authStatus');";
    html += "  if (key.length >= 8) {";
    html += "    statusEl.textContent = '\xE2\x9C\x85 Authenticated (Key: ' + key.substring(0, 4) + '****)';";
    html += "    statusEl.className = 'auth-status authenticated';";
    html += "  } else {";
    html += "    statusEl.textContent = '\xE2\x9D\x8C Not authenticated - Please enter your API Key';";
    html += "    statusEl.className = 'auth-status not-authenticated';";
    html += "  }";
    html += "}";
    html += "function showMessage(msg, isError) {";
    html += "  const el = document.getElementById('message');";
    html += "  el.textContent = msg;";
    html += "  el.className = 'message ' + (isError ? 'error' : 'success');";
    html += "  setTimeout(() => el.className = 'message', 3000);";
    html += "}";
    html += "function sendCommand(url, method, body) {";
    html += "  const apiKey = getStoredApiKey();";
    html += "  if (!apiKey || apiKey.length < 8) {";
    html += "    showMessage('Please enter your API Key first', true);";
    html += "    return;";
    html += "  }";
    html += "  const options = { method: method, headers: { 'X-API-Key': apiKey } };";
    html += "  if (body) {";
    html += "    options.headers['Content-Type'] = 'application/x-www-form-urlencoded';";
    html += "    options.body = body;";
    html += "  }";
    html += "  fetch(url, options)";
    html += "    .then(r => r.json())";
    html += "    .then(data => {";
    html += "      if (data.success) showMessage(data.message || 'Success!', false);";
    html += "      else showMessage(data.message || 'Failed', true);";
    html += "    })";
    html += "    .catch(e => showMessage('Error: ' + e.message, true));";
    html += "}";
    html += "function getStatus() {";
    html += "  fetch('/car/status')";
    html += "    .then(r => r.json())";
    html += "    .then(data => {";
    html += "      let html = '';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x94\x90 Locks:</strong> ' + (data.controls.car_lock ? '\xF0\x9F\x94\x92 Locked' : '\xF0\x9F\x94\x93 Unlocked') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xE2\x9A\xA1 Accessory Power:</strong> ' + (data.controls.accessory_power ? '\xE2\x9C\x85 ON' : '\xE2\x9D\x8C OFF') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\xAA\x9F Windows:</strong> ' + (data.window_close_active ? '\xE2\x8F\xB3 Closing (' + Math.round(data.window_close_remaining_ms/1000) + 's)' : '\xE2\x9C\x85 Idle') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x9A\xAA Doors:</strong> FL:' + (data.doors.front_left ? '\xF0\x9F\x94\xB4' : '\xF0\x9F\x9F\xA2') + ' FR:' + (data.doors.front_right ? '\xF0\x9F\x94\xB4' : '\xF0\x9F\x9F\xA2') + ' Trunk:' + (data.doors.trunk ? '\xF0\x9F\x94\xB4' : '\xF0\x9F\x9F\xA2') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x92\xA1 Lights:</strong> Demi:' + (data.lights.demi_light ? '\xF0\x9F\x92\xA1' : '\xE2\x9A\xAB') + ' Normal:' + (data.lights.normal_light ? '\xF0\x9F\x92\xA1' : '\xE2\x9A\xAB') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\xAA\x91 Seats:</strong> FL:' + (data.seats.front_left_occupied ? '\xF0\x9F\x91\xA4' : '\xE2\x9A\xAB') + ' FR:' + (data.seats.front_right_occupied ? '\xF0\x9F\x91\xA4' : '\xE2\x9A\xAB') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x94\x97 Seatbelts:</strong> FL:' + (data.seats.front_left_seatbelt ? '\xE2\x9C\x85' : '\xE2\x9A\xA0\xEF\xB8\x8F') + ' FR:' + (data.seats.front_right_seatbelt ? '\xE2\x9C\x85' : '\xE2\x9A\xA0\xEF\xB8\x8F') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x93\xA1 Proximity:</strong> L:' + (data.proximity.rear_left ? '\xF0\x9F\x9A\xA8' : '\xE2\x9C\x85') + ' R:' + (data.proximity.rear_right ? '\xF0\x9F\x9A\xA8' : '\xE2\x9C\x85') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x9A\x97 Speed:</strong> ' + data.sensors.vehicle_speed + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x8E\xAE Pedals:</strong> Accel:' + data.sensors.accelerator + ' Brake:' + data.sensors.brake + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>\xF0\x9F\x8E\x9B Steering:</strong> ' + data.sensors.steering_angle + '</div>';";
    html += "      document.getElementById('status').innerHTML = html;";
    html += "    })";
    html += "    .catch(e => showMessage('Error loading status: ' + e.message, true));";
    html += "}";
    html += "window.onload = function() {";
    html += "  const storedKey = getStoredApiKey();";
    html += "  if (storedKey) {";
    html += "    document.getElementById('apiKeyInput').value = storedKey;";
    html += "  }";
    html += "  updateAuthStatus();";
    html += "  getStatus();";
    html += "};";
    html += "</script>";
    html += "</body></html>";
    request->send(200, "text/html", html);
  });

  // Handle 404
  server.onNotFound([](AsyncWebServerRequest *request){
    request->send(404, "text/plain", "Not found");
  });

  server.begin();
  Serial.println("Web server started on port 80");
}
void setupOnboardingServer() {
  // Onboarding home page with configuration form
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request){
    String html = "<!DOCTYPE html><html><head>";
    html += "<meta charset='UTF-8'>";
    html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
    html += "<title>VF3 Smart - Setup</title>";
    html += "<style>";
    html += "body { font-family: Arial; max-width: 500px; margin: 50px auto; padding: 20px; }";
    html += "h1 { color: #e71e2c; }";
    html += "input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; }";
    html += "button { background: #e71e2c; color: white; padding: 15px; width: 100%; border: none; cursor: pointer; font-size: 16px; }";
    html += "button:hover { background: #c51a26; }";
    html += ".info { background: #f0f0f0; padding: 15px; margin: 20px 0; border-radius: 5px; }";
    html += ".warning { background: #fff3e0; color: #e65100; padding: 15px; margin: 20px 0; border-radius: 5px; }";
    html += "</style>";
    html += "</head><body>";
    html += "<h1>VF3 Smart Setup</h1>";

    // Show different message based on whether reconfiguring or initial setup
    if (is_configured) {
      html += "<div class='warning'>\xE2\x9A\xA0\xEF\xB8\x8F Reconfiguration Mode - Previous WiFi connection failed or you requested to reconfigure.</div>";
    } else {
      html += "<div class='info'>Welcome! Configure your VF3 Smart device with WiFi credentials and security settings.</div>";
    }

    html += "<form action='/configure' method='POST'>";
    html += "<h3>WiFi Configuration</h3>";
    html += "<label>WiFi SSID (Network Name)</label>";
    html += "<input type='text' name='ssid' placeholder='Enter WiFi SSID' value='" + configured_ssid + "' required>";
    html += "<label>WiFi Password</label>";
    html += "<input type='password' name='password' placeholder='Enter WiFi Password' value='" + configured_password + "' required>";
    html += "<label>API Key (for secure control)</label>";
    html += "<input type='text' name='api_key' placeholder='Enter API Key' value='" + configured_api_key + "' required>";
    html += "<div class='info'>API Key can be any string (minimum 8 characters). Save it securely - you'll need it to control your car via HTTP API and MQTT.</div>";
    html += "<button type='submit'>Save Configuration</button>";
    html += "</form>";
    html += "</body></html>";
    request->send(200, "text/html", html);
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

  server.begin();
  Serial.println("Onboarding web server started on port 80");
}
