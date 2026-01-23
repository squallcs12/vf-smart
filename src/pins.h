#ifndef PINS_H
#define PINS_H

#include <Arduino.h>

// ===== ANALOG INPUTS (Sensors) - Use input-only pins =====
#define VF3_ACCELERATOR_PEDAL 34       // GPIO 34 - Accelerator pedal position (input only)
#define VF3_BRAKE_PEDAL 39             // GPIO 39 - Brake pedal sensor (input only)
#define VF3_STEERING_ANGLE 36          // GPIO 36 - Steering angle sensor (input only)

// ===== DIGITAL INPUTS (Sensors & Switches) =====
#define VF3_SPEED_SENSOR 35            // GPIO 35 - Vehicle speed sensor (input only)
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

// ===== DIGITAL OUTPUTS (Controls & Indicators) =====
#define VF3_CAR_LOCK 5                 // GPIO 5 - Car lock control
#define VF3_CAR_UNLOCK 18              // GPIO 18 - Car unlock control
#define VF3_TURN_SIGNAL_L 19           // GPIO 19 - Left turn signal
#define VF3_TURN_SIGNAL_R 21           // GPIO 21 - Right turn signal
#define VF3_BUZZER 22                  // GPIO 22 - Warning buzzer/alarm
#define VF3_WINDOW_LEFT 23             // GPIO 23 - Front left window control
#define VF3_WINDOW_RIGHT 13            // GPIO 13 - Front right window control
#define VF3_ACCESSORY_POWER 0          // GPIO 0 - Accessory power control (boot pin - ensure LOW at boot)
#define VF3_BRAKE_SIGNAL 1             // GPIO 1 - Brake signal/brake light (TX pin - use carefully)
#define VF3_DOOR_LOCK 13               // GPIO 13 - Door lock/unlock relay

// ===== INPUT VARIABLES =====
extern int vf3_accelerator;
extern int vf3_brake;
extern int vf3_steering_angle;
extern int vf3_vehicle_speed;
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

// ===== OUTPUT VARIABLES =====
extern int vf3_car_lock;
extern int vf3_car_unlock;
extern int vf3_accessory_power;
extern int vf3_door_locked;

void initializePins();

#endif // PINS_H
