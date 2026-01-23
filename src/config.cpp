#include "config.h"

// ===== CONFIGURATION STORAGE =====
Preferences preferences;
String configured_ssid = "";
String configured_password = "";
String configured_api_key = "";
bool is_configured = false;

// ===== DEFAULT ONBOARDING AP =====
const char* onboarding_ssid = "VF3-SETUP";
const char* onboarding_password = "setup123";

// ===== NTP TIME CONFIGURATION =====
const char* ntp_server = "pool.ntp.org";
const long gmt_offset_sec = 7 * 3600;     // GMT+7 for Vietnam
const int daylight_offset_sec = 0;        // No daylight saving in Vietnam
struct tm boot_time;
bool time_synced = false;

// ===== WEBSOCKET STATUS BROADCAST =====
unsigned long lastStatusBroadcast = 0;

// ===== LIGHT REMINDER =====
unsigned long light_reminder_timer = 0;
unsigned long last_light_reminder = 0;

// ===== WINDOW CONTROL =====
unsigned long window_close_timer = 0;
