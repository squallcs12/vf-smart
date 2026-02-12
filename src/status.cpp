#include "status.h"
#include <time.h>

String getCarStatusJSON() {
  JsonDocument doc;

  // Analog sensor values
  JsonObject sensors = doc["sensors"].to<JsonObject>();
  sensors["brake"] = vf3_brake;
  sensors["steering_angle"] = vf3_steering_angle;
  sensors["battery_voltage"] = serialized(String(vf3_battery_voltage, 2));  // 2 decimal places
  sensors["gear_drive"] = vf3_gear_drive;

  // Door status
  JsonObject doors = doc["doors"].to<JsonObject>();
  doors["front_left"] = vf3_door_fl;
  doors["front_right"] = vf3_door_fr;
  doors["trunk"] = vf3_door_trunk;
  doors["locked"] = vf3_door_locked;

  // Window state
  JsonObject windows = doc["windows"].to<JsonObject>();
  windows["left_state"] = vf3_window_left_state;
  windows["right_state"] = vf3_window_right_state;

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

  // Charging status
  doc["charging_status"] = vf3_charging_status;

  // Proximity sensors
  JsonObject proximity = doc["proximity"].to<JsonObject>();
  proximity["rear_left"] = vf3_proximity_rear_l;
  proximity["rear_right"] = vf3_proximity_rear_r;

  // Controls status
  JsonObject controls = doc["controls"].to<JsonObject>();
  controls["brake_pressed"] = vf3_brake_pressed;
  controls["accessory_power"] = self_accessory_power;
  controls["inside_cameras"] = self_inside_cameras;
  controls["car_lock"] = vf3_car_lock;
  controls["car_unlock"] = vf3_car_unlock;
  controls["dashcam"] = self_dashcam;
  controls["odo_screen"] = self_odo_screen;
  controls["armrest"] = self_armrest;

  // Car lock state
  doc["car_lock_state"] = (car_lock_state == CAR_LOCKED) ? "locked" : "unlocked";

  // Window timer status
  doc["window_close_active"] = (window_close_timer != 0);
  if (window_close_timer != 0) {
    doc["window_close_remaining_ms"] = WINDOW_CLOSE_DURATION - (millis() - window_close_timer);
  } else {
    doc["window_close_remaining_ms"] = 0;
  }

  // Light reminder status
  doc["light_reminder_enabled"] = light_reminder_enabled;

  // Time information
  JsonObject time_info = doc["time"].to<JsonObject>();
  time_info["synced"] = time_synced;
  if (time_synced) {
    struct tm timeinfo;
    if (getLocalTime(&timeinfo)) {
      char current_time_str[64];
      char boot_time_str[64];
      sprintf(current_time_str, "%04d-%02d-%02d %02d:%02d:%02d",
              timeinfo.tm_year + 1900, timeinfo.tm_mon + 1, timeinfo.tm_mday,
              timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec);
      sprintf(boot_time_str, "%04d-%02d-%02d %02d:%02d:%02d",
              boot_time.tm_year + 1900, boot_time.tm_mon + 1, boot_time.tm_mday,
              boot_time.tm_hour, boot_time.tm_min, boot_time.tm_sec);

      time_info["current_time"] = current_time_str;
      time_info["boot_time"] = boot_time_str;
      time_info["is_night"] = isNightTime();
    }
  }

  String output;
  serializeJson(doc, output);
  return output;
}
