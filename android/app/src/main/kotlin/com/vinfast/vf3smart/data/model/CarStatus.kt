package com.vinfast.vf3smart.data.model

import com.google.gson.annotations.SerializedName

/**
 * Main car status data class matching ESP32 JSON structure
 * Received from GET /car/status and WebSocket ws://IP/ws
 */
data class CarStatus(
    @SerializedName("sensors")
    val sensors: Sensors,

    @SerializedName("doors")
    val doors: Doors,

    @SerializedName("windows")
    val windows: Windows,

    @SerializedName("seats")
    val seats: Seats,

    @SerializedName("lights")
    val lights: Lights,

    @SerializedName("proximity")
    val proximity: Proximity,

    @SerializedName("controls")
    val controls: Controls,

    @SerializedName("charging_status")
    val chargingStatus: Int,

    @SerializedName("car_lock_state")
    val carLockState: String,

    @SerializedName("window_close_active")
    val windowCloseActive: Boolean,

    @SerializedName("window_close_remaining_ms")
    val windowCloseRemainingMs: Long,

    @SerializedName("light_reminder_enabled")
    val lightReminderEnabled: Boolean,

    @SerializedName("time")
    val time: TimeInfo?,

    @SerializedName("tpms")
    val tpms: TpmsData?
)

data class Sensors(
    @SerializedName("brake")
    val brake: Int,

    @SerializedName("steering_angle")
    val steeringAngle: Int,

    @SerializedName("battery_voltage")
    val batteryVoltage: String = "0.0",

    @SerializedName("gear_drive")
    val gearDrive: Int
)

data class Doors(
    @SerializedName("front_left")
    val frontLeft: Int,

    @SerializedName("front_right")
    val frontRight: Int,

    @SerializedName("trunk")
    val trunk: Int,

    @SerializedName("locked")
    val locked: Int
) {
    val anyOpen: Boolean
        get() = frontLeft == 1 || frontRight == 1 || trunk == 1
}

data class Windows(
    @SerializedName("left_state")
    val leftState: Int,  // 0=unknown, 1=closed, 2=open

    @SerializedName("right_state")
    val rightState: Int
) {
    val anyOpen: Boolean
        get() = leftState == 2 || rightState == 2
}

data class Seats(
    @SerializedName("front_left_occupied")
    val frontLeftOccupied: Int,

    @SerializedName("front_right_occupied")
    val frontRightOccupied: Int,

    @SerializedName("front_left_seatbelt")
    val frontLeftSeatbelt: Int,

    @SerializedName("front_right_seatbelt")
    val frontRightSeatbelt: Int
)

data class Lights(
    @SerializedName("demi_light")
    val demiLight: Int,

    @SerializedName("normal_light")
    val normalLight: Int
) {
    val anyOn: Boolean
        get() = demiLight == 1 || normalLight == 1
}

data class Proximity(
    @SerializedName("rear_left")
    val rearLeft: Int,

    @SerializedName("rear_right")
    val rearRight: Int
)

data class Controls(
    @SerializedName("brake_pressed")
    val brakePressed: Int,

    @SerializedName("accessory_power")
    val accessoryPower: Int,

    @SerializedName("inside_cameras")
    val insideCameras: Int,

    @SerializedName("car_lock")
    val carLock: Int,

    @SerializedName("car_unlock")
    val carUnlock: Int,

    @SerializedName("dashcam")
    val dashcam: Int,

    @SerializedName("odo_screen")
    val odoScreen: Int,

    @SerializedName("armrest")
    val armrest: Int
)

data class TpmsTire(
    @SerializedName("valid")        val valid: Boolean,
    @SerializedName("stale")        val stale: Boolean,
    @SerializedName("pressure_kpa") val pressureKpa: Float,
    @SerializedName("temp_c")       val tempC: Int,
    @SerializedName("battery_ok")   val batteryOk: Boolean,
    @SerializedName("alarm")        val alarm: Boolean
)

data class TpmsData(
    @SerializedName("fl") val fl: TpmsTire,
    @SerializedName("fr") val fr: TpmsTire,
    @SerializedName("rl") val rl: TpmsTire,
    @SerializedName("rr") val rr: TpmsTire
) {
    val anyAlarm: Boolean get() = fl.alarm || fr.alarm || rl.alarm || rr.alarm
    val anyLow: Boolean   get() = listOf(fl, fr, rl, rr)
        .filter { it.valid && !it.stale }
        .any    { it.pressureKpa in 80f..179f }
    val allValid: Boolean get() = fl.valid && fr.valid && rl.valid && rr.valid
}

data class TimeInfo(
    @SerializedName("synced")
    val synced: Boolean,

    @SerializedName("current_time")
    val currentTime: String,

    @SerializedName("boot_time")
    val bootTime: String,

    @SerializedName("is_night")
    val isNight: Boolean
)
