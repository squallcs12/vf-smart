#include "sensors.h"

void readSensors() {
  // Read analog sensors
  vf3_accelerator = analogRead(VF3_ACCELERATOR_PEDAL);
  vf3_brake = analogRead(VF3_BRAKE_PEDAL);
  vf3_steering_angle = analogRead(VF3_STEERING_ANGLE);

  // Read digital sensors
  vf3_vehicle_speed = digitalRead(VF3_SPEED_SENSOR);
  vf3_gear_drive = digitalRead(VF3_GEAR_DRIVE);
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
}
