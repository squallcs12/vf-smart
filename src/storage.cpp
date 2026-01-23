#include "storage.h"

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
