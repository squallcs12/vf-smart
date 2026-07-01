#ifndef PINS_H
#define PINS_H

#include <Arduino.h>
#include "pcf8575_io.h"

// ===== ANALOG INPUTS (Sensors) - Use input-only pins =====
// --- Pedals & steering ---
#define VF3_BRAKE_PEDAL 39             // GPIO 39 - Brake pedal sensor (input only)
#define VF3_STEERING_ANGLE 36          // GPIO 36 - Steering angle sensor (input only)
// --- Power ---
#define VF3_BATTERY_VOLTAGE 38         // GPIO 38 - Battery voltage sensor via 4:1 voltage divider (input only)

// ===== DIGITAL INPUTS (Sensors & Switches) - ESP32 GPIO Pins =====
// --- Drivetrain ---
#define VF3_GEAR_DRIVE 37              // GPIO 37 - Gear in Drive position (1=D, 0=other) (input only)
#define VF3_BRAKE_SWITCH 2             // GPIO 2 - Brake pressed switch
// --- Windows (state feedback) ---
#define VF3_WINDOW_LEFT_STATE 34       // GPIO 34 - Left window state (0=closed, 1=open) (input only)
#define VF3_WINDOW_RIGHT_STATE 35      // GPIO 35 - Right window state (0=closed, 1=open) (input only)
// --- Doors & trunk ---
// NOTE: Front-left/right door sensors are now PCF8575 inputs (P13/P12) — see the
//       PCF8575 section below. GPIO 4/16 are the native charger-latch outputs.
#define VF3_DOOR_TRUNK 17              // GPIO 17 - Trunk/tailgate open/close sensor
// --- Seats & seatbelts ---
#define VF3_SEAT_FL 15                 // GPIO 15 - Front left seat occupancy sensor
#define VF3_SEAT_FR 12                 // GPIO 12 - Front right seat occupancy sensor
#define VF3_SEATBELT_FL 14             // GPIO 14 - Front left seatbelt sensor
#define VF3_SEATBELT_FR 27             // GPIO 27 - Front right seatbelt sensor
// --- Lights ---
#define VF3_DEMI_LIGHT 26              // GPIO 26 - Demi/low beam light (0=off, 1=on)
#define VF3_NORMAL_LIGHT 25            // GPIO 25 - Normal/high beam light (0=off, 1=on)
// --- Proximity / parking ---
#define VF3_PROXIMITY_REAR_L 33        // GPIO 33 - Rear left proximity/parking detection
#define VF3_PROXIMITY_REAR_R 32        // GPIO 32 - Rear right proximity/parking detection
// --- Charging ---
#define VF3_CHARGING_STATUS 13         // GPIO 13 - Charging status (1=charging, 0=not charging)
// --- Remote key fob ---
#define VF3_REMOTE_LOCK_PRESS 18       // GPIO 18 - Car remote lock button press detection (1=pressed, 0=not pressed)
#define VF3_REMOTE_UNLOCK_PRESS 19     // GPIO 19 - Car remote unlock button press detection (1=pressed, 0=not pressed)
// --- System ---
#define VF3_FACTORY_RESET_BTN 0        // GPIO 0 - Factory reset button (BOOT button, active LOW, hold 10s to reset)
#define TPMS_RF_PIN 23                 // GPIO 23 - RF 433MHz receiver DATA pin (XY-MK-5V or equivalent)

// ===== NATIVE DIGITAL OUTPUTS (ESP32 GPIO) =====
// --- Charger port latch (two-phase pulse: pull then push) ---
#define VF3_CHARGER_UNLOCK_PULL 4        // GPIO 4  - ON 1s then OFF to pull charger latch (phase 1)
#define VF3_CHARGER_UNLOCK_PUSH 16       // GPIO 16 - ON 1s then OFF to push charger latch (phase 2)

// ===== DIGITAL OUTPUTS (Controls & Indicators) - PCF8575 I2C I/O Expander =====
// All outputs now controlled via PCF8575 at I2C address 0x20 (pins P0-P15)
// Use pcf8575.digitalWrite() and pcf8575.digitalRead() for these pins
// --- Locking ---
#define VF3_CAR_LOCK PCF_P0              // P0 - ON for 1s then OFF to lock car
#define VF3_CAR_UNLOCK PCF_P1            // P1 - ON for 1s then OFF to unlock car
#define VF3_DOOR_LOCK PCF_P5             // P5 - ON for 1s then OFF to lock doors
// --- Windows ---
#define VF3_WINDOW_LEFT_UP PCF_P3        // P3 - Window left UP control
#define VF3_WINDOW_RIGHT_UP PCF_P4       // P4 - Window right UP control
#define VF3_WINDOW_LEFT_DOWN PCF_P14     // P14 - Window left DOWN control
#define VF3_WINDOW_RIGHT_DOWN PCF_P15    // P15 - Window right DOWN control
// --- Mirrors ---
#define SELF_SIDE_MIRRORS_OPEN PCF_P9    // P9 - ON 1s then OFF to open mirrors
#define SELF_SIDE_MIRRORS_CLOSE PCF_P10  // P10 - ON 1s then OFF to close mirrors
// --- Trunk ---
#define VF3_FRONT_TRUNK_UNLOCK PCF_P7    // P7 - ON for 8s then OFF to unlock front trunk
// --- Door sensors (inputs) ---
#define VF3_DOOR_FL PCF_P13              // P13 - Front left door open/close sensor (input)
#define VF3_DOOR_FR PCF_P12              // P12 - Front right door open/close sensor (input)
// --- Accessory & cabin ---
#define SELF_ACCESSORY_POWER PCF_P6      // P6 - ON/OFF accessory power
#define SELF_INSIDE_CARMERAS PCF_P8      // P8 - ON/OFF inside cameras
#define VF3_BUZZER PCF_P2                // P2 - ON/OFF buzzer
// --- Reserved ---
// P11 - Reserved for future use

#define WRITE_ON LOW
#define WRITE_OFF HIGH


// ===== INPUT VARIABLES =====
// --- Pedals, steering & drivetrain ---
extern int vf3_brake;
extern int vf3_steering_angle;
extern int vf3_gear_drive;
extern int vf3_brake_pressed;
// --- Power ---
extern float vf3_battery_voltage;
extern int vf3_charging_status;
// --- Windows ---
extern int vf3_window_left_state;
extern int vf3_window_right_state;
// --- Doors & trunk ---
extern int vf3_door_fl;
extern int vf3_door_fr;
extern int vf3_door_trunk;
// --- Seats & seatbelts ---
extern int vf3_seat_fl;
extern int vf3_seat_fr;
extern int vf3_seatbelt_fl;
extern int vf3_seatbelt_fr;
// --- Lights ---
extern int vf3_demi_light;
extern int vf3_normal_light;
// --- Proximity / parking ---
extern int vf3_proximity_rear_l;
extern int vf3_proximity_rear_r;
// --- Remote key fob ---
extern int vf3_remote_lock_press;
extern int vf3_remote_unlock_press;

// ===== OUTPUT VARIABLES =====
// --- Locking ---
extern int vf3_car_lock;
extern int vf3_car_unlock;
extern int vf3_door_locked;
// --- Trunk ---
extern int vf3_front_trunk_unlock;
// --- Accessory & cabin ---
extern int self_accessory_power;
extern int self_inside_cameras;

void initializePins();

#endif // PINS_H
