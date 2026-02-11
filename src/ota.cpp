#include "ota.h"
#include <LittleFS.h>

// OTA status tracking
bool ota_in_progress = false;
int ota_progress_percent = 0;
String ota_error = "";

void initOTA() {
  // Configure OTA hostname
  ArduinoOTA.setHostname(OTA_HOSTNAME);

  // Set OTA password if defined (empty by default - use API key auth on HTTP endpoints)
  if (strlen(OTA_PASSWORD) > 0) {
    ArduinoOTA.setPassword(OTA_PASSWORD);
  }

  // OTA callbacks for status tracking
  ArduinoOTA.onStart([]() {
    String type;
    if (ArduinoOTA.getCommand() == U_FLASH) {
      type = "firmware";
    } else { // U_SPIFFS
      type = "filesystem";
    }

    ota_in_progress = true;
    ota_progress_percent = 0;
    ota_error = "";

    Serial.println("OTA Update Started: " + type);

    // Unmount filesystem if updating filesystem
    if (ArduinoOTA.getCommand() != U_FLASH) {
      LittleFS.end();
    }
  });

  ArduinoOTA.onEnd([]() {
    ota_in_progress = false;
    ota_progress_percent = 100;
    Serial.println("\nOTA Update Complete!");
  });

  ArduinoOTA.onProgress([](unsigned int progress, unsigned int total) {
    ota_progress_percent = (progress / (total / 100));
    Serial.printf("OTA Progress: %u%%\r", ota_progress_percent);
  });

  ArduinoOTA.onError([](ota_error_t error) {
    ota_in_progress = false;

    Serial.printf("OTA Error[%u]: ", error);

    switch (error) {
      case OTA_AUTH_ERROR:
        ota_error = "Authentication Failed";
        Serial.println("Auth Failed");
        break;
      case OTA_BEGIN_ERROR:
        ota_error = "Begin Failed";
        Serial.println("Begin Failed");
        break;
      case OTA_CONNECT_ERROR:
        ota_error = "Connect Failed";
        Serial.println("Connect Failed");
        break;
      case OTA_RECEIVE_ERROR:
        ota_error = "Receive Failed";
        Serial.println("Receive Failed");
        break;
      case OTA_END_ERROR:
        ota_error = "End Failed";
        Serial.println("End Failed");
        break;
      default:
        ota_error = "Unknown Error";
        Serial.println("Unknown Error");
        break;
    }
  });

  // Start OTA service
  ArduinoOTA.begin();
  Serial.println("OTA Update Service Started");
  Serial.print("OTA Hostname: ");
  Serial.println(OTA_HOSTNAME);
}

void handleOTA() {
  ArduinoOTA.handle();
}
