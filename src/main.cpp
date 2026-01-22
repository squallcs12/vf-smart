#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// VinFast VF3 Electric Car - Input and Output Definitions
// =========================================================

// ===== CONFIGURATION STORAGE =====
Preferences preferences;
String configured_ssid = "";
String configured_password = "";
String configured_api_key = "";
bool is_configured = false;

// ===== DEFAULT ONBOARDING AP =====
const char* onboarding_ssid = "VF3-SETUP";
const char* onboarding_password = "setup123";

// ===== WEB SERVER =====
AsyncWebServer server(80);

// ===== ANALOG INPUTS (Sensors) - Use input-only pins =====
#define VF3_ACCELERATOR_PEDAL 34       // GPIO 34 - Accelerator pedal position (input only)
#define VF3_BRAKE_PEDAL 39             // GPIO 39 - Brake pedal sensor (input only)
#define VF3_STEERING_ANGLE 36          // GPIO 36 - Steering angle sensor (input only)

// ===== DIGITAL INPUTS (Sensors & Switches) =====
#define VF3_SPEED_SENSOR 35            // GPIO 35 - Vehicle speed sensor (input only)
#define VF3_DOOR_FL 4                  // GPIO 4 - Front left door open/close sensor
#define VF3_DOOR_FR 16                 // GPIO 16 - Front right door open/close sensor
#define VF3_DOOR_TRUNK 17              // GPIO 17 - Trunk/tailgate open/close sensor
#define VF3_BRAKE_SWITCH 2             // GPIO 2 - Brake pressed switch
#define VF3_SEAT_FL 15                 // GPIO 15 - Front left seat occupancy sensor
#define VF3_SEAT_FR 12                 // GPIO 12 - Front right seat occupancy sensor
#define VF3_SEATBELT_FL 14             // GPIO 14 - Front left seatbelt sensor
#define VF3_SEATBELT_FR 27             // GPIO 27 - Front right seatbelt sensor
#define VF3_DEMI_LIGHT 26              // GPIO 26 - Demi/low beam light (0=off, 1=on)
#define VF3_NORMAL_LIGHT 25            // GPIO 25 - Normal/high beam light (0=off, 1=on)
#define VF3_PROXIMITY_REAR_L 33        // GPIO 33 - Rear left proximity/parking detection
#define VF3_PROXIMITY_REAR_R 32        // GPIO 32 - Rear right proximity/parking detection

// ===== DIGITAL OUTPUTS (Controls & Indicators) =====
// AVOID: GPIO 0 (boot), 1 (TX), 3 (RX), 6-11 (flash)
#define VF3_CAR_LOCK 5                 // GPIO 5 - Car lock control
#define VF3_CAR_UNLOCK 18              // GPIO 18 - Car unlock control
#define VF3_TURN_SIGNAL_L 19           // GPIO 19 - Left turn signal
#define VF3_TURN_SIGNAL_R 21           // GPIO 21 - Right turn signal
#define VF3_BUZZER 22                  // GPIO 22 - Warning buzzer/alarm
#define VF3_WINDOW_LEFT 23             // GPIO 23 - Front left window control
#define VF3_WINDOW_RIGHT 13            // GPIO 13 - Front right window control
#define VF3_ACCESSORY_POWER 0          // GPIO 0 - Accessory power control (boot pin - ensure LOW at boot)
#define VF3_BRAKE_SIGNAL 1             // GPIO 1 - Brake signal/brake light (TX pin - use carefully)

// ===== INPUT VARIABLES =====
int vf3_accelerator = 0;              // Accelerator pedal (0-100%)
int vf3_brake = 0;                    // Brake pedal (0-100%)
int vf3_steering_angle = 0;           // Steering angle (-180 to +180°)
int vf3_vehicle_speed = 0;            // Vehicle speed (km/h)
int vf3_door_fl = LOW;                // Front left door (0=closed, 1=open)
int vf3_door_fr = LOW;                // Front right door (0=closed, 1=open)
int vf3_door_trunk = LOW;             // Trunk/tailgate (0=closed, 1=open)
int vf3_seat_fl = LOW;                // Front left seat (0=empty, 1=occupied)
int vf3_seat_fr = LOW;                // Front right seat (0=empty, 1=occupied)
int vf3_seatbelt_fl = LOW;            // Front left seatbelt (0=unbuckled, 1=buckled)
int vf3_seatbelt_fr = LOW;            // Front right seatbelt (0=unbuckled, 1=buckled)
int vf3_brake_pressed = LOW;          // 0=not pressed, 1=pressed
int vf3_proximity_rear_l = LOW;       // Rear left proximity sensor (0=clear, 1=detected)
int vf3_proximity_rear_r = LOW;       // Rear right proximity sensor (0=clear, 1=detected)
int vf3_demi_light = LOW;             // Demi/low beam light (0=off, 1=on)
int vf3_normal_light = LOW;           // Normal/high beam light (0=off, 1=on)

// ===== OUTPUT VARIABLES =====
int vf3_car_lock = LOW;               // Car lock control
int vf3_car_unlock = LOW;             // Car unlock control
int vf3_accessory_power = HIGH;       // Accessory power control (default ON)
unsigned long window_close_timer = 0; // Timer for auto-close windows feature
#define WINDOW_CLOSE_DURATION 30000    // Window close duration in milliseconds (30 seconds)
#define VF3_DOOR_LOCK 13               // GPIO 13 - Door lock/unlock relay
int vf3_door_locked = LOW;            // 0=unlocked, 1=locked

// ===== FUNCTION DECLARATIONS =====
void handleAccessoryPower();
void handleWindowControl();
void setupWebServer();
void setupOnboardingServer();
String getCarStatusJSON();
bool authenticateRequest(AsyncWebServerRequest *request);
void loadConfiguration();
void saveConfiguration(String ssid, String password, String api_key);

void setup() {
  // Initialize Serial Communication
  Serial.begin(9600);
  Serial.println("VinFast VF3 MCU Control System Initializing...");

  // Load configuration from flash
  loadConfiguration();

  // Initialize Digital Input Pins
  pinMode(VF3_SPEED_SENSOR, INPUT);
  pinMode(VF3_DOOR_FL, INPUT);
  pinMode(VF3_DOOR_FR, INPUT);
  pinMode(VF3_DOOR_TRUNK, INPUT);
  pinMode(VF3_BRAKE_SWITCH, INPUT);
  pinMode(VF3_SEAT_FL, INPUT);
  pinMode(VF3_SEAT_FR, INPUT);
  pinMode(VF3_SEATBELT_FL, INPUT);
  pinMode(VF3_SEATBELT_FR, INPUT);
  pinMode(VF3_DEMI_LIGHT, INPUT);
  pinMode(VF3_NORMAL_LIGHT, INPUT);
  pinMode(VF3_PROXIMITY_REAR_L, INPUT);
  pinMode(VF3_PROXIMITY_REAR_R, INPUT);
  
  // Initialize Digital Output Pins (Control Systems)
  pinMode(VF3_CAR_LOCK, OUTPUT);
  pinMode(VF3_CAR_UNLOCK, OUTPUT);
  pinMode(VF3_DOOR_LOCK, OUTPUT);
  pinMode(VF3_TURN_SIGNAL_L, OUTPUT);
  pinMode(VF3_TURN_SIGNAL_R, OUTPUT);
  pinMode(VF3_BUZZER, OUTPUT);
  pinMode(VF3_WINDOW_LEFT, OUTPUT);
  pinMode(VF3_WINDOW_RIGHT, OUTPUT);
  pinMode(VF3_ACCESSORY_POWER, OUTPUT);
  
  // Turn on accessory power on startup
  digitalWrite(VF3_ACCESSORY_POWER, HIGH);

  // Initialize WiFi based on configuration status
  if (is_configured) {
    // Try to connect to configured WiFi as Station
    WiFi.mode(WIFI_STA);
    WiFi.begin(configured_ssid.c_str(), configured_password.c_str());

    Serial.println("Connecting to WiFi...");
    Serial.print("SSID: ");
    Serial.println(configured_ssid);

    // Wait up to 3 minutes (180 seconds) for connection
    unsigned long start_time = millis();
    unsigned long timeout = 180000; // 3 minutes

    while (WiFi.status() != WL_CONNECTED && (millis() - start_time) < timeout) {
      delay(500);
      Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
      // Successfully connected to WiFi
      Serial.println("WiFi Connected!");
      Serial.print("IP Address: ");
      Serial.println(WiFi.localIP());

      // Setup normal web server
      setupWebServer();
      Serial.println("VinFast VF3 MCU System Ready!");
    } else {
      // Connection failed - start AP mode for reconfiguration
      Serial.println("WiFi Connection Failed!");
      Serial.println("Starting AP mode for reconfiguration...");

      WiFi.mode(WIFI_AP);
      WiFi.softAP(onboarding_ssid, onboarding_password);

      Serial.println("===========================================");
      Serial.println("RECONFIGURATION MODE - WiFi connection failed");
      Serial.println("===========================================");
      Serial.print("Connect to WiFi: ");
      Serial.println(onboarding_ssid);
      Serial.print("Password: ");
      Serial.println(onboarding_password);
      Serial.print("Then visit: http://");
      Serial.println(WiFi.softAPIP());
      Serial.println("===========================================");

      // Setup onboarding web server
      setupOnboardingServer();
    }
  } else {
    // Not configured - start AP mode for initial onboarding
    WiFi.mode(WIFI_AP);
    WiFi.softAP(onboarding_ssid, onboarding_password);

    Serial.println("===========================================");
    Serial.println("ONBOARDING MODE - Device not configured");
    Serial.println("===========================================");
    Serial.print("Connect to WiFi: ");
    Serial.println(onboarding_ssid);
    Serial.print("Password: ");
    Serial.println(onboarding_password);
    Serial.print("Then visit: http://");
    Serial.println(WiFi.softAPIP());
    Serial.println("===========================================");

    // Setup onboarding web server
    setupOnboardingServer();
  }
}

void loop() {
  // ===== READ ANALOG SENSORS =====
  vf3_accelerator = analogRead(VF3_ACCELERATOR_PEDAL);
  vf3_brake = analogRead(VF3_BRAKE_PEDAL);
  vf3_steering_angle = analogRead(VF3_STEERING_ANGLE);
  
  // ===== READ DIGITAL SENSORS =====
  vf3_vehicle_speed = digitalRead(VF3_SPEED_SENSOR);
  vf3_door_fl = digitalRead(VF3_DOOR_FL);
  vf3_door_fr = digitalRead(VF3_DOOR_FR);
  vf3_door_trunk = digitalRead(VF3_DOOR_TRUNK);
  vf3_seat_fl = digitalRead(VF3_SEAT_FL);
  vf3_seat_fr = digitalRead(VF3_SEAT_FR);
  vf3_seatbelt_fl = digitalRead(VF3_SEATBELT_FL);
  vf3_seatbelt_fr = digitalRead(VF3_SEATBELT_FR);
  vf3_brake_pressed = digitalRead(VF3_BRAKE_SWITCH);
  vf3_demi_light = digitalRead(VF3_DEMI_LIGHT);
  vf3_normal_light = digitalRead(VF3_NORMAL_LIGHT);
  vf3_proximity_rear_l = digitalRead(VF3_PROXIMITY_REAR_L);
  vf3_proximity_rear_r = digitalRead(VF3_PROXIMITY_REAR_R);

  // ===== CONTROL LOGIC =====

  // Handle window control
  handleWindowControl();

  // Handle accessory power
  handleAccessoryPower();
  
  delay(50);  // 50ms control loop cycle
}

// ===== FUNCTION DEFINITIONS =====
void handleWindowControl() {
  // Auto close windows when car is locked (on for 30s, then off)
  if (vf3_car_lock == HIGH) {
    // Lock signal detected, start/reset timer
    window_close_timer = millis();
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
  } else if (window_close_timer != 0 && millis() - window_close_timer < WINDOW_CLOSE_DURATION) {
    // Keep windows closing for 30 seconds
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
  } else {
    // Timer expired or lock not active
    digitalWrite(VF3_WINDOW_LEFT, LOW);
    digitalWrite(VF3_WINDOW_RIGHT, LOW);
    window_close_timer = 0;  // Reset timer after windows finish closing
  }
}

void handleAccessoryPower() {
  // Turn off accessory power when car is locked
  if (vf3_car_lock == HIGH) {
    digitalWrite(VF3_ACCESSORY_POWER, LOW);
    vf3_accessory_power = LOW;
  }

  // Turn on accessory power when car is unlocked
  if (vf3_car_unlock == HIGH) {
    digitalWrite(VF3_ACCESSORY_POWER, HIGH);
    vf3_accessory_power = HIGH;
  }
}

void loadConfiguration() {
  preferences.begin("vf3-config", false);

  is_configured = preferences.getBool("configured", false);

  if (is_configured) {
    configured_ssid = preferences.getString("ssid", "");
    configured_password = preferences.getString("password", "");
    configured_api_key = preferences.getString("api_key", "");

    Serial.println("Configuration loaded from flash");
    Serial.print("SSID: ");
    Serial.println(configured_ssid);
  } else {
    Serial.println("Device not configured - entering onboarding mode");
  }

  preferences.end();
}

void saveConfiguration(String ssid, String password, String api_key) {
  preferences.begin("vf3-config", false);

  preferences.putString("ssid", ssid);
  preferences.putString("password", password);
  preferences.putString("api_key", api_key);
  preferences.putBool("configured", true);

  preferences.end();

  Serial.println("Configuration saved to flash");
}

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

String getCarStatusJSON() {
  JsonDocument doc;

  // Analog sensor values
  JsonObject sensors = doc["sensors"].to<JsonObject>();
  sensors["accelerator"] = vf3_accelerator;
  sensors["brake"] = vf3_brake;
  sensors["steering_angle"] = vf3_steering_angle;
  sensors["vehicle_speed"] = vf3_vehicle_speed;

  // Door status
  JsonObject doors = doc["doors"].to<JsonObject>();
  doors["front_left"] = vf3_door_fl;
  doors["front_right"] = vf3_door_fr;
  doors["trunk"] = vf3_door_trunk;
  doors["locked"] = vf3_door_locked;

  // Seat and seatbelt status
  JsonObject seats = doc["seats"].to<JsonObject>();
  seats["front_left_occupied"] = vf3_seat_fl;
  seats["front_right_occupied"] = vf3_seat_fr;
  seats["front_left_seatbelt"] = vf3_seatbelt_fl;
  seats["front_right_seatbelt"] = vf3_seatbelt_fr;

  // Lights status
  JsonObject lights = doc["lights"].to<JsonObject>();
  lights["demi_light"] = vf3_demi_light;
  lights["normal_light"] = vf3_normal_light;

  // Proximity sensors
  JsonObject proximity = doc["proximity"].to<JsonObject>();
  proximity["rear_left"] = vf3_proximity_rear_l;
  proximity["rear_right"] = vf3_proximity_rear_r;

  // Controls status
  JsonObject controls = doc["controls"].to<JsonObject>();
  controls["brake_pressed"] = vf3_brake_pressed;
  controls["accessory_power"] = vf3_accessory_power;
  controls["car_lock"] = vf3_car_lock;
  controls["car_unlock"] = vf3_car_unlock;

  // Window timer status
  doc["window_close_active"] = (window_close_timer != 0);
  if (window_close_timer != 0) {
    doc["window_close_remaining_ms"] = WINDOW_CLOSE_DURATION - (millis() - window_close_timer);
  } else {
    doc["window_close_remaining_ms"] = 0;
  }

  String output;
  serializeJson(doc, output);
  return output;
}

void setupWebServer() {
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
    html += "</style>";
    html += "</head><body>";
    html += "<h1>VF3 Smart Control</h1>";

    html += "<div class='section'>";
    html += "<h2>Lock / Unlock</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-primary' onclick='sendCommand(\"/car/lock\", \"POST\")'>Lock Car</button>";
    html += "<button class='btn-secondary' onclick='sendCommand(\"/car/unlock\", \"POST\")'>Unlock Car</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>Accessory Power</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-secondary' onclick='sendCommand(\"/car/accessory-power\", \"POST\", \"state=on\")'>Power ON</button>";
    html += "<button class='btn-danger' onclick='sendCommand(\"/car/accessory-power\", \"POST\", \"state=off\")'>Power OFF</button>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/accessory-power\", \"POST\", \"state=toggle\")'>Toggle</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>Windows</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-primary' onclick='sendCommand(\"/car/windows/close\", \"POST\")'>Close Windows (30s)</button>";
    html += "<button class='btn-danger' onclick='sendCommand(\"/car/windows/stop\", \"POST\")'>Stop</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>Buzzer</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/buzzer\", \"POST\", \"state=beep&duration=500\")'>Beep (0.5s)</button>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/buzzer\", \"POST\", \"state=beep&duration=1000\")'>Beep (1s)</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>Turn Signals</h2>";
    html += "<div class='button-group'>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/turn-signal\", \"POST\", \"side=left&state=on\")'>Left ON</button>";
    html += "<button class='btn-warning' onclick='sendCommand(\"/car/turn-signal\", \"POST\", \"side=right&state=on\")'>Right ON</button>";
    html += "<button class='btn-danger' onclick='sendCommand(\"/car/turn-signal\", \"POST\", \"side=both&state=off\")'>All OFF</button>";
    html += "</div></div>";

    html += "<div class='section'>";
    html += "<h2>Car Status</h2>";
    html += "<button class='btn-primary' onclick='getStatus()' style='width: 100%;'>Refresh Status</button>";
    html += "<div class='status' id='status'>Click 'Refresh Status' to load car data...</div>";
    html += "</div>";

    html += "<div id='message' class='message'></div>";

    html += "<script>";
    html += "const API_KEY = '" + configured_api_key + "';";
    html += "function showMessage(msg, isError) {";
    html += "  const el = document.getElementById('message');";
    html += "  el.textContent = msg;";
    html += "  el.className = 'message ' + (isError ? 'error' : 'success');";
    html += "  setTimeout(() => el.className = 'message', 3000);";
    html += "}";
    html += "function sendCommand(url, method, body) {";
    html += "  const options = { method: method, headers: { 'X-API-Key': API_KEY } };";
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
    html += "      html += '<div class=\"status-item\"><strong>Locks:</strong> ' + (data.controls.car_lock ? 'LOCKED' : 'UNLOCKED') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Accessory Power:</strong> ' + (data.controls.accessory_power ? 'ON' : 'OFF') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Windows:</strong> ' + (data.window_close_active ? 'Closing (' + Math.round(data.window_close_remaining_ms/1000) + 's)' : 'Idle') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Doors:</strong> FL:' + (data.doors.front_left ? 'OPEN' : 'CLOSED') + ' FR:' + (data.doors.front_right ? 'OPEN' : 'CLOSED') + ' Trunk:' + (data.doors.trunk ? 'OPEN' : 'CLOSED') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Lights:</strong> Demi:' + (data.lights.demi_light ? 'ON' : 'OFF') + ' Normal:' + (data.lights.normal_light ? 'ON' : 'OFF') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Seats:</strong> FL:' + (data.seats.front_left_occupied ? 'OCCUPIED' : 'EMPTY') + ' FR:' + (data.seats.front_right_occupied ? 'OCCUPIED' : 'EMPTY') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Seatbelts:</strong> FL:' + (data.seats.front_left_seatbelt ? 'FASTENED' : 'UNFASTENED') + ' FR:' + (data.seats.front_right_seatbelt ? 'FASTENED' : 'UNFASTENED') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Proximity:</strong> L:' + (data.proximity.rear_left ? 'DETECTED' : 'CLEAR') + ' R:' + (data.proximity.rear_right ? 'DETECTED' : 'CLEAR') + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Speed:</strong> ' + data.sensors.vehicle_speed + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Pedals:</strong> Accel:' + data.sensors.accelerator + ' Brake:' + data.sensors.brake + '</div>';";
    html += "      html += '<div class=\"status-item\"><strong>Steering:</strong> ' + data.sensors.steering_angle + '</div>';";
    html += "      document.getElementById('status').innerHTML = html;";
    html += "    })";
    html += "    .catch(e => showMessage('Error loading status: ' + e.message, true));";
    html += "}";
    html += "window.onload = getStatus;";
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
      html += "<div class='warning'>WARNING: Reconfiguration Mode - Previous WiFi connection failed or you requested to reconfigure.</div>";
    } else {
      html += "<div class='info'>Welcome! Configure your VF3 Smart device with WiFi credentials and security settings.</div>";
    }

    html += "<form action='/configure' method='POST'>";
    html += "<label>WiFi SSID (Network Name)</label>";
    html += "<input type='text' name='ssid' placeholder='Enter WiFi SSID' value='" + configured_ssid + "' required>";
    html += "<label>WiFi Password</label>";
    html += "<input type='password' name='password' placeholder='Enter WiFi Password' value='" + configured_password + "' required>";
    html += "<label>API Key (for secure control)</label>";
    html += "<input type='text' name='api_key' placeholder='Enter API Key' value='" + configured_api_key + "' required>";
    html += "<div class='info'>API Key can be any string (minimum 8 characters). Save it securely - you'll need it to control your car.</div>";
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
