#ifndef BLE_SERVER_H
#define BLE_SERVER_H

/**
 * BLE Peripheral — advertises a GATT service that the Android phone connects to
 * and subscribes to for real-time car status updates via NOTIFY.
 *
 * Wire format (same as the previous client implementation, sent on the
 * CAR_STATUS NOTIFY characteristic):
 *   Full  (on first send after connect + every 60 s):
 *     "F|S:<vals>|D:<vals>|W:<vals>|E:<vals>|L:<vals>|P:<vals>|C:<vals>|X:<vals>"
 *   Delta (on change only):
 *     "U|<only changed group entries>"
 *
 * MTU handling:
 *   - The phone should request MTU 247 immediately after connect.
 *   - If the negotiated MTU is large enough, payloads go in a single notify.
 *   - If MTU stays small (e.g. default 23 → 20-byte ATT payload), the payload
 *     is split on '|' and each group is sent as its own "U|<group>" notify.
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

void initBleServer();
void handleBleServer();

#endif // BLE_SERVER_H
