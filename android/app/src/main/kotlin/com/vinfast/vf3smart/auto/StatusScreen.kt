package com.vinfast.vf3smart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.vinfast.vf3smart.R
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.network.WebSocketManager
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
    private var connectionState: WebSocketManager.ConnectionState = WebSocketManager.ConnectionState.Disconnected

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
                        invalidate()
                    }
                }

                // Collect connection state and invalidate screen
                scope.launch {
                    repository.connectionState.collectLatest { state ->
                        connectionState = state
                        invalidate()
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
        return if (status == null) {
            buildLoadingTemplate()
        } else {
            buildControlsTemplate(status)
        }
    }

    private fun buildControlsTemplate(status: CarStatus): Template {
        val gridItemListBuilder = ItemList.Builder()
        val isConnected = connectionState == WebSocketManager.ConnectionState.Connected

        val accessoryPowerOn = status.controls.accessoryPower == 1
        val odoScreenOn = status.controls.odoScreen == 1
        val armrestOn = status.controls.armrest == 1
        val dashcamOn = status.controls.dashcam == 1

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

        // ODO Screen
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

        // Armrest
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

        // Dashcam
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
            is WebSocketManager.ConnectionState.Connected -> "Connected"
            is WebSocketManager.ConnectionState.Disconnected -> "Disconnected"
            is WebSocketManager.ConnectionState.Error -> "Error"
        }

        return GridTemplate.Builder()
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(gridItemListBuilder.build())
            .setTitle(connectionText)
            .build()
    }

    private fun buildLoadingTemplate(): Template {
        val connectionText = when (connectionState) {
            is WebSocketManager.ConnectionState.Connected -> "Connected"
            is WebSocketManager.ConnectionState.Disconnected -> "Disconnected. Connecting..."
            is WebSocketManager.ConnectionState.Error -> "Error. Retrying..."
        }

        return MessageTemplate.Builder(connectionText)
            .setTitle("VF3 Smart")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()
    }
}
