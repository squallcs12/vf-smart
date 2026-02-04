#ifndef CONFIG_H
#define CONFIG_H

#include <Arduino.h>
#include <Preferences.h>

// ===== CONFIGURATION STORAGE =====
extern Preferences preferences;
extern String configured_ssid;
extern String configured_password;
extern String configured_api_key;
extern bool is_configured;

// ===== DEFAULT ONBOARDING AP =====
extern const char* onboarding_ssid;
extern const char* onboarding_password;

// ===== NTP TIME CONFIGURATION =====
extern const char* ntp_server;
extern const long gmt_offset_sec;
extern const int daylight_offset_sec;
extern struct tm boot_time;
extern bool time_synced;

// ===== LIGHT REMINDER CONFIGURATION =====
extern unsigned long light_reminder_timer;
extern unsigned long last_light_reminder;
extern bool light_reminder_enabled;
#define LIGHT_REMINDER_INTERVAL 30000          // Remind every 30 seconds
#define LIGHT_REMINDER_BEEP_DURATION 200       // Beep duration in milliseconds
#define NIGHT_START_HOUR 18                    // 6 PM
#define NIGHT_END_HOUR 6                       // 6 AM

// ===== WINDOW CONTROL =====
extern unsigned long window_close_timer;
#define WINDOW_CLOSE_DURATION 30000    // Window close duration in milliseconds (30 seconds)

#endif // CONFIG_H
