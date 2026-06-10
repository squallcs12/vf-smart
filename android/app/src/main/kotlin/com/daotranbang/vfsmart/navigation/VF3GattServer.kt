package com.daotranbang.vfsmart.navigation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.model.Controls
import com.daotranbang.vfsmart.data.model.Doors
import com.daotranbang.vfsmart.data.model.Lights
import com.daotranbang.vfsmart.data.model.Proximity
import com.daotranbang.vfsmart.data.model.Seats
import com.daotranbang.vfsmart.data.model.Sensors
import com.daotranbang.vfsmart.data.model.TimeInfo
import com.daotranbang.vfsmart.data.model.TpmsData
import com.daotranbang.vfsmart.data.model.TpmsTire
import com.daotranbang.vfsmart.data.model.Windows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE GATT server — the sole transport between phone and car (no ESP32 webserver).
 *
 * Inbound (ESP32 → phone), three write characteristics:
 *   TPMS_CHAR        — tire pressure data        →  [tpmsState]
 *   SPEED_LIMIT_CHAR — speed limit km/h          →  [speedLimitState]
 *   CAR_STATUS_CHAR  — delta car status updates  →  [carStatusState]
 *
 * Outbound (phone → ESP32), one notify characteristic:
 *   COMMAND_CHAR     — control commands           ←  [sendCommand]
 *
 * The phone advertises as a peripheral. The client (ESP32) connects, writes the
 * status characteristics, and subscribes to COMMAND_CHAR to receive commands.
 *
 * ── Wire formats ──────────────────────────────────────────────────────────────
 *
 * TPMS_CHAR  "FL_KPA,FL_TEMP,FL_ALARM|FR_KPA,FR_TEMP,FR_ALARM|RL_...|RR_..."
 *
 * CAR_STATUS_CHAR — delta protocol:
 *   Full (on connect, 60 s heartbeat):
 *     "F|S:<s>|D:<d>|W:<w>|E:<e>|L:<l>|P:<p>|C:<c>|X:<x>"
 *   Delta (only changed groups):
 *     "U|S:<s>|L:<l>|..."
 *
 *   Group formats:
 *     S  brake,steering,voltage,gear
 *     D  fl,fr,trunk,locked
 *     W  left_state,right_state
 *     E  seat_flo,seat_fro,seatbelt_flo,seatbelt_fro
 *     L  demi,normal
 *     P  rear_l,rear_r
 *     C  brake_pressed,acc_power,cameras,car_lock,car_unlock
 *     X  charging,lock_state(0/1),wca,wcr_secs,lr,is_night
 *
 * COMMAND_CHAR — UTF-8 command strings, "verb[:args]" with comma-separated args:
 *     "lock"                 "unlock"
 *     "acc:on" / "off" / "toggle"          "cameras:toggle"
 *     "windows:close" / "stop"
 *     "window:down,left,on"  ("up"/"down", side, "on"/"off")
 *     "buzzer:beep,500" / "on" / "off"
 *     "light-reminder:toggle"              "charger-unlock"
 *     "mirrors:open" / "close"
 *     "odo:toggle"  "armrest:toggle"  "dashcam:toggle"
 *     "tpms:reset"  "tpms:swap,fl,fr"
 */
class VF3GattServer(private val context: Context) {

    // ── BLE connection state ───────────────────────────────────────────────
    sealed class BleConnectionState {
        object Disconnected : BleConnectionState()
        object Connected : BleConnectionState()
    }

    companion object {
        /** VF3 Smart primary service */
        val SERVICE_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567890")

        /** Tire pressure data — WRITE | WRITE_NO_RESPONSE */
        val TPMS_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567893")

        /** Speed limit in km/h — WRITE | WRITE_NO_RESPONSE */
        val SPEED_LIMIT_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567894")

        /** Car status delta updates — WRITE | WRITE_NO_RESPONSE */
        val CAR_STATUS_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567895")

        /** Control commands phone → ESP32 — NOTIFY */
        val COMMAND_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567896")

        /** Client Characteristic Configuration Descriptor (standard 0x2902) */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // ── Public state flows ─────────────────────────────────────────────────

        private val _tpmsState = MutableStateFlow<TpmsData?>(null)
        val tpmsState: StateFlow<TpmsData?> = _tpmsState.asStateFlow()

        private val _speedLimitState = MutableStateFlow<Int?>(null)
        val speedLimitState: StateFlow<Int?> = _speedLimitState.asStateFlow()

        /** Clears the current speed limit (e.g. user dismissed it on the ODO screen). */
        fun clearSpeedLimit() { _speedLimitState.value = null }

        private val _carStatusState = MutableStateFlow<CarStatus?>(null)
        val carStatusState: StateFlow<CarStatus?> = _carStatusState.asStateFlow()

        private val _bleConnectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val bleConnectionState: StateFlow<BleConnectionState> = _bleConnectionState.asStateFlow()

        private const val TAG = "VF3GattServer"

        /** The running server instance, used to route outbound commands. */
        @Volatile
        private var activeInstance: VF3GattServer? = null

        /**
         * Send a control command to the connected ESP32 via COMMAND_CHAR notify.
         * @return true if the notification was dispatched, false if no server is
         *         running, no client is connected, or the client hasn't subscribed.
         */
        fun sendCommand(command: String): Boolean =
            activeInstance?.notifyCommand(command) ?: run {
                Log.w(TAG, "sendCommand(\"$command\") — no active GATT server")
                false
            }
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = bluetoothManager?.adapter

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    // ── Outbound command state ────────────────────────────────────────────────
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    /** Set once the client writes the CCCD to enable notifications on COMMAND_CHAR. */
    private var commandNotificationsEnabled = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted — skipping start")
            return
        }
        val bt = adapter ?: run { Log.e(TAG, "No Bluetooth adapter"); return }
        if (!bt.isEnabled) { Log.w(TAG, "Bluetooth disabled"); return }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE not supported"); return
        }
        if (gattServer != null) { Log.d(TAG, "Already running"); return }
        openGattServer()
        startAdvertising()
        activeInstance = this
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (activeInstance === this) activeInstance = null
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        gattServer?.close()
        gattServer = null
        advertiser = null
        commandCharacteristic = null
        connectedDevice = null
        commandNotificationsEnabled = false
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

        // Outbound commands: NOTIFY, with a CCCD so the client can subscribe.
        val commandChar = BluetoothGattCharacteristic(
            COMMAND_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or
                        BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }
        commandCharacteristic = commandChar

        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        ).also {
            it.addCharacteristic(writeChar(TPMS_CHAR_UUID))
            it.addCharacteristic(writeChar(SPEED_LIMIT_CHAR_UUID))
            it.addCharacteristic(writeChar(CAR_STATUS_CHAR_UUID))
            it.addCharacteristic(commandChar)
        }

        server.addService(service)
        gattServer = server
        Log.d(TAG, "GATT server opened (tpms + speed_limit + car_status + command)")
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
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _bleConnectionState.value = BleConnectionState.Connected
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address) {
                        connectedDevice = null
                        commandNotificationsEnabled = false
                    }
                    _bleConnectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID &&
                descriptor.characteristic.uuid == COMMAND_CHAR_UUID
            ) {
                commandNotificationsEnabled =
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                Log.d(TAG, "COMMAND_CHAR notifications enabled=$commandNotificationsEnabled")
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
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
                CAR_STATUS_CHAR_UUID  -> { Log.d(TAG, "Status len=${payload.length}"); applyCarStatusPayload(payload) }
            }
        }
    }

    // ── Outbound command notify ───────────────────────────────────────────────

    /**
     * Push a command to the connected client over COMMAND_CHAR.
     * Returns false if there is no connected/subscribed client or the notify fails.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun notifyCommand(command: String): Boolean {
        val server = gattServer ?: return false
        val char = commandCharacteristic ?: return false
        val device = connectedDevice ?: run {
            Log.w(TAG, "notifyCommand(\"$command\") — no connected device")
            return false
        }
        if (!commandNotificationsEnabled) {
            Log.w(TAG, "notifyCommand(\"$command\") — client not subscribed")
            return false
        }
        return try {
            val bytes = command.toByteArray(Charsets.UTF_8)
            // Deprecated setValue + 3-arg notify keeps a single path for minSdk 23.
            char.value = bytes
            val ok = server.notifyCharacteristicChanged(device, char, false)
            Log.d(TAG, "notifyCommand(\"$command\") dispatched=$ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "notifyCommand(\"$command\") failed", e)
            false
        }
    }

    // ── Car status delta parser ───────────────────────────────────────────────

    /**
     * Applies a full ("F|...") or delta ("U|...") car status payload,
     * merging only the changed groups into [_carStatusState].
     */
    private fun applyCarStatusPayload(payload: String) {
        if (payload.isEmpty()) return
        val parts = payload.split('|')
        if (parts.isEmpty()) return

        val cur = _carStatusState.value

        var sensors   = cur?.sensors   ?: Sensors(0, 0, "0.00", 0)
        var doors     = cur?.doors     ?: Doors(0, 0, 0, 0)
        var windows   = cur?.windows   ?: Windows(0, 0)
        var seats     = cur?.seats     ?: Seats(0, 0, 0, 0)
        var lights    = cur?.lights    ?: Lights(0, 0)
        var proximity = cur?.proximity ?: Proximity(0, 0)
        var controls  = cur?.controls  ?: Controls(0, 0, 0, 0, 0, 0, 0, 0)
        var chargingStatus         = cur?.chargingStatus         ?: 0
        var carLockState           = cur?.carLockState           ?: "unlocked"
        var windowCloseActive      = cur?.windowCloseActive      ?: false
        var windowCloseRemainingMs = cur?.windowCloseRemainingMs ?: 0L
        var lightReminderEnabled   = cur?.lightReminderEnabled   ?: true
        var isNight                = cur?.time?.isNight          ?: false
        val tpms                   = cur?.tpms // preserved — arrives via TPMS_CHAR

        for (i in 1 until parts.size) {
            val entry = parts[i]
            val colon = entry.indexOf(':')
            if (colon < 0) continue
            val grp = entry.substring(0, colon)
            val v   = entry.substring(colon + 1).split(',')

            when (grp) {
                "S" -> sensors = Sensors(
                    brake          = v.getOrNull(0)?.toIntOrNull()   ?: sensors.brake,
                    steeringAngle  = v.getOrNull(1)?.toIntOrNull()   ?: sensors.steeringAngle,
                    batteryVoltage = v.getOrNull(2)                  ?: sensors.batteryVoltage,
                    gearDrive      = v.getOrNull(3)?.toIntOrNull()   ?: sensors.gearDrive
                )
                "D" -> doors = Doors(
                    frontLeft  = v.getOrNull(0)?.toIntOrNull() ?: doors.frontLeft,
                    frontRight = v.getOrNull(1)?.toIntOrNull() ?: doors.frontRight,
                    trunk      = v.getOrNull(2)?.toIntOrNull() ?: doors.trunk,
                    locked     = v.getOrNull(3)?.toIntOrNull() ?: doors.locked
                )
                "W" -> windows = Windows(
                    leftState  = v.getOrNull(0)?.toIntOrNull() ?: windows.leftState,
                    rightState = v.getOrNull(1)?.toIntOrNull() ?: windows.rightState
                )
                "E" -> seats = Seats(
                    frontLeftOccupied  = v.getOrNull(0)?.toIntOrNull() ?: seats.frontLeftOccupied,
                    frontRightOccupied = v.getOrNull(1)?.toIntOrNull() ?: seats.frontRightOccupied,
                    frontLeftSeatbelt  = v.getOrNull(2)?.toIntOrNull() ?: seats.frontLeftSeatbelt,
                    frontRightSeatbelt = v.getOrNull(3)?.toIntOrNull() ?: seats.frontRightSeatbelt
                )
                "L" -> lights = Lights(
                    demiLight   = v.getOrNull(0)?.toIntOrNull() ?: lights.demiLight,
                    normalLight = v.getOrNull(1)?.toIntOrNull() ?: lights.normalLight
                )
                "P" -> proximity = Proximity(
                    rearLeft  = v.getOrNull(0)?.toIntOrNull() ?: proximity.rearLeft,
                    rearRight = v.getOrNull(1)?.toIntOrNull() ?: proximity.rearRight
                )
                "C" -> controls = Controls(
                    brakePressed   = v.getOrNull(0)?.toIntOrNull() ?: controls.brakePressed,
                    accessoryPower = v.getOrNull(1)?.toIntOrNull() ?: controls.accessoryPower,
                    insideCameras  = v.getOrNull(2)?.toIntOrNull() ?: controls.insideCameras,
                    carLock        = v.getOrNull(3)?.toIntOrNull() ?: controls.carLock,
                    carUnlock      = v.getOrNull(4)?.toIntOrNull() ?: controls.carUnlock,
                    dashcam        = controls.dashcam,
                    odoScreen      = controls.odoScreen,
                    armrest        = controls.armrest
                )
                "X" -> {
                    chargingStatus         = v.getOrNull(0)?.toIntOrNull() ?: chargingStatus
                    carLockState           = if ((v.getOrNull(1)?.toIntOrNull() ?: 0) == 1) "locked" else "unlocked"
                    windowCloseActive      = (v.getOrNull(2)?.toIntOrNull() ?: 0) == 1
                    windowCloseRemainingMs = (v.getOrNull(3)?.toLongOrNull() ?: 0L) * 1000L
                    lightReminderEnabled   = (v.getOrNull(4)?.toIntOrNull() ?: 1) == 1
                    isNight                = (v.getOrNull(5)?.toIntOrNull() ?: 0) == 1
                }
            }
        }

        _carStatusState.value = CarStatus(
            sensors                = sensors,
            doors                  = doors,
            windows                = windows,
            seats                  = seats,
            lights                 = lights,
            proximity              = proximity,
            controls               = controls,
            chargingStatus         = chargingStatus,
            carLockState           = carLockState,
            windowCloseActive      = windowCloseActive,
            windowCloseRemainingMs = windowCloseRemainingMs,
            lightReminderEnabled   = lightReminderEnabled,
            time                   = TimeInfo(
                synced      = true,
                currentTime = "",
                bootTime    = "",
                isNight     = isNight
            ),
            tpms = tpms
        )
    }

    // ── TPMS payload parser ───────────────────────────────────────────────────

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
