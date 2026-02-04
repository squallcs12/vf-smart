#ifndef PINS_H
#define PINS_H

#include <Arduino.h>
#include "pcf8575_io.h"

// ===== ANALOG INPUTS (Sensors) - Use input-only pins =====
#define VF3_BRAKE_PEDAL 39             // GPIO 39 - Brake pedal sensor (input only)
#define VF3_STEERING_ANGLE 36          // GPIO 36 - Steering angle sensor (input only)

// ===== DIGITAL INPUTS (Sensors & Switches) - ESP32 GPIO Pins =====
#define VF3_GEAR_DRIVE 37              // GPIO 37 - Gear in Drive position (1=D, 0=other) (input only)
#define VF3_DOOR_FL 4                  // GPIO 4 - Front left door open/close sensor
#define VF3_DOOR_FR 16                 // GPIO 16 - Front right door open/close sensor
#define VF3_DOOR_TRUNK 17              // GPIO 17 - Trunk/tailgate open/close sensor
#define VF3_BRAKE_SWITCH 2             // GPIO 2 - Brake pressed switch
#define VF3_SEAT_FL 15                 // GPIO 15 - Front left seat occupancy sensor
#define VF3_SEAT_FR 12                 // GPIO 12 - Front right seat occupancy sensor
#define VF3_SEATBELT_FL 14             // GPIO 14 - Front left seatbelt sensor
#define VF3_SEATBELT_FR 27             // GPIO 27 - Front right seatbelt sensor
#define VF3_DEMI_LIGHT 26              // GPIO 26 - Demi/low beam light (0=off, 1=on)
#define VF3_NORMAL_LIGHT 25            // GPIO 25 - Normal/high beam light (0=off, 1=on)
#define VF3_PROXIMITY_REAR_L 33        // GPIO 33 - Rear left proximity/parking detection
#define VF3_PROXIMITY_REAR_R 32        // GPIO 32 - Rear right proximity/parking detection
#define VF3_CHARGING_STATUS 13         // GPIO 13 - Charging status (1=charging, 0=not charging)

// ===== DIGITAL OUTPUTS (Controls & Indicators) - PCF8575 I2C I/O Expander =====
// All outputs now controlled via PCF8575 at I2C address 0x20 (pins P0-P15)
// Use pcf8575.digitalWrite() and pcf8575.digitalRead() for these pins
#define VF3_CAR_LOCK PCF_P0              // P0 - ON for 1s then OFF to lock car
#define VF3_CAR_UNLOCK PCF_P1            // P1 - ON for 1s then OFF to unlock car
#define VF3_BUZZER PCF_P2                // P2 - ON/OFF buzzer
#define VF3_WINDOW_LEFT_UP PCF_P3        // P3 - Window left UP control
#define VF3_WINDOW_RIGHT_UP PCF_P4       // P4 - Window right UP control
#define VF3_DOOR_LOCK PCF_P5             // P5 - ON for 1s then OFF to lock doors
#define SELF_ACCESSORY_POWER PCF_P6      // P6 - ON/OFF accessory power
#define SELF_INSIDE_CARMERAS PCF_P7      // P7 - ON/OFF inside cameras
#define SELF_DASHCAM PCF_P8              // P8 - ON/OFF dashcam
#define SELF_SIDE_MIRRORS_OPEN PCF_P9    // P9 - ON 1s then OFF to open mirrors
#define SELF_SIDE_MIRRORS_CLOSE PCF_P10  // P10 - ON 1s then OFF to close mirrors
#define SELF_ODO_SCREEN PCF_P11          // P11 - ON/OFF odometer screen
#define SELF_ARMREST PCF_P12             // P12 - ON/OFF armrest
#define VF3_CHARGER_UNLOCK PCF_P13       // P13 - ON for 1s then OFF to unlock charger port
#define VF3_WINDOW_LEFT_DOWN PCF_P14     // P14 - Window left DOWN control
#define VF3_WINDOW_RIGHT_DOWN PCF_P15    // P15 - Window right DOWN control

#define WRITE_ON LOW
#define WRITE_OFF HIGH


// ===== INPUT VARIABLES =====
extern int vf3_brake;
extern int vf3_steering_angle;
extern int vf3_gear_drive;
extern int vf3_door_fl;
extern int vf3_door_fr;
extern int vf3_door_trunk;
extern int vf3_seat_fl;
extern int vf3_seat_fr;
extern int vf3_seatbelt_fl;
extern int vf3_seatbelt_fr;
extern int vf3_brake_pressed;
extern int vf3_proximity_rear_l;
extern int vf3_proximity_rear_r;
extern int vf3_demi_light;
extern int vf3_normal_light;
extern int vf3_charging_status;

// ===== OUTPUT VARIABLES =====
extern int vf3_car_lock;
extern int vf3_car_unlock;
extern int self_accessory_power;
extern int self_inside_cameras;
extern int self_dashcam;
extern int self_odo_screen;
extern int self_armrest;
extern int vf3_door_locked;

void initializePins();

#endif // PINS_H
