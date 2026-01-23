#include "pins.h"

// ===== INPUT VARIABLES =====
int vf3_accelerator = 0;
int vf3_brake = 0;
int vf3_steering_angle = 0;
int vf3_vehicle_speed = 0;
int vf3_gear_drive = LOW;
int vf3_door_fl = LOW;
int vf3_door_fr = LOW;
int vf3_door_trunk = LOW;
int vf3_seat_fl = LOW;
int vf3_seat_fr = LOW;
int vf3_seatbelt_fl = LOW;
int vf3_seatbelt_fr = LOW;
int vf3_brake_pressed = LOW;
int vf3_proximity_rear_l = LOW;
int vf3_proximity_rear_r = LOW;
int vf3_demi_light = LOW;
int vf3_normal_light = LOW;

// ===== OUTPUT VARIABLES =====
int vf3_car_lock = LOW;
int vf3_car_unlock = LOW;
int vf3_accessory_power = HIGH;
int vf3_door_locked = LOW;

void initializePins() {
  // Initialize Digital Input Pins
  pinMode(VF3_SPEED_SENSOR, INPUT);
  pinMode(VF3_GEAR_DRIVE, INPUT);
  pinMode(VF3_DOOR_FL, INPUT);
  pinMode(VF3_DOOR_FR, INPUT);
  pinMode(VF3_DOOR_TRUNK, INPUT);
  pinMode(VF3_BRAKE_SWITCH, INPUT);
  pinMode(VF3_SEAT_FL, INPUT);
  pinMode(VF3_SEAT_FR, INPUT);
  pinMode(VF3_SEATBELT_FL, INPUT);
  pinMode(VF3_SEATBELT_FR, INPUT);
  pinMode(VF3_DEMI_LIGHT, INPUT);
  pinMode(VF3_NORMAL_LIGHT, INPUT);
  pinMode(VF3_PROXIMITY_REAR_L, INPUT);
  pinMode(VF3_PROXIMITY_REAR_R, INPUT);

  // Initialize Digital Output Pins (Control Systems)
  pinMode(VF3_CAR_LOCK, OUTPUT);
  pinMode(VF3_CAR_UNLOCK, OUTPUT);
  pinMode(VF3_DOOR_LOCK, OUTPUT);
  pinMode(VF3_TURN_SIGNAL_L, OUTPUT);
  pinMode(VF3_TURN_SIGNAL_R, OUTPUT);
  pinMode(VF3_BUZZER, OUTPUT);
  pinMode(VF3_WINDOW_LEFT, OUTPUT);
  pinMode(VF3_WINDOW_RIGHT, OUTPUT);
  pinMode(VF3_ACCESSORY_POWER, OUTPUT);

  // Turn on accessory power on startup
  digitalWrite(VF3_ACCESSORY_POWER, HIGH);
}
