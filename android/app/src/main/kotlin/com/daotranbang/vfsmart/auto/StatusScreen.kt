package com.daotranbang.vfsmart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.repository.VF3Repository
import com.daotranbang.vfsmart.navigation.VF3GattServer
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
    private var connectionState: VF3GattServer.BleConnectionState = VF3GattServer.BleConnectionState.Disconnected

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // Collect BLE status updates and invalidate screen
                scope.launch {
                    repository.carStatus.collectLatest { status ->
                        currentStatus = status
                        invalidate()
                    }
                }

                // Collect BLE connection state and invalidate screen
                scope.launch {
                    repository.connectionState.collectLatest { state ->
                        connectionState = state
                        invalidate()
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val status = currentStatus
        return if (status == null) {
            buildLoadingTemplate()
        } else {
            buildControlsTemplate(status)
        }
    }

    private fun buildControlsTemplate(status: CarStatus): Template {
        val gridItemListBuilder = ItemList.Builder()
        val isConnected = connectionState == VF3GattServer.BleConnectionState.Connected

        val accessoryPowerOn = status.controls.accessoryPower == 1
        val odoScreenOn = status.controls.odoScreen == 1
        val armrestOn = status.controls.armrest == 1
        val dashcamOn = status.controls.dashcam == 1

        // 1. Close Left Window
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("Close Left")
                .setText("Close Left Window")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_window_left_up
                        )
                    ).setTint(if (isConnected) CarColor.BLUE else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.controlWindowUp("left", true)
                        }
                    }
                }
                .build()
        )

        // 2. Turn On Inside Camera
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
                    ).setTint(if (isConnected) CarColor.YELLOW else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.setInsideCameras(true)
                        }
                    }
                }
                .build()
        )

        // 3. Accessory Power Control
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
                    ).setTint(if (isConnected) (if (accessoryPowerOn) CarColor.GREEN else CarColor.DEFAULT) else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.toggleAccessoryPower()
                        }
                    }
                }
                .build()
        )

        // 4. Close Right Window
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("Close Right")
                .setText("Close Right Window")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_window_right_up
                        )
                    ).setTint(if (isConnected) CarColor.BLUE else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.controlWindowUp("right", true)
                        }
                    }
                }
                .build()
        )

        // 5. Open Left Window
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("Open Left")
                .setText("Open Left Window")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_window_left_down
                        )
                    ).setTint(if (isConnected) CarColor.YELLOW else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.controlWindowDown("left", true)
                        }
                    }
                }
                .build()
        )

        // 6. ODO Screen
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (odoScreenOn) "ODO Off" else "ODO On")
                .setText(if (odoScreenOn) "ODO Screen On" else "ODO Screen Off")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_odo_screen
                        )
                    ).setTint(if (isConnected) (if (odoScreenOn) CarColor.GREEN else CarColor.DEFAULT) else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.toggleOdoScreen()
                        }
                    }
                }
                .build()
        )

        // 7. Armrest
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (armrestOn) "Armrest Off" else "Armrest On")
                .setText(if (armrestOn) "Armrest On" else "Armrest Off")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_armrest
                        )
                    ).setTint(if (isConnected) (if (armrestOn) CarColor.GREEN else CarColor.DEFAULT) else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.toggleArmrest()
                        }
                    }
                }
                .build()
        )

        // 8. Open Right Window
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("Open Right")
                .setText("Open Right Window")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_window_right_down
                        )
                    ).setTint(if (isConnected) CarColor.YELLOW else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.controlWindowDown("right", true)
                        }
                    }
                }
                .build()
        )

        // 9. Dashcam
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(if (dashcamOn) "Dashcam Off" else "Dashcam On")
                .setText(if (dashcamOn) "Dashcam On" else "Dashcam Off")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_dashcam
                        )
                    ).setTint(if (isConnected) (if (dashcamOn) CarColor.GREEN else CarColor.DEFAULT) else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.toggleDashcam()
                        }
                    }
                }
                .build()
        )

        val connectionText = when (connectionState) {
            VF3GattServer.BleConnectionState.Connected -> "Connected"
            VF3GattServer.BleConnectionState.Disconnected -> "Disconnected"
        }

        return GridTemplate.Builder()
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(gridItemListBuilder.build())
            .setTitle(connectionText)
            .build()
    }

    private fun buildLoadingTemplate(): Template {
        val connectionText = when (connectionState) {
            VF3GattServer.BleConnectionState.Connected -> "Connected"
            VF3GattServer.BleConnectionState.Disconnected -> "Waiting for ESP32..."
        }

        return MessageTemplate.Builder(connectionText)
            .setTitle("VF3 Smart")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()
    }
}
