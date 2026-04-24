package com.daotranbang.vfsmart.navigation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import android.bluetooth.BluetoothProfile
import com.daotranbang.vfsmart.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE GATT server with two write characteristics:
 *
 *   TPMS_CHAR        — tire pressure data  →  [tpmsState]
 *   SPEED_LIMIT_CHAR — speed limit km/h    →  [speedLimitState]
 *
 * The phone advertises as a peripheral. The client (ESP32) connects and writes.
 *
 * ── Wire formats ──────────────────────────────────────────────────────────────
 *
 * TPMS_CHAR  "FL_KPA,FL_TEMP,FL_ALARM|FR_KPA,FR_TEMP,FR_ALARM|RL_...|RR_..."
 *   e.g.  "225.5,28,0|227.0,29,0|220.0,27,0|221.5,28,1"
 *   Each field: pressure (kPa float), temperature (°C int), alarm (0/1).
 *   "" = no TPMS data.
 */
class VF3GattServer(private val context: Context) {

    companion object {
        /** VF3 Smart primary service */
        val SERVICE_UUID: UUID  = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567890")

        /** Tire pressure data — WRITE | WRITE_NO_RESPONSE */
        val TPMS_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567893")

        /** Speed limit in km/h — WRITE | WRITE_NO_RESPONSE  e.g. "60"  "" = unknown */
        val SPEED_LIMIT_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567894")

        /** Car status compact payload — WRITE | WRITE_NO_RESPONSE */
        val CAR_STATUS_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567895")

        private val _tpmsState = MutableStateFlow<TpmsData?>(null)
        val tpmsState: StateFlow<TpmsData?> = _tpmsState.asStateFlow()

        private val _speedLimitState = MutableStateFlow<Int?>(null)
        val speedLimitState: StateFlow<Int?> = _speedLimitState.asStateFlow()

        private val _carStatusState = MutableStateFlow<CarStatus?>(null)
        val carStatusState: StateFlow<CarStatus?> = _carStatusState.asStateFlow()

        private val _bleConnectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val bleConnectionState: StateFlow<BleConnectionState> = _bleConnectionState.asStateFlow()

        private const val TAG = "VF3GattServer"

        sealed class BleConnectionState {
            object Disconnected : BleConnectionState()
            object Connected : BleConnectionState()
        }
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = bluetoothManager?.adapter

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start() {
        val bt = adapter ?: run { Log.e(TAG, "No Bluetooth adapter"); return }
        if (!bt.isEnabled) { Log.w(TAG, "Bluetooth disabled"); return }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE not supported"); return
        }
        if (gattServer != null) { Log.d(TAG, "Already running"); return }
        openGattServer()
        startAdvertising()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        gattServer?.close()
        gattServer = null
        advertiser = null
        _tpmsState.value = null
        _speedLimitState.value = null
        _carStatusState.value = null
        _bleConnectionState.value = BleConnectionState.Disconnected
        Log.d(TAG, "Stopped")
    }

    // ── GATT server setup ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun openGattServer() {
        val server = bluetoothManager?.openGattServer(context, serverCallback)
            ?: run { Log.e(TAG, "Failed to open GATT server"); return }

        fun writeChar(uuid: UUID) = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        ).also {
            it.addCharacteristic(writeChar(TPMS_CHAR_UUID))
            it.addCharacteristic(writeChar(SPEED_LIMIT_CHAR_UUID))
            it.addCharacteristic(writeChar(CAR_STATUS_CHAR_UUID))
        }

        server.addService(service)
        gattServer = server
        Log.d(TAG, "GATT server opened (tpms + speed_limit + car_status)")
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        advertiser = adapter?.bluetoothLeAdvertiser
            ?: run { Log.w(TAG, "LE advertising not supported"); return }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: errorCode=$errorCode")
        }
    }

    // ── GATT callbacks ────────────────────────────────────────────────────────

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "Connection: ${device.address} → state=$newState")
            _bleConnectionState.value = when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> BleConnectionState.Connected
                BluetoothProfile.STATE_DISCONNECTED -> BleConnectionState.Disconnected
                else -> _bleConnectionState.value
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            val payload = value.toString(Charsets.UTF_8).trim()
            when (characteristic.uuid) {
                TPMS_CHAR_UUID        -> { Log.d(TAG, "TPMS:  \"$payload\""); _tpmsState.value = parseTpms(payload) }
                SPEED_LIMIT_CHAR_UUID -> { Log.d(TAG, "Speed: \"$payload\""); _speedLimitState.value = payload.toIntOrNull() }
                CAR_STATUS_CHAR_UUID  -> { Log.d(TAG, "CarStatus: \"$payload\""); _carStatusState.value = parseCarStatus(payload) }
            }
        }
    }

    // ── Payload parsers ───────────────────────────────────────────────────────

    /**
     * Parses compact pipe-delimited car status string written by ESP32 ble_client.cpp.
     *
     * Format (version 1):
     * 1|sensors|doors|windows|seats|lights|proximity|controls|charging|lock_state|wca,wcr|lr|night
     *
     * sensors   : brake,steering,batt,gear
     * doors     : fl,fr,trunk,locked
     * windows   : left_state,right_state
     * seats     : flo,fro,flb,frb
     * lights    : demi,normal
     * proximity : rl,rr
     * controls  : brake_p,acc_pwr,cameras,car_lock,car_unlock,dashcam,odo_screen,armrest
     */
    private fun parseCarStatus(payload: String): CarStatus? {
        return try {
            val p = payload.split('|')
            if (p.size < 13) return null

            val sens = p[1].split(',')
            val door = p[2].split(',')
            val win  = p[3].split(',')
            val seat = p[4].split(',')
            val lgt  = p[5].split(',')
            val prox = p[6].split(',')
            val ctrl = p[7].split(',')
            val charging   = p[8].toIntOrNull() ?: 0
            val lockState  = p[9]
            val wcParts    = p[10].split(',')
            val lr         = p[11].trim() == "1"
            val isNight    = p[12].trim() == "1"

            CarStatus(
                sensors = Sensors(
                    brake          = sens.getOrNull(0)?.toIntOrNull() ?: 0,
                    steeringAngle  = sens.getOrNull(1)?.toIntOrNull() ?: 0,
                    batteryVoltage = sens.getOrNull(2) ?: "0.00",
                    gearDrive      = sens.getOrNull(3)?.toIntOrNull() ?: 0
                ),
                doors = Doors(
                    frontLeft  = door.getOrNull(0)?.toIntOrNull() ?: 0,
                    frontRight = door.getOrNull(1)?.toIntOrNull() ?: 0,
                    trunk      = door.getOrNull(2)?.toIntOrNull() ?: 0,
                    locked     = door.getOrNull(3)?.toIntOrNull() ?: 0
                ),
                windows = Windows(
                    leftState  = win.getOrNull(0)?.toIntOrNull() ?: 0,
                    rightState = win.getOrNull(1)?.toIntOrNull() ?: 0
                ),
                seats = Seats(
                    frontLeftOccupied  = seat.getOrNull(0)?.toIntOrNull() ?: 0,
                    frontRightOccupied = seat.getOrNull(1)?.toIntOrNull() ?: 0,
                    frontLeftSeatbelt  = seat.getOrNull(2)?.toIntOrNull() ?: 0,
                    frontRightSeatbelt = seat.getOrNull(3)?.toIntOrNull() ?: 0
                ),
                lights = Lights(
                    demiLight   = lgt.getOrNull(0)?.toIntOrNull() ?: 0,
                    normalLight = lgt.getOrNull(1)?.toIntOrNull() ?: 0
                ),
                proximity = Proximity(
                    rearLeft  = prox.getOrNull(0)?.toIntOrNull() ?: 0,
                    rearRight = prox.getOrNull(1)?.toIntOrNull() ?: 0
                ),
                controls = Controls(
                    brakePressed    = ctrl.getOrNull(0)?.toIntOrNull() ?: 0,
                    accessoryPower  = ctrl.getOrNull(1)?.toIntOrNull() ?: 0,
                    insideCameras   = ctrl.getOrNull(2)?.toIntOrNull() ?: 0,
                    carLock         = ctrl.getOrNull(3)?.toIntOrNull() ?: 0,
                    carUnlock       = ctrl.getOrNull(4)?.toIntOrNull() ?: 0,
                    dashcam         = ctrl.getOrNull(5)?.toIntOrNull() ?: 0,
                    odoScreen       = ctrl.getOrNull(6)?.toIntOrNull() ?: 0,
                    armrest         = ctrl.getOrNull(7)?.toIntOrNull() ?: 0
                ),
                chargingStatus       = charging,
                carLockState         = lockState,
                windowCloseActive    = wcParts.getOrNull(0)?.trim() == "1",
                windowCloseRemainingMs = wcParts.getOrNull(1)?.toLongOrNull() ?: 0L,
                lightReminderEnabled = lr,
                time = TimeInfo(
                    synced      = true,
                    currentTime = "",
                    bootTime    = "",
                    isNight     = isNight
                ),
                tpms = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseCarStatus failed: ${e.message}")
            null
        }
    }

    /**
     * "FL_KPA,FL_TEMP,FL_ALARM|FR_...|RL_...|RR_..."
     * e.g. "225.5,28,0|227.0,29,0|220.0,27,0|221.5,28,1"
     */
    private fun parseTpms(payload: String): TpmsData? {
        if (payload.isBlank()) return null
        val tires = payload.split('|')
        if (tires.size < 4) return null
        fun parseTire(s: String): TpmsTire? {
            val f = s.split(',')
            val kpa   = f.getOrNull(0)?.toFloatOrNull() ?: return null
            val temp  = f.getOrNull(1)?.toIntOrNull() ?: 0
            val alarm = f.getOrNull(2)?.trim() == "1"
            return TpmsTire(valid = true, stale = false, pressureKpa = kpa,
                tempC = temp, batteryOk = true, alarm = alarm)
        }
        return TpmsData(
            fl = parseTire(tires[0]) ?: return null,
            fr = parseTire(tires[1]) ?: return null,
            rl = parseTire(tires[2]) ?: return null,
            rr = parseTire(tires[3]) ?: return null
        )
    }
}
