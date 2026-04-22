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
import com.daotranbang.vfsmart.data.model.TpmsData
import com.daotranbang.vfsmart.data.model.TpmsTire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Single BLE GATT server with three write characteristics:
 *
 *   NAV_CHAR  — turn-by-turn navigation  →  [navigationState]
 *   GPS_CHAR  — GPS position + speed     →  [gpsState]
 *   TPMS_CHAR — tire pressure data       →  [tpmsState]
 *
 * The phone advertises as a peripheral. The client (ESP32) connects and writes.
 *
 * ── Wire formats ──────────────────────────────────────────────────────────────
 *
 * NAV_CHAR   "DIRECTION|DISTANCE"
 *   e.g.  "LEFT|300 m"    "STRAIGHT|1.2 km"    "" = inactive
 *   DIRECTION tokens: LEFT | RIGHT | U_TURN | ROUNDABOUT | STRAIGHT
 *
 * GPS_CHAR   "LAT|LON|SPEED_MS|BEARING"
 *   e.g.  "10.7769|106.7009|13.89|270.0"
 *   BEARING is optional.  "" = no fix.
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

        /** Navigation data — WRITE | WRITE_NO_RESPONSE */
        val NAV_CHAR_UUID: UUID  = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567891")

        /** GPS position + speed — WRITE | WRITE_NO_RESPONSE */
        val GPS_CHAR_UUID: UUID  = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567892")

        /** Tire pressure data — WRITE | WRITE_NO_RESPONSE */
        val TPMS_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567893")

        /** Speed limit in km/h — WRITE | WRITE_NO_RESPONSE  e.g. "60"  "" = unknown */
        val SPEED_LIMIT_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567894")

        private val _navigationState = MutableStateFlow(NavigationState())
        val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

        private val _gpsState = MutableStateFlow(GpsState())
        val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

        private val _tpmsState = MutableStateFlow<TpmsData?>(null)
        val tpmsState: StateFlow<TpmsData?> = _tpmsState.asStateFlow()

        private val _speedLimitState = MutableStateFlow<Int?>(null)
        val speedLimitState: StateFlow<Int?> = _speedLimitState.asStateFlow()

        private const val TAG = "VF3GattServer"
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
        _navigationState.value = NavigationState()
        _gpsState.value = GpsState()
        _tpmsState.value = null
        _speedLimitState.value = null
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
            it.addCharacteristic(writeChar(NAV_CHAR_UUID))
            it.addCharacteristic(writeChar(GPS_CHAR_UUID))
            it.addCharacteristic(writeChar(TPMS_CHAR_UUID))
            it.addCharacteristic(writeChar(SPEED_LIMIT_CHAR_UUID))
        }

        server.addService(service)
        gattServer = server
        Log.d(TAG, "GATT server opened (nav + gps + tpms)")
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
                NAV_CHAR_UUID         -> { Log.d(TAG, "Nav:   \"$payload\""); _navigationState.value = parseNav(payload) }
                GPS_CHAR_UUID         -> { Log.d(TAG, "GPS:   \"$payload\""); _gpsState.value = parseGps(payload) }
                TPMS_CHAR_UUID        -> { Log.d(TAG, "TPMS:  \"$payload\""); _tpmsState.value = parseTpms(payload) }
                SPEED_LIMIT_CHAR_UUID -> { Log.d(TAG, "Speed: \"$payload\""); _speedLimitState.value = payload.toIntOrNull() }
            }
        }
    }

    // ── Payload parsers ───────────────────────────────────────────────────────

    /** "DIRECTION|DISTANCE"  e.g. "LEFT|300 m" */
    private fun parseNav(payload: String): NavigationState {
        if (payload.isBlank()) return NavigationState()
        val sep      = payload.indexOf('|')
        val dirToken = if (sep >= 0) payload.substring(0, sep).trim().uppercase()
                       else payload.trim().uppercase()
        val distance = if (sep >= 0) payload.substring(sep + 1).trim() else ""
        val direction = when (dirToken) {
            "LEFT"       -> NavigationState.Direction.LEFT
            "RIGHT"      -> NavigationState.Direction.RIGHT
            "U_TURN"     -> NavigationState.Direction.U_TURN
            "ROUNDABOUT" -> NavigationState.Direction.ROUNDABOUT
            else         -> NavigationState.Direction.STRAIGHT
        }
        return NavigationState(
            isActive  = distance.isNotBlank(),
            maneuver  = navLabel(direction),
            distance  = distance,
            direction = direction
        )
    }

    private fun navLabel(d: NavigationState.Direction): String = when (d) {
        NavigationState.Direction.LEFT       -> "TURN LEFT"
        NavigationState.Direction.RIGHT      -> "TURN RIGHT"
        NavigationState.Direction.U_TURN     -> "U-TURN"
        NavigationState.Direction.ROUNDABOUT -> "ROUNDABOUT"
        NavigationState.Direction.STRAIGHT   -> "CONTINUE"
    }

    /** "LAT|LON|SPEED_MS|BEARING"  e.g. "10.7769|106.7009|13.89|270.0" */
    private fun parseGps(payload: String): GpsState {
        if (payload.isBlank()) return GpsState()
        val parts   = payload.split('|')
        val lat     = parts.getOrNull(0)?.toDoubleOrNull() ?: return GpsState()
        val lon     = parts.getOrNull(1)?.toDoubleOrNull() ?: return GpsState()
        val speedMs = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        val bearing = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
        return GpsState(isActive = true, latitude = lat, longitude = lon,
            speedMs = speedMs, bearing = bearing)
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
