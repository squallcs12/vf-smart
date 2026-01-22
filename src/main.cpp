#include <Arduino.h>

// VinFast VF3 Electric Car - Input and Output Definitions
// =========================================================

// ===== ANALOG INPUTS (Sensors) - ADC1 pins =====
#define VF3_MOTOR_TEMP 35              // GPIO 35 - Motor temperature sensor
#define VF3_ACCELERATOR_PEDAL 33       // GPIO 33 - Accelerator pedal position
#define VF3_BRAKE_PEDAL 25             // GPIO 25 - Brake pedal sensor
#define VF3_STEERING_ANGLE 26          // GPIO 26 - Steering angle sensor

// ===== DIGITAL INPUTS (Sensors & Switches) =====
#define VF3_SPEED_SENSOR 4             // GPIO 4 - Vehicle speed sensor
#define VF3_DOOR_FL 14                 // GPIO 14 - Front left door open/close sensor
#define VF3_DOOR_FR 15                 // GPIO 15 - Front right door open/close sensor
#define VF3_DOOR_TRUNK 24              // GPIO 24 - Trunk/tailgate open/close sensor
#define VF3_BRAKE_SWITCH 16            // GPIO 16 - Brake pressed switch
#define VF3_SEAT_FL 27                 // GPIO 27 - Front left seat occupancy sensor
#define VF3_SEAT_FR 28                 // GPIO 28 - Front right seat occupancy sensor
#define VF3_SEATBELT_FL 30             // GPIO 30 - Front left seatbelt sensor
#define VF3_SEATBELT_FR 31             // GPIO 31 - Front right seatbelt sensor
#define VF3_DEMI_LIGHT 18              // GPIO 18 - Demi/low beam light (0=off, 1=on)
#define VF3_NORMAL_LIGHT 19            // GPIO 19 - Normal/high beam light (0=off, 1=on)
#define VF3_PROXIMITY_REAR_L 20        // GPIO 20 - Rear left proximity/parking detection
#define VF3_PROXIMITY_REAR_R 21        // GPIO 21 - Rear right proximity/parking detection

// ===== DIGITAL OUTPUTS (Controls & Indicators) =====
#define VF3_CAR_LOCK 5                 // GPIO 5 - Car lock control
#define VF3_CAR_UNLOCK 6               // GPIO 6 - Car unlock control
#define VF3_TURN_SIGNAL_L 2            // GPIO 2 - Left turn signal
#define VF3_TURN_SIGNAL_R 0            // GPIO 0 - Right turn signal
#define VF3_BUZZER 8                   // GPIO 8 - Warning buzzer/alarm
#define VF3_WINDOW_LEFT 10             // GPIO 10 - Front left window control
#define VF3_WINDOW_RIGHT 11            // GPIO 11 - Front right window control
#define VF3_ACCESSORY_POWER 7          // GPIO 7 - Accessory power control

// ===== INPUT VARIABLES =====
int vf3_motor_temp = 0;               // Motor temperature (°C)
int vf3_accelerator = 0;              // Accelerator pedal (0-100%)
int vf3_brake = 0;                    // Brake pedal (0-100%)
int vf3_steering_angle = 0;           // Steering angle (-180 to +180°)
int vf3_vehicle_speed = 0;            // Vehicle speed (km/h)
int vf3_door_fl = LOW;                // Front left door (0=closed, 1=open)
int vf3_door_fr = LOW;                // Front right door (0=closed, 1=open)
int vf3_door_trunk = LOW;             // Trunk/tailgate (0=closed, 1=open)
int vf3_seat_fl = LOW;                // Front left seat (0=empty, 1=occupied)
int vf3_seat_fr = LOW;                // Front right seat (0=empty, 1=occupied)
int vf3_seatbelt_fl = LOW;            // Front left seatbelt (0=unbuckled, 1=buckled)
int vf3_seatbelt_fr = LOW;            // Front right seatbelt (0=unbuckled, 1=buckled)
int vf3_brake_pressed = LOW;          // 0=not pressed, 1=pressed
int vf3_proximity_rear_l = LOW;       // Rear left proximity sensor (0=clear, 1=detected)
int vf3_proximity_rear_r = LOW;       // Rear right proximity sensor (0=clear, 1=detected)
int vf3_demi_light = LOW;             // Demi/low beam light (0=off, 1=on)
int vf3_normal_light = LOW;           // Normal/high beam light (0=off, 1=on)

// ===== OUTPUT VARIABLES =====
int vf3_car_lock = LOW;               // Car lock control
int vf3_car_unlock = LOW;             // Car unlock control
int vf3_accessory_power = HIGH;       // Accessory power control (default ON)
unsigned long window_close_timer = 0; // Timer for auto-close windows feature
#define WINDOW_CLOSE_DURATION 30000    // Window close duration in milliseconds (30 seconds)
#define VF3_DOOR_LOCK 13               // GPIO 13 - Door lock/unlock relay
int vf3_door_locked = LOW;            // 0=unlocked, 1=locked

// ===== FUNCTION DECLARATIONS =====
void handleAccessoryPower();
void handleWindowControl();

void setup() {
  // Initialize Serial Communication
  Serial.begin(9600);
  Serial.println("VinFast VF3 MCU Control System Initializing...");
  
  // Initialize Digital Input Pins
  pinMode(VF3_SPEED_SENSOR, INPUT);
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
  
  Serial.println("VinFast VF3 MCU System Ready!");
}

void loop() {
  // ===== READ ANALOG SENSORS =====
  vf3_motor_temp = analogRead(VF3_MOTOR_TEMP);
  vf3_accelerator = analogRead(VF3_ACCELERATOR_PEDAL);
  vf3_brake = analogRead(VF3_BRAKE_PEDAL);
  vf3_steering_angle = analogRead(VF3_STEERING_ANGLE);
  
  // ===== READ DIGITAL SENSORS =====
  vf3_vehicle_speed = digitalRead(VF3_SPEED_SENSOR);
  vf3_door_fl = digitalRead(VF3_DOOR_FL);
  vf3_door_fr = digitalRead(VF3_DOOR_FR);
  vf3_door_trunk = digitalRead(VF3_DOOR_TRUNK);
  vf3_seat_fl = digitalRead(VF3_SEAT_FL);
  vf3_seat_fr = digitalRead(VF3_SEAT_FR);
  vf3_seatbelt_fl = digitalRead(VF3_SEATBELT_FL);
  vf3_seatbelt_fr = digitalRead(VF3_SEATBELT_FR);
  vf3_brake_pressed = digitalRead(VF3_BRAKE_SWITCH);
  vf3_demi_light = digitalRead(VF3_DEMI_LIGHT);
  vf3_normal_light = digitalRead(VF3_NORMAL_LIGHT);
  vf3_proximity_rear_l = digitalRead(VF3_PROXIMITY_REAR_L);
  vf3_proximity_rear_r = digitalRead(VF3_PROXIMITY_REAR_R);
  
  // ===== CONTROL LOGIC =====
  
  // Handle window control
  handleWindowControl();
  
  // Handle accessory power
  handleAccessoryPower();
  
  delay(50);  // 50ms control loop cycle
}

// ===== FUNCTION DEFINITIONS =====
void handleWindowControl() {
  // Auto close windows when car is locked (on for 30s, then off)
  if (vf3_car_lock == HIGH) {
    // Lock signal detected, start/reset timer
    window_close_timer = millis();
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
  } else if (window_close_timer != 0 && millis() - window_close_timer < WINDOW_CLOSE_DURATION) {
    // Keep windows closing for 30 seconds
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
  } else {
    // Timer expired or lock not active
    digitalWrite(VF3_WINDOW_LEFT, LOW);
    digitalWrite(VF3_WINDOW_RIGHT, LOW);
    window_close_timer = 0;  // Reset timer after windows finish closing
  }
}

void handleAccessoryPower() {
  // Turn off accessory power when car is locked
  if (vf3_car_lock == HIGH) {
    digitalWrite(VF3_ACCESSORY_POWER, LOW);
    vf3_accessory_power = LOW;
  }
  
  // Turn on accessory power when car is unlocked
  if (vf3_car_unlock == HIGH) {
    digitalWrite(VF3_ACCESSORY_POWER, HIGH);
    vf3_accessory_power = HIGH;
  }