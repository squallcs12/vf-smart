#include "pins.h"
#include <Arduino.h>

// ===== INPUT VARIABLES =====
int vf3_brake = 0;
int vf3_steering_angle = 0;
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
int vf3_charging_status = LOW;

// ===== OUTPUT VARIABLES =====
int vf3_car_lock = LOW;
int vf3_car_unlock = LOW;
int self_accessory_power = HIGH;
int self_inside_cameras = LOW;
int self_dashcam = WRITE_ON;  // ON by default at boot
int self_odo_screen = WRITE_ON;  // ON by default at boot
int self_armrest = WRITE_ON;  // ON by default at boot
int vf3_door_locked = LOW;

void initializePins() {
  // Initialize Digital Input Pins (ESP32 GPIO)
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
  pinMode(VF3_CHARGING_STATUS, INPUT);

  // Initialize PCF8575 I2C I/O Expander for all digital outputs
  if (!initPCF8575()) {
    Serial.println("FATAL ERROR: Failed to initialize PCF8575!");
    Serial.println("Check I2C connections (SDA=21, SCL=22) and I2C address (0x20)");
    // Continue anyway - outputs just won't work
  }

  // Initialize all PCF8575 outputs to OFF state
  pcf8575.digitalWrite(VF3_CAR_LOCK, WRITE_OFF);
  pcf8575.digitalWrite(VF3_CAR_UNLOCK, WRITE_OFF);
  pcf8575.digitalWrite(VF3_DOOR_LOCK, WRITE_OFF);
  pcf8575.digitalWrite(VF3_BUZZER, WRITE_OFF);
  pcf8575.digitalWrite(VF3_WINDOW_LEFT_UP, WRITE_OFF);
  pcf8575.digitalWrite(VF3_WINDOW_RIGHT_UP, WRITE_OFF);
  pcf8575.digitalWrite(VF3_WINDOW_LEFT_DOWN, WRITE_OFF);
  pcf8575.digitalWrite(VF3_WINDOW_RIGHT_DOWN, WRITE_OFF);
  pcf8575.digitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_OFF);
  pcf8575.digitalWrite(SELF_SIDE_MIRRORS_CLOSE, WRITE_OFF);
  pcf8575.digitalWrite(VF3_CHARGER_UNLOCK, WRITE_OFF);

  // Turn on accessory power on startup
  pcf8575.digitalWrite(SELF_ACCESSORY_POWER, WRITE_ON);

  // Turn on related accessories since accessory power is ON by default
  pcf8575.digitalWrite(SELF_DASHCAM, WRITE_ON);
  pcf8575.digitalWrite(SELF_ODO_SCREEN, WRITE_ON);
  pcf8575.digitalWrite(SELF_ARMREST, WRITE_ON);
  pcf8575.digitalWrite(SELF_INSIDE_CARMERAS, WRITE_OFF);

  // Open side mirrors on startup (1 second pulse - will be handled by accessory_power handler)
  pcf8575.digitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_ON);
  delay(1000);
  pcf8575.digitalWrite(SELF_SIDE_MIRRORS_OPEN, WRITE_OFF);
}
