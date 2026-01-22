#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>

// VinFast VF3 Electric Car - Input and Output Definitions
// =========================================================

// ===== WIFI CONFIGURATION =====
const char* ssid = "VF3_SMART";           // WiFi SSID
const char* password = "vf3smart123";     // WiFi Password

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
String getCarStatusJSON();

void setup() {
  // Initialize Serial Communication
  Serial.begin(9600);
  Serial.println("VinFast VF3 MCU Control System Initializing...");
  
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

  // Initialize WiFi
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid, password);
  Serial.println("WiFi AP Started");
  Serial.print("AP IP address: ");
  Serial.println(WiFi.softAPIP());

  // Setup web server
  setupWebServer();

  Serial.println("VinFast VF3 MCU System Ready!");
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
