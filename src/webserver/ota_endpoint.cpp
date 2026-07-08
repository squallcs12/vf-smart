#include "ota_endpoint.h"
#include "../ota.h"
#include "../webserver.h"
#include <Update.h>
#include <ArduinoJson.h>

void registerOTAEndpoint(AsyncWebServer& server) {
  // GET /ota/status - Get OTA update status
  server.on("/ota/status", HTTP_GET, [](AsyncWebServerRequest *request){
    JsonDocument doc;
    doc["ota_enabled"] = true;
    doc["in_progress"] = ota_in_progress;
    doc["progress_percent"] = ota_progress_percent;
    doc["error"] = ota_error;

    String response;
    serializeJson(doc, response);
    request->send(200, "application/json", response);
  });

  // POST /ota/update - Upload firmware via HTTP
  server.on("/ota/update", HTTP_POST,
    // Handler called after upload completes
    [](AsyncWebServerRequest *request) {
      // Check authentication
      if (!authenticateRequest(request)) {
        sendUnauthorized(request);
        return;
      }

      bool success = !Update.hasError();

      JsonDocument doc;
      doc["success"] = success;

      if (success) {
        doc["message"] = "Firmware update successful - Rebooting in 3 seconds";
        ota_progress_percent = 100;
      } else {
        doc["message"] = "Firmware update failed";
        doc["error"] = Update.errorString();
        ota_error = Update.errorString();
      }

      String response;
      serializeJson(doc, response);
      request->send(success ? 200 : 500, "application/json", response);

      if (success) {
        // Restart ESP32 after successful update
        delay(3000);
        ESP.restart();
      }
    },
    // Upload handler called for each chunk of data
    [](AsyncWebServerRequest *request, String filename, size_t index, uint8_t *data, size_t len, bool final) {
      // First chunk: authenticate, then open the update session.
      if (index == 0) {
        if (!authenticateRequest(request)) {
          // Reject unauthenticated uploads outright - drop the connection so we
          // don't keep parsing (and feeding) the rest of the body.
          request->client()->close();
          return;
        }

        // Refuse to start on top of an in-flight session (e.g. a previous
        // upload that hasn't timed out yet) - Update supports only one at a time.
        if (ota_in_progress) {
          Serial.println("OTA rejected: an update is already in progress");
          request->client()->close();
          return;
        }

        Serial.printf("OTA Update Started: %s\n", filename.c_str());
        ota_in_progress = true;
        ota_progress_percent = 0;
        ota_error = "";
        ota_last_activity = millis();

        // Determine update type based on filename
        int updateType = U_FLASH; // Default to firmware
        if (filename.endsWith(".littlefs.bin") || filename.endsWith(".spiffs.bin")) {
          updateType = U_SPIFFS;
        }

        // Start update process
        if (!Update.begin(UPDATE_SIZE_UNKNOWN, updateType)) {
          Update.printError(Serial);
          ota_error = Update.errorString();
          ota_in_progress = false;
        }
      }

      // Only process chunks for an authorized, active session. This also drops
      // any post-auth-failure chunks (index > 0) that skipped the block above.
      if (!ota_in_progress) {
        return;
      }
      ota_last_activity = millis();

      // Write data chunk
      if (Update.write(data, len) != len) {
        Update.printError(Serial);
        ota_error = Update.errorString();
        Update.abort();
        ota_in_progress = false;
        return;
      }

      // Update progress
      if (Update.size() > 0) {
        ota_progress_percent = (Update.progress() * 100) / Update.size();
        Serial.printf("OTA Progress: %d%%\r", ota_progress_percent);
      }

      // Finalize update
      if (final) {
        if (Update.end(true)) {
          Serial.printf("\nOTA Update Success: %u bytes\n", index + len);
          ota_in_progress = false;
          ota_progress_percent = 100;
        } else {
          Update.printError(Serial);
          ota_error = Update.errorString();
          Update.abort();
          ota_in_progress = false;
        }
        ota_last_activity = 0;
      }
    }
  );
}
