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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE GATT server that receives turn-by-turn navigation data written by a
 * BLE client (e.g. ESP32 or a companion phone).
 *
 * The phone acts as a **peripheral** (GATT server + advertiser).
 * The client connects and writes to [NAV_CHAR_UUID].
 *
 * Wire format (UTF-8 string):
 *   "DIRECTION|DISTANCE"  — e.g. "LEFT|300 m", "STRAIGHT|1.2 km"
 *   ""                    — navigation inactive (clears display)
 *
 * Direction tokens: LEFT | RIGHT | U_TURN | ROUNDABOUT | STRAIGHT
 */
class NavigationGattServer(private val context: Context) {

    companion object {
        /** VF3 Smart Navigation Service */
        val SERVICE_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567890")

        /** Navigation data characteristic — WRITE | WRITE_NO_RESPONSE */
        val NAV_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567891")

        private val _navigationState = MutableStateFlow(NavigationState())
        val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

        private const val TAG = "NavGattServer"
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
        if (!bt.isEnabled) { Log.w(TAG, "Bluetooth disabled — server not started"); return }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE not supported on this device"); return
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
        Log.d(TAG, "GATT server stopped")
    }

    // ── GATT server setup ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun openGattServer() {
        val server = bluetoothManager?.openGattServer(context, serverCallback)
            ?: run { Log.e(TAG, "Failed to open GATT server"); return }

        val characteristic = BluetoothGattCharacteristic(
            NAV_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        service.addCharacteristic(characteristic)
        server.addService(service)

        gattServer = server
        Log.d(TAG, "GATT server opened")
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        advertiser = adapter?.bluetoothLeAdvertiser
            ?: run { Log.w(TAG, "LE advertising not supported"); return }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)                                      // advertise indefinitely
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
            Log.d(TAG, "BLE advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: errorCode=$errorCode")
        }
    }

    // ── GATT server callbacks ─────────────────────────────────────────────────

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(
            device: BluetoothDevice, status: Int, newState: Int
        ) {
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
            if (characteristic.uuid != NAV_CHAR_UUID) return

            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                )
            }

            val payload = value.toString(Charsets.UTF_8).trim()
            Log.d(TAG, "Nav payload: \"$payload\"")
            _navigationState.value = parsePayload(payload)
        }
    }

    // ── Payload parsing ───────────────────────────────────────────────────────

    /**
     * Parse "DIRECTION|DISTANCE" into a [NavigationState].
     * Empty payload → inactive state.
     */
    private fun parsePayload(payload: String): NavigationState {
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
            maneuver  = directionLabel(direction),
            distance  = distance,
            direction = direction
        )
    }

    private fun directionLabel(d: NavigationState.Direction): String = when (d) {
        NavigationState.Direction.LEFT       -> "TURN LEFT"
        NavigationState.Direction.RIGHT      -> "TURN RIGHT"
        NavigationState.Direction.U_TURN     -> "U-TURN"
        NavigationState.Direction.ROUNDABOUT -> "ROUNDABOUT"
        NavigationState.Direction.STRAIGHT   -> "CONTINUE"
    }
}
