#ifndef BLE_CLIENT_H
#define BLE_CLIENT_H

/**
 * BLE Central client — sends car status to Android phone's GATT server.
 *
 * Replaces WebSocket for real-time status updates.
 * Only transmits groups whose values have changed (delta protocol).
 *
 * Wire format:
 *   Full  (on connect + every 60 s):  "F|S:<vals>|D:<vals>|W:<vals>|E:<vals>|L:<vals>|P:<vals>|C:<vals>|X:<vals>"
 *   Delta (on change only):           "U|<only changed group entries>"
 *
 * Group IDs:
 *   S = sensors    (brake, steering, voltage, gear)
 *   D = doors      (fl, fr, trunk, locked)
 *   W = windows    (left_state, right_state)
 *   E = seats      (flo, fro, flb, frb)
 *   L = lights     (demi, normal)
 *   P = proximity  (rear_l, rear_r)
 *   C = controls   (brake_pressed, acc_power, cameras, car_lock, car_unlock)
 *   X = misc       (charging, lock_state, wca, wcr_secs, lr, is_night)
 */

void initBleClient();
void handleBleClient();

#endif // BLE_CLIENT_H
