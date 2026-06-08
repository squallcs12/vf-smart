package com.daotranbang.vfsmart.data.repository

import android.util.Log
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.navigation.VF3GattServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository - single source of truth for VF3-Smart data.
 *
 * All car communication goes over BLE (the ESP32 has no webserver):
 *  - Inbound status arrives on [VF3GattServer]'s status characteristics and is
 *    exposed here as [carStatus] / [connectionState].
 *  - Outbound commands are pushed via [VF3GattServer.sendCommand] over the
 *    COMMAND notify characteristic. Commands are fire-and-forget: a success
 *    result means the notification was dispatched, not that the car acted.
 */
@Singleton
class VF3Repository @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "VF3Repository"
    }

    // Real-time status from BLE
    val carStatus: StateFlow<CarStatus?> = VF3GattServer.carStatusState
    val connectionState: StateFlow<VF3GattServer.BleConnectionState> =
        VF3GattServer.bleConnectionState

    // ── Commands ───────────────────────────────────────────────────────────────

    /** Lock the car */
    suspend fun lockCar(): Result<Unit> = sendCommand("lock")

    /** Unlock the car */
    suspend fun unlockCar(): Result<Unit> = sendCommand("unlock")

    /** Toggle accessory power */
    suspend fun toggleAccessoryPower(): Result<Unit> = sendCommand("acc:toggle")

    /** Set accessory power on/off */
    suspend fun setAccessoryPower(on: Boolean): Result<Unit> =
        sendCommand(if (on) "acc:on" else "acc:off")

    /** Toggle inside cameras */
    suspend fun toggleInsideCameras(): Result<Unit> = sendCommand("cameras:toggle")

    /** Set inside cameras on/off */
    suspend fun setInsideCameras(on: Boolean): Result<Unit> =
        sendCommand(if (on) "cameras:on" else "cameras:off")

    /** Start closing windows (30-second timer) */
    suspend fun closeWindows(): Result<Unit> = sendCommand("windows:close")

    /** Stop window operation immediately */
    suspend fun stopWindows(): Result<Unit> = sendCommand("windows:stop")

    /**
     * Control window down operation
     * @param side "left", "right", or "both"
     * @param on true to roll down, false to stop
     */
    suspend fun controlWindowDown(side: String, on: Boolean): Result<Unit> =
        sendCommand("window:down,$side,${if (on) "on" else "off"}")

    /**
     * Control window up operation
     * @param side "left", "right", or "both"
     * @param on true to roll up, false to stop
     */
    suspend fun controlWindowUp(side: String, on: Boolean): Result<Unit> =
        sendCommand("window:up,$side,${if (on) "on" else "off"}")

    /** Beep horn/buzzer for [durationMs] milliseconds */
    suspend fun beepHorn(durationMs: Int = 500): Result<Unit> =
        sendCommand("buzzer:beep,$durationMs")

    /** Turn buzzer on/off */
    suspend fun setBuzzer(on: Boolean): Result<Unit> =
        sendCommand(if (on) "buzzer:on" else "buzzer:off")

    /** Toggle light reminder */
    suspend fun toggleLightReminder(): Result<Unit> = sendCommand("light-reminder:toggle")

    /** Enable/disable light reminder */
    suspend fun setLightReminder(enabled: Boolean): Result<Unit> =
        sendCommand(if (enabled) "light-reminder:on" else "light-reminder:off")

    /** Unlock charger port */
    suspend fun unlockCharger(): Result<Unit> = sendCommand("charger-unlock")

    /** Open side mirrors */
    suspend fun openSideMirrors(): Result<Unit> = sendCommand("mirrors:open")

    /** Close side mirrors */
    suspend fun closeSideMirrors(): Result<Unit> = sendCommand("mirrors:close")

    /** Toggle ODO screen */
    suspend fun toggleOdoScreen(): Result<Unit> = sendCommand("odo:toggle")

    /** Set ODO screen on/off */
    suspend fun setOdoScreen(on: Boolean): Result<Unit> =
        sendCommand(if (on) "odo:on" else "odo:off")

    /** Toggle armrest */
    suspend fun toggleArmrest(): Result<Unit> = sendCommand("armrest:toggle")

    /** Set armrest on/off */
    suspend fun setArmrest(on: Boolean): Result<Unit> =
        sendCommand(if (on) "armrest:on" else "armrest:off")

    /** Toggle dashcam */
    suspend fun toggleDashcam(): Result<Unit> = sendCommand("dashcam:toggle")

    /** Set dashcam on/off */
    suspend fun setDashcam(on: Boolean): Result<Unit> =
        sendCommand(if (on) "dashcam:on" else "dashcam:off")

    /** Reset all TPMS sensor assignments (drive near each tire to re-learn) */
    suspend fun tpmsReset(): Result<Unit> = sendCommand("tpms:reset")

    /** Swap two TPMS tire positions (posA/posB: "fl", "fr", "rl", "rr") */
    suspend fun tpmsSwap(posA: String, posB: String): Result<Unit> =
        sendCommand("tpms:swap,$posA,$posB")

    // ── Transport ────────────────────────────────────────────────────────────────

    /**
     * Dispatch a command over the BLE COMMAND characteristic.
     * Fails if no car is connected/subscribed.
     */
    private suspend fun sendCommand(command: String): Result<Unit> =
        withContext(ioDispatcher) {
            if (VF3GattServer.sendCommand(command)) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to send command: $command")
                Result.failure(Exception("Car not connected"))
            }
        }
}
