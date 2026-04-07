package com.vinfast.vf3smart.data.model

import com.google.gson.annotations.SerializedName

/**
 * Generic API response wrapper
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: T? = null
)

/**
 * Lock/unlock response from POST /car/lock and POST /car/unlock
 */
data class LockResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("car_lock")
    val carLock: Int,

    @SerializedName("car_unlock")
    val carUnlock: Int
)

/**
 * Window control response from POST /car/windows/
 */
data class WindowResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("window_close_active")
    val windowCloseActive: Boolean? = null,

    @SerializedName("duration_ms")
    val durationMs: Long? = null,

    @SerializedName("side")
    val side: String? = null,

    @SerializedName("state")
    val state: String? = null
)

/**
 * Generic control response for accessory power, cameras, etc.
 */
data class ControlResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("accessory_power")
    val accessoryPower: Int? = null,

    @SerializedName("inside_cameras")
    val insideCameras: Int? = null,

    @SerializedName("light_reminder_enabled")
    val lightReminderEnabled: Boolean? = null
)

/**
 * Buzzer control response
 */
data class BuzzerResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String
)

/**
 * Charger unlock response
 */
data class ChargerResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String
)

/**
 * TPMS calibration — GET /tpms/calibrate and POST /tpms/calibrate responses
 */
data class TpmsSensorInfo(
    @SerializedName("id")      val id: String,
    @SerializedName("learned") val learned: Boolean
)

data class TpmsSensorAssignments(
    @SerializedName("fl") val fl: TpmsSensorInfo,
    @SerializedName("fr") val fr: TpmsSensorInfo,
    @SerializedName("rl") val rl: TpmsSensorInfo,
    @SerializedName("rr") val rr: TpmsSensorInfo
)

data class TpmsCalibrationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("sensors") val sensors: TpmsSensorAssignments? = null
)

/**
 * Error response (401, 404, 500, etc.)
 */
data class ErrorResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String
)
