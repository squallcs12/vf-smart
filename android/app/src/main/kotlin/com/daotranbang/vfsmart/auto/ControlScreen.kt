package com.daotranbang.vfsmart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.repository.VF3Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto control screen
 *
 * Displays car control buttons as the main interface.
 * Lock/unlock, window controls, and status information.
 */
class ControlScreen(
    carContext: CarContext,
    private val repository: VF3Repository
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentStatus: CarStatus? = null

    init {
        // Observe lifecycle and connect/disconnect WebSocket
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // Connect WebSocket for real-time updates
                repository.connectWebSocket()

                // Collect status updates and invalidate screen
                scope.launch {
                    repository.carStatus.collectLatest { status ->
                        currentStatus = status
                        invalidate() // Refresh UI
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // Disconnect WebSocket when screen destroyed
                repository.disconnectWebSocket()
                scope.cancel()
            }
        })
    }

    /**
     * Build the Android Auto template
     * Uses ListTemplate to display control buttons
     */
    override fun onGetTemplate(): Template {
        val status = currentStatus

        return if (status != null) {
            buildControlsTemplate(status)
        } else {
            buildLoadingTemplate()
        }
    }

    /**
     * Build controls template with action buttons
     */
    private fun buildControlsTemplate(status: CarStatus): Template {
        val listBuilder = ItemList.Builder()

        val isLocked = status.carLockState == "locked"
        val windowsOpen = status.windows.leftState == 2 || status.windows.rightState == 2

        // Lock/Unlock Control
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (isLocked) "Unlock Car" else "Lock Car")
                .addText(if (isLocked) "Car is currently locked" else "Car is currently unlocked")
                .setImage(
                    CarIcon.Builder(
                        if (isLocked) CarIcon.APP_ICON else CarIcon.ALERT
                    ).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener {
                    scope.launch {
                        if (isLocked) {
                            repository.unlockCar()
                        } else {
                            repository.lockCar()
                        }
                    }
                }
                .build()
        )

        // Window Control
        if (windowsOpen) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Close Windows")
                    .addText("Windows are currently open")
                    .setImage(
                        CarIcon.Builder(CarIcon.ALERT).build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    .setOnClickListener {
                        scope.launch {
                            repository.closeWindows()
                        }
                    }
                    .build()
            )
        } else {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Windows")
                    .addText("Windows are closed")
                    .setImage(
                        CarIcon.Builder(CarIcon.APP_ICON).build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    .build()
            )
        }

        // Status Info Row
        val statusText = buildStatusText(status)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Status")
                .addText(statusText)
                .setImage(
                    CarIcon.Builder(CarIcon.APP_ICON).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .build()
        )

        return ListTemplate.Builder()
            .setTitle("VF3 Smart Controls")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    /**
     * Build status text from car status
     */
    private fun buildStatusText(status: CarStatus): String {
        val parts = mutableListOf<String>()

        // Charging
        if (status.chargingStatus == 1) {
            parts.add("Charging")
        }

        // Lights
        if (status.lights.normalLight == 1 || status.lights.demiLight == 1) {
            parts.add("Lights On")
        }

        // Gear
        if (status.sensors.gearDrive == 1) {
            parts.add("Drive")
        } else {
            parts.add("Park")
        }

        // Doors
        val doorsOpen = status.doors.frontLeft == 1 ||
                status.doors.frontRight == 1 ||
                status.doors.trunk == 1
        if (doorsOpen) {
            parts.add("Doors Open")
        }

        return if (parts.isEmpty()) {
            "All systems normal"
        } else {
            parts.joinToString(" • ")
        }
    }

    /**
     * Build loading template while waiting for status
     */
    private fun buildLoadingTemplate(): Template {
        return MessageTemplate.Builder("Connecting to device...")
            .setTitle("VF3 Smart")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()
    }
}
