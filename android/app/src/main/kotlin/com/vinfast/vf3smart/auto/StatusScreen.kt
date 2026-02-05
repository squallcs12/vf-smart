package com.vinfast.vf3smart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Android Auto status and control screen
 *
 * Displays real-time car status using GridTemplate with control actions.
 * Provides lock/unlock and window controls via ActionStrip buttons.
 *
 * Note: Cannot use @AndroidEntryPoint as Screen is not a supported Hilt entry point.
 * Uses EntryPointAccessors to manually retrieve repository from Application.
 */
class StatusScreen(
    carContext: CarContext
) : Screen(carContext) {

    /**
     * Hilt EntryPoint for accessing repository from non-Hilt components
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StatusScreenEntryPoint {
        fun repository(): VF3Repository
    }

    private val repository: VF3Repository by lazy {
        val appContext = carContext.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            StatusScreenEntryPoint::class.java
        )
        entryPoint.repository()
    }

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
     * Uses GridTemplate to display status cards
     */
    override fun onGetTemplate(): Template {
        val status = currentStatus

        return if (status != null) {
            buildStatusTemplate(status)
        } else {
            buildLoadingTemplate()
        }
    }

    /**
     * Build status display template with car information
     */
    private fun buildStatusTemplate(status: CarStatus): Template {
        val gridItemListBuilder = ItemList.Builder()

        // Lock status
        val isLocked = status.carLockState == "locked"
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (isLocked) "Locked" else "Unlocked")
                .setText("Car Lock")
                .setImage(
                    CarIcon.Builder(
                        if (isLocked) CarIcon.APP_ICON else CarIcon.ALERT
                    ).build()
                )
                .build()
        )

        // Window status
        val windowsOpen = status.windows.leftState == 2 || status.windows.rightState == 2
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (windowsOpen) "Open" else "Closed")
                .setText("Windows")
                .setImage(
                    CarIcon.Builder(
                        if (windowsOpen) CarIcon.ALERT else CarIcon.APP_ICON
                    ).build()
                )
                .build()
        )

        // Charging status
        val isCharging = status.chargingStatus == 1
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (isCharging) "Charging" else "Not Charging")
                .setText("Battery")
                .setImage(CarIcon.Builder(CarIcon.APP_ICON).build())
                .build()
        )

        // Lights status
        val lightsOn = status.lights.normalLight == 1 || status.lights.demiLight == 1
        val isNight = status.time?.isNight == true
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (lightsOn) "On" else "Off")
                .setText("Lights")
                .setImage(
                    CarIcon.Builder(
                        if (isNight && !lightsOn) CarIcon.ALERT else CarIcon.APP_ICON
                    ).build()
                )
                .build()
        )

        // Gear status
        val inDrive = status.sensors.gearDrive == 1
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (inDrive) "Drive" else "Park/Other")
                .setText("Gear")
                .setImage(CarIcon.Builder(CarIcon.APP_ICON).build())
                .build()
        )

        // Doors status
        val doorsOpen = status.doors.frontLeft == 1 ||
                status.doors.frontRight == 1 ||
                status.doors.trunk == 1
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (doorsOpen) "Open" else "Closed")
                .setText("Doors")
                .setImage(
                    CarIcon.Builder(
                        if (doorsOpen) CarIcon.ALERT else CarIcon.APP_ICON
                    ).build()
                )
                .build()
        )

        return GridTemplate.Builder()
            .setTitle("VF3 Smart Status")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(gridItemListBuilder.build())
            .setActionStrip(buildControlActions(status))
            .build()
    }

    /**
     * Build control actions for lock/unlock and windows
     */
    private fun buildControlActions(status: CarStatus): ActionStrip {
        val builder = ActionStrip.Builder()

        // Lock/Unlock button
        val isLocked = status.carLockState == "locked"
        builder.addAction(
            Action.Builder()
                .setTitle(if (isLocked) "Unlock" else "Lock")
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

        // Close windows button (if windows open)
        val windowsOpen = status.windows.leftState == 2 || status.windows.rightState == 2
        if (windowsOpen) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Close Windows")
                    .setOnClickListener {
                        scope.launch {
                            repository.closeWindows()
                        }
                    }
                    .build()
            )
        }

        return builder.build()
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
