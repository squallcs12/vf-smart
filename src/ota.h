#ifndef OTA_H
#define OTA_H

#include <ArduinoOTA.h>
#include <WiFi.h>

// OTA Configuration
#define OTA_HOSTNAME "VF3-Smart"
#define OTA_PASSWORD "" // Empty password - use API key authentication on HTTP endpoints instead

// Abort an HTTP OTA upload that stalls this long between chunks (ms). Guards
// against a dropped connection wedging ota_in_progress until the next reboot.
#define OTA_HTTP_TIMEOUT_MS 30000

// OTA status tracking
extern bool ota_in_progress;
extern int ota_progress_percent;
extern String ota_error;
extern unsigned long ota_last_activity; // millis() of last HTTP OTA chunk (0 = idle)

// Initialize ArduinoOTA
void initOTA();

// Handle OTA updates in main loop
void handleOTA();

#endif // OTA_H
