#include "status.h"
#include <time.h>

String getCarStatusJSON() {
  JsonDocument doc;

  // Analog sensor values
  JsonObject sensors = doc["sensors"].to<JsonObject>();
  sensors["accelerator"] = vf3_accelerator;
  sensors["brake"] = vf3_brake;
  sensors["steering_angle"] = vf3_steering_angle;
  sensors["vehicle_speed"] = vf3_vehicle_speed;
  sensors["gear_drive"] = vf3_gear_drive;

  // Door status
  JsonObject doors = doc["doors"].to<JsonObject>();
  doors["front_left"] = vf3_door_fl;
  doors["front_right"] = vf3_door_fr;
  doors["trunk"] = vf3_door_trunk;
  doors["locked"] = vf3_door_locked;

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

  // Proximity sensors
  JsonObject proximity = doc["proximity"].to<JsonObject>();
  proximity["rear_left"] = vf3_proximity_rear_l;
  proximity["rear_right"] = vf3_proximity_rear_r;

  // Controls status
  JsonObject controls = doc["controls"].to<JsonObject>();
  controls["brake_pressed"] = vf3_brake_pressed;
  controls["accessory_power"] = vf3_accessory_power;
  controls["car_lock"] = vf3_car_lock;
  controls["car_unlock"] = vf3_car_unlock;

  // Window timer status
  doc["window_close_active"] = (window_close_timer != 0);
  if (window_close_timer != 0) {
    doc["window_close_remaining_ms"] = WINDOW_CLOSE_DURATION - (millis() - window_close_timer);
  } else {
    doc["window_close_remaining_ms"] = 0;
  }

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
