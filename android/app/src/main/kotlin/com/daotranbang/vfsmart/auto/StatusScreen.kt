package com.daotranbang.vfsmart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.data.model.*
import com.daotranbang.vfsmart.data.repository.VF3Repository
import com.daotranbang.vfsmart.data.network.ConnectionState
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
    private var connectionState: ConnectionState = ConnectionState.Disconnected

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
        return buildControlsTemplate(currentStatus ?: defaultCarStatus())
    }

    private fun defaultCarStatus() = CarStatus(
        sensors = Sensors(brake = 0, steeringAngle = 0, batteryVoltage = "0.0", gearDrive = 0),
        doors = Doors(frontLeft = 0, frontRight = 0, trunk = 0, locked = 0),
        windows = Windows(leftState = 0, rightState = 0),
        seats = Seats(frontLeftOccupied = 0, frontRightOccupied = 0, frontLeftSeatbelt = 0, frontRightSeatbelt = 0),
        lights = Lights(demiLight = 0, normalLight = 0),
        proximity = Proximity(rearLeft = 0, rearRight = 0),
        controls = Controls(brakePressed = 0, accessoryPower = 0, insideCameras = 0, carLock = 0, carUnlock = 0, dashcam = 0, odoScreen = 0, armrest = 0),
        chargingStatus = 0,
        carLockState = "",
        lightReminderEnabled = false,
        time = null,
        tpms = null
    )

    private fun buildControlsTemplate(status: CarStatus): Template {
        val gridItemListBuilder = ItemList.Builder()
        val isConnected = connectionState == ConnectionState.Connected

        val accessoryPowerOn = status.controls.accessoryPower == 1

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

        // 6. Open Right Window
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

        // 7. Unlock Charger
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.btn_unlock_charger))
                .setText(carContext.getString(R.string.auto_charger_unlock_desc))
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_charger
                        )
                    ).setTint(if (isConnected) CarColor.GREEN else CarColor.DEFAULT).build()
                )
                .setOnClickListener {
                    if (isConnected) {
                        scope.launch {
                            repository.unlockCharger()
                        }
                    }
                }
                .build()
        )

        // 8. ODO Config
        gridItemListBuilder.addItem(
            GridItem.Builder()
                .setTitle("ODO Config")
                .setText("Cấu hình ODO")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_odo_screen)
                    ).setTint(CarColor.BLUE).build()
                )
                .setOnClickListener { screenManager.push(OdoConfigScreen(carContext, repository)) }
                .build()
        )

        val connectionText = when (connectionState) {
            ConnectionState.Connected -> "Connected"
            ConnectionState.Disconnected -> "Disconnected"
        }

        return GridTemplate.Builder()
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(gridItemListBuilder.build())
            .setTitle(connectionText)
            .build()
    }
}
