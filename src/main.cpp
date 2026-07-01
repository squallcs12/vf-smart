#include <Arduino.h>
#include <WiFi.h>
#include <LittleFS.h>
#include "config.h"
#include "pins.h"
#include "sensors.h"
#include "controls.h"
#include "time_sync.h"
#include "storage.h"
#include "webserver.h"
#include "websocket.h"
#include "discovery.h"
#include "ota.h"
#include "tpms.h"

// Bring up SoftAP + the onboarding web server. `banner` is the headline shown in
// the serial log explaining why we're in onboarding (initial setup vs. reconfig).
static void startOnboardingMode(const char* banner) {
  Serial.println("===========================================");
  Serial.println(banner);
  Serial.println("===========================================");

  WiFi.mode(WIFI_AP);
  WiFi.softAP(onboarding_ssid, onboarding_password);

  Serial.print("Connect to WiFi: ");
  Serial.println(onboarding_ssid);
  Serial.print("Password: ");
  Serial.println(onboarding_password);
  Serial.print("Then visit: http://");
  Serial.println(WiFi.softAPIP());
  Serial.println("===========================================");

  setupOnboardingServer();
}

void setup() {
  // Initialize Serial Communication
  Serial.begin(9600);
  Serial.println("VinFast VF3 MCU Control System Initializing...");

  // Initialize LittleFS for HTML file storage
  if (!LittleFS.begin(true)) {
    Serial.println("LittleFS Mount Failed - HTML files unavailable");
  } else {
    Serial.println("LittleFS Mounted Successfully");
  }

  // Load configuration from flash
  loadConfiguration();

  // Initialize all pins
  initializePins();

  // Initialize TPMS RF receiver
  initTpms();

  // Initialize WiFi based on configuration status
  if (is_configured) {
    // Try to connect to configured WiFi as Station
    WiFi.mode(WIFI_STA);
    WiFi.begin(configured_ssid.c_str(), configured_password.c_str());

    Serial.println("Connecting to WiFi...");
    Serial.print("SSID: ");
    Serial.println(configured_ssid);

    // Wait up to 3 minutes for connection
    unsigned long start_time = millis();
    unsigned long timeout = 60000; // 1 minutes

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

      // Sync time with NTP server
      syncTime();

      // Initialize UDP discovery broadcasting
      initDiscovery();

      // Initialize OTA update service
      initOTA();

      Serial.println("VinFast VF3 MCU System Ready!");
    } else {
      // Connection failed - start AP mode for reconfiguration
      Serial.println("WiFi Connection Failed!");
      startOnboardingMode("RECONFIGURATION MODE - WiFi connection failed");
    }
  } else {
    // Not configured - start AP mode for initial onboarding
    startOnboardingMode("ONBOARDING MODE - Device not configured");
  }
}

void loop() {
  // Stream car status to subscribed WebSocket clients (full + delta, heartbeat)
  handleWebSocket();

  // Handle OTA updates
  handleOTA();

  // Broadcast UDP discovery message
  handleDiscoveryBroadcast();

  // Decode TPMS RF packets
  handleTpms();

  // Read all sensors
  readSensors();

  // Execute control logic
  handleFactoryResetButton();  // Check hardware factory reset button (hold 10s)
  handleCarState();
  handleWindowControl();
  handleAccessoryPower();
  handleLightReminder();
  handleChargingControl();
  handleRemoteFrunkControl();  // Open front trunk when remote unlock held for 1s
  handleRemoteMirrorControl();  // Control mirrors via remote button double-press

  // 50ms control loop cycle
  delay(50);
}
