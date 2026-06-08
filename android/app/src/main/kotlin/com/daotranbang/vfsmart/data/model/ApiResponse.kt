package com.daotranbang.vfsmart.data.model

/**
 * TPMS sensor-ID assignments shown on the calibration screen.
 *
 * With the ESP32 webserver gone there is no read-back channel, so these are
 * currently informational placeholders — calibration commands (reset/swap) are
 * sent fire-and-forget over BLE and the live pressures come from [TpmsData].
 */
data class TpmsSensorInfo(
    val id: String,
    val learned: Boolean
)

data class TpmsSensorAssignments(
    val fl: TpmsSensorInfo,
    val fr: TpmsSensorInfo,
    val rl: TpmsSensorInfo,
    val rr: TpmsSensorInfo
)
