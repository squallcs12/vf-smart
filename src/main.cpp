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

// ===== ANALOG INPUTS (Sensors) - ADC1 pins =====
#define VF3_MOTOR_TEMP 35              // GPIO 35 - Motor temperature sensor
#define VF3_ACCELERATOR_PEDAL 33       // GPIO 33 - Accelerator pedal position
#define VF3_BRAKE_PEDAL 25             // GPIO 25 - Brake pedal sensor
#define VF3_STEERING_ANGLE 26          // GPIO 26 - Steering angle sensor

// ===== DIGITAL INPUTS (Sensors & Switches) =====
#define VF3_SPEED_SENSOR 4             // GPIO 4 - Vehicle speed sensor
#define VF3_DOOR_FL 14                 // GPIO 14 - Front left door open/close sensor
#define VF3_DOOR_FR 15                 // GPIO 15 - Front right door open/close sensor
#define VF3_DOOR_TRUNK 24              // GPIO 24 - Trunk/tailgate open/close sensor
#define VF3_BRAKE_SWITCH 16            // GPIO 16 - Brake pressed switch
#define VF3_SEAT_FL 27                 // GPIO 27 - Front left seat occupancy sensor
#define VF3_SEAT_FR 28                 // GPIO 28 - Front right seat occupancy sensor
#define VF3_SEATBELT_FL 30             // GPIO 30 - Front left seatbelt sensor
#define VF3_SEATBELT_FR 31             // GPIO 31 - Front right seatbelt sensor
#define VF3_DEMI_LIGHT 18              // GPIO 18 - Demi/low beam light (0=off, 1=on)
#define VF3_NORMAL_LIGHT 19            // GPIO 19 - Normal/high beam light (0=off, 1=on)
#define VF3_PROXIMITY_REAR_L 20        // GPIO 20 - Rear left proximity/parking detection
#define VF3_PROXIMITY_REAR_R 21        // GPIO 21 - Rear right proximity/parking detection
#define VF3_ACCESSORY_POWER_BUTTON 22  // GPIO 22 - Accessory power button (manual toggle)

// ===== DIGITAL OUTPUTS (Controls & Indicators) =====
#define VF3_CAR_LOCK 5                 // GPIO 5 - Car lock control
#define VF3_CAR_UNLOCK 6               // GPIO 6 - Car unlock control
#define VF3_TURN_SIGNAL_L 2            // GPIO 2 - Left turn signal
#define VF3_TURN_SIGNAL_R 0            // GPIO 0 - Right turn signal
#define VF3_BUZZER 8                   // GPIO 8 - Warning buzzer/alarm
#define VF3_WINDOW_LEFT 10             // GPIO 10 - Front left window control
#define VF3_WINDOW_RIGHT 11            // GPIO 11 - Front right window control
#define VF3_ACCESSORY_POWER 7          // GPIO 7 - Accessory power control
#define VF3_BRAKE_SIGNAL 12            // GPIO 12 - Brake signal/brake light

// ===== INPUT VARIABLES =====
int vf3_motor_temp = 0;               // Motor temperature (°C)
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
int vf3_accessory_power_button = LOW; // Accessory power button state (0=not pressed, 1=pressed)
int vf3_accessory_power_button_last = LOW; // Last button state for edge detection

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
  pinMode(VF3_ACCESSORY_POWER_BUTTON, INPUT);
  
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
  WiFi.mode(WIFI_AP);

  if (is_configured) {
    // Use configured credentials
    WiFi.softAP(configured_ssid.c_str(), configured_password.c_str());
    Serial.println("WiFi AP Started with configured credentials");
    Serial.print("SSID: ");
    Serial.println(configured_ssid);
    Serial.print("AP IP address: ");
    Serial.println(WiFi.softAPIP());

    // Setup normal web server
    setupWebServer();
    Serial.println("VinFast VF3 MCU System Ready!");
  } else {
    // Use onboarding AP
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
  vf3_motor_temp = analogRead(VF3_MOTOR_TEMP);
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
  vf3_accessory_power_button = digitalRead(VF3_ACCESSORY_POWER_BUTTON);
  
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
  // Manual button toggle (on button press - rising edge detection)
  if (vf3_accessory_power_button == HIGH && vf3_accessory_power_button_last == LOW) {
    // Toggle accessory power
    vf3_accessory_power = !vf3_accessory_power;
    digitalWrite(VF3_ACCESSORY_POWER, vf3_accessory_power);
  }
  vf3_accessory_power_button_last = vf3_accessory_power_button;

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
  sensors["motor_temp"] = vf3_motor_temp;
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

  // Root endpoint - Simple info page
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request){
    String html = "<!DOCTYPE html><html><head><title>VF3 Smart</title></head><body>";
    html += "<h1>VinFast VF3 Smart Control System</h1>";
    html += "<h2>API Endpoints</h2>";
    html += "<h3>Status</h3>";
    html += "<ul><li>GET <a href='/car/status'>/car/status</a> - Get car status</li></ul>";
    html += "<h3>Control</h3>";
    html += "<ul>";
    html += "<li>POST /car/lock - Lock the car</li>";
    html += "<li>POST /car/unlock - Unlock the car</li>";
    html += "<li>POST /car/accessory-power - Control accessory power (state=on/off/toggle)</li>";
    html += "<li>POST /car/windows/close - Close windows</li>";
    html += "<li>POST /car/windows/stop - Stop windows</li>";
    html += "<li>POST /car/buzzer - Control buzzer (state=on/off/beep, duration=ms)</li>";
    html += "<li>POST /car/turn-signal - Control turn signals (side=left/right/both, state=on/off)</li>";
    html += "</ul>";
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
    html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
    html += "<title>VF3 Smart - Setup</title>";
    html += "<style>";
    html += "body { font-family: Arial; max-width: 500px; margin: 50px auto; padding: 20px; }";
    html += "h1 { color: #e71e2c; }";
    html += "input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; }";
    html += "button { background: #e71e2c; color: white; padding: 15px; width: 100%; border: none; cursor: pointer; font-size: 16px; }";
    html += "button:hover { background: #c51a26; }";
    html += ".info { background: #f0f0f0; padding: 15px; margin: 20px 0; border-radius: 5px; }";
    html += "</style>";
    html += "</head><body>";
    html += "<h1>VF3 Smart Setup</h1>";
    html += "<div class='info'>Welcome! Configure your VF3 Smart device with WiFi credentials and security settings.</div>";
    html += "<form action='/configure' method='POST'>";
    html += "<label>WiFi SSID (Network Name)</label>";
    html += "<input type='text' name='ssid' placeholder='Enter WiFi SSID' required>";
    html += "<label>WiFi Password</label>";
    html += "<input type='password' name='password' placeholder='Enter WiFi Password' required>";
    html += "<label>API Key (for secure control)</label>";
    html += "<input type='text' name='api_key' placeholder='Enter API Key' required>";
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
