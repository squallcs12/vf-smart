#ifndef OTA_H
#define OTA_H

#include <ArduinoOTA.h>
#include <WiFi.h>

// OTA Configuration
#define OTA_HOSTNAME "VF3-Smart"
#define OTA_PASSWORD "" // Empty password - use API key authentication on HTTP endpoints instead

// OTA status tracking
extern bool ota_in_progress;
extern int ota_progress_percent;
extern String ota_error;

// Initialize ArduinoOTA
void initOTA();

// Handle OTA updates in main loop
void handleOTA();

#endif // OTA_H
