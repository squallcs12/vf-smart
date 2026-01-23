#include "time_sync.h"
#include "config.h"
#include <Arduino.h>
#include <time.h>

void syncTime() {
  Serial.println("Syncing time with NTP server...");

  // Configure time with NTP server
  configTime(gmt_offset_sec, daylight_offset_sec, ntp_server);

  // Wait for time to be set
  struct tm timeinfo;
  int retry = 0;
  const int max_retry = 10;

  while (!getLocalTime(&timeinfo) && retry < max_retry) {
    Serial.print(".");
    delay(1000);
    retry++;
  }

  if (retry < max_retry) {
    time_synced = true;
    boot_time = timeinfo;

    Serial.println("\nTime synced successfully!");
    Serial.print("Device boot time: ");
    Serial.printf("%04d-%02d-%02d %02d:%02d:%02d\n",
                  boot_time.tm_year + 1900,
                  boot_time.tm_mon + 1,
                  boot_time.tm_mday,
                  boot_time.tm_hour,
                  boot_time.tm_min,
                  boot_time.tm_sec);
  } else {
    Serial.println("\nFailed to sync time with NTP server");
    time_synced = false;
  }
}

bool isNightTime() {
  if (!time_synced) {
    return false;
  }

  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    return false;
  }

  int hour = timeinfo.tm_hour;

  // Night time is from 18:00 (6 PM) to 06:00 (6 AM)
  return (hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR);
}
