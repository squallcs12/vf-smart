#include "sensors.h"

// Previous sensor values for change detection
static int prev_vf3_brake = -1;
static int prev_vf3_steering_angle = -1;
static int prev_vf3_gear_drive = -1;
static int prev_vf3_window_left_state = -1;
static int prev_vf3_window_right_state = -1;
static int prev_vf3_door_fl = -1;
static int prev_vf3_door_fr = -1;
static int prev_vf3_door_trunk = -1;
static int prev_vf3_seat_fl = -1;
static int prev_vf3_seat_fr = -1;
static int prev_vf3_seatbelt_fl = -1;
static int prev_vf3_seatbelt_fr = -1;
static int prev_vf3_brake_pressed = -1;
static int prev_vf3_demi_light = -1;
static int prev_vf3_normal_light = -1;
static int prev_vf3_proximity_rear_l = -1;
static int prev_vf3_proximity_rear_r = -1;
static int prev_vf3_charging_status = -1;
static int prev_vf3_car_lock = -1;
static int prev_vf3_car_unlock = -1;
static int prev_self_accessory_power = -1;
static int prev_self_inside_cameras = -1;
static int prev_self_dashcam = -1;
static int prev_self_odo_screen = -1;
static int prev_self_armrest = -1;

bool readSensors() {
  bool changed = false;

  // Read analog sensors (ESP32 ADC)
  int new_brake = analogRead(VF3_BRAKE_PEDAL);
  int new_steering_angle = analogRead(VF3_STEERING_ANGLE);

  // Read digital sensors (ESP32 GPIO)
  int new_gear_drive = digitalRead(VF3_GEAR_DRIVE);
  int new_window_left_state = digitalRead(VF3_WINDOW_LEFT_STATE);
  int new_window_right_state = digitalRead(VF3_WINDOW_RIGHT_STATE);
  int new_door_fl = digitalRead(VF3_DOOR_FL);
  int new_door_fr = digitalRead(VF3_DOOR_FR);
  int new_door_trunk = digitalRead(VF3_DOOR_TRUNK);
  int new_seat_fl = digitalRead(VF3_SEAT_FL);
  int new_seat_fr = digitalRead(VF3_SEAT_FR);
  int new_seatbelt_fl = digitalRead(VF3_SEATBELT_FL);
  int new_seatbelt_fr = digitalRead(VF3_SEATBELT_FR);
  int new_brake_pressed = digitalRead(VF3_BRAKE_SWITCH);
  int new_demi_light = digitalRead(VF3_DEMI_LIGHT);
  int new_normal_light = digitalRead(VF3_NORMAL_LIGHT);
  int new_proximity_rear_l = digitalRead(VF3_PROXIMITY_REAR_L);
  int new_proximity_rear_r = digitalRead(VF3_PROXIMITY_REAR_R);
  int new_charging_status = digitalRead(VF3_CHARGING_STATUS);

  // Read output states from PCF8575 (for status reporting)
  int new_car_lock = safeDigitalRead(VF3_CAR_LOCK);
  int new_car_unlock = safeDigitalRead(VF3_CAR_UNLOCK);
  int new_accessory_power = safeDigitalRead(SELF_ACCESSORY_POWER);
  int new_inside_cameras = safeDigitalRead(SELF_INSIDE_CARMERAS);
  int new_dashcam = safeDigitalRead(SELF_DASHCAM);
  int new_odo_screen = safeDigitalRead(SELF_ODO_SCREEN);
  int new_armrest = safeDigitalRead(SELF_ARMREST);

  // Check for changes (using threshold for analog values to avoid noise)
  if (abs(new_brake - prev_vf3_brake) > 10) changed = true;
  if (abs(new_steering_angle - prev_vf3_steering_angle) > 10) changed = true;

  // Check digital sensors
  if (new_gear_drive != prev_vf3_gear_drive) changed = true;
  if (new_window_left_state != prev_vf3_window_left_state) changed = true;
  if (new_window_right_state != prev_vf3_window_right_state) changed = true;
  if (new_door_fl != prev_vf3_door_fl) changed = true;
  if (new_door_fr != prev_vf3_door_fr) changed = true;
  if (new_door_trunk != prev_vf3_door_trunk) changed = true;
  if (new_seat_fl != prev_vf3_seat_fl) changed = true;
  if (new_seat_fr != prev_vf3_seat_fr) changed = true;
  if (new_seatbelt_fl != prev_vf3_seatbelt_fl) changed = true;
  if (new_seatbelt_fr != prev_vf3_seatbelt_fr) changed = true;
  if (new_brake_pressed != prev_vf3_brake_pressed) changed = true;
  if (new_demi_light != prev_vf3_demi_light) changed = true;
  if (new_normal_light != prev_vf3_normal_light) changed = true;
  if (new_proximity_rear_l != prev_vf3_proximity_rear_l) changed = true;
  if (new_proximity_rear_r != prev_vf3_proximity_rear_r) changed = true;
  if (new_charging_status != prev_vf3_charging_status) changed = true;
  if (new_car_lock != prev_vf3_car_lock) changed = true;
  if (new_car_unlock != prev_vf3_car_unlock) changed = true;
  if (new_accessory_power != prev_self_accessory_power) changed = true;
  if (new_inside_cameras != prev_self_inside_cameras) changed = true;
  if (new_dashcam != prev_self_dashcam) changed = true;
  if (new_odo_screen != prev_self_odo_screen) changed = true;
  if (new_armrest != prev_self_armrest) changed = true;

  // Update global variables
  vf3_brake = new_brake;
  vf3_steering_angle = new_steering_angle;
  vf3_gear_drive = new_gear_drive;
  vf3_window_left_state = new_window_left_state;
  vf3_window_right_state = new_window_right_state;
  vf3_door_fl = new_door_fl;
  vf3_door_fr = new_door_fr;
  vf3_door_trunk = new_door_trunk;
  vf3_seat_fl = new_seat_fl;
  vf3_seat_fr = new_seat_fr;
  vf3_seatbelt_fl = new_seatbelt_fl;
  vf3_seatbelt_fr = new_seatbelt_fr;
  vf3_brake_pressed = new_brake_pressed;
  vf3_demi_light = new_demi_light;
  vf3_normal_light = new_normal_light;
  vf3_proximity_rear_l = new_proximity_rear_l;
  vf3_proximity_rear_r = new_proximity_rear_r;
  vf3_charging_status = new_charging_status;
  vf3_car_lock = new_car_lock;
  vf3_car_unlock = new_car_unlock;
  self_accessory_power = new_accessory_power;
  self_inside_cameras = new_inside_cameras;
  self_dashcam = new_dashcam;
  self_odo_screen = new_odo_screen;
  self_armrest = new_armrest;

  // Update previous values
  prev_vf3_brake = new_brake;
  prev_vf3_steering_angle = new_steering_angle;
  prev_vf3_gear_drive = new_gear_drive;
  prev_vf3_window_left_state = new_window_left_state;
  prev_vf3_window_right_state = new_window_right_state;
  prev_vf3_door_fl = new_door_fl;
  prev_vf3_door_fr = new_door_fr;
  prev_vf3_door_trunk = new_door_trunk;
  prev_vf3_seat_fl = new_seat_fl;
  prev_vf3_seat_fr = new_seat_fr;
  prev_vf3_seatbelt_fl = new_seatbelt_fl;
  prev_vf3_seatbelt_fr = new_seatbelt_fr;
  prev_vf3_brake_pressed = new_brake_pressed;
  prev_vf3_demi_light = new_demi_light;
  prev_vf3_normal_light = new_normal_light;
  prev_vf3_proximity_rear_l = new_proximity_rear_l;
  prev_vf3_proximity_rear_r = new_proximity_rear_r;
  prev_vf3_charging_status = new_charging_status;
  prev_vf3_car_lock = new_car_lock;
  prev_vf3_car_unlock = new_car_unlock;
  prev_self_accessory_power = new_accessory_power;
  prev_self_inside_cameras = new_inside_cameras;
  prev_self_dashcam = new_dashcam;
  prev_self_odo_screen = new_odo_screen;
  prev_self_armrest = new_armrest;

  return changed;
}
