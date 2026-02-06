package com.vinfast.vf3smart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.vinfast.vf3smart.R
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.repository.VF3Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto control screen
 *
 * Displays car control buttons: Lock/Unlock, Close Windows, Inside Camera
 */
class StatusScreen(
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

    override fun onGetTemplate(): Template {
        val status = currentStatus

        return if (status != null) {
            buildControlsTemplate(status)
        } else {
            buildLoadingTemplate()
        }
    }

    private fun buildControlsTemplate(status: CarStatus): Template {
        val gridItemListBuilder = ItemList.Builder()

        val isLocked = status.carLockState == "locked"
        val windowsOpen = status.windows.leftState == 2 || status.windows.rightState == 2
        val accessoryPowerOn = status.controls.accessoryPower == 1

        // Lock/Unlock Control
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (isLocked) "Unlock" else "Lock")
                .setText(if (isLocked) "Car Locked" else "Car Unlocked")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
                        )
                    ).setTint(if (isLocked) CarColor.GREEN else CarColor.RED).build()
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

        // Close Windows Control
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("Windows")
                .setText(if (windowsOpen) "Close Windows" else "Windows Closed")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_windows
                        )
                    ).setTint(CarColor.BLUE).build()
                )
                .setOnClickListener {
                    scope.launch {
                        repository.closeWindows()
                    }
                }
                .build()
        )

        // Turn On Inside Camera
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("Camera")
                .setText("Turn On Camera")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_camera
                        )
                    ).setTint(CarColor.YELLOW).build()
                )
                .setOnClickListener {
                    scope.launch {
                        repository.setInsideCameras(true)
                    }
                }
                .build()
        )

        // Accessory Power Control
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (accessoryPowerOn) "Power Off" else "Power On")
                .setText(if (accessoryPowerOn) "Accessory Power On" else "Accessory Power Off")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_power
                        )
                    ).setTint(if (accessoryPowerOn) CarColor.GREEN else CarColor.RED).build()
                )
                .setOnClickListener {
                    scope.launch {
                        repository.toggleAccessoryPower()
                    }
                }
                .build()
        )

        return GridTemplate.Builder()
            .setTitle("VF3 Smart Controls")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(gridItemListBuilder.build())
            .build()
    }

    private fun buildLoadingTemplate(): Template {
        return MessageTemplate.Builder("Connecting to device...")
            .setTitle("VF3 Smart")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()
    }
}
