package com.daotranbang.vfsmart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.repository.VF3Repository
import com.daotranbang.vfsmart.navigation.VF3GattServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class OdoConfigScreen(
    carContext: CarContext,
    private val repository: VF3Repository
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentStatus: CarStatus? = repository.carStatus.value
    private var connectionState: VF3GattServer.BleConnectionState = repository.connectionState.value

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                scope.launch {
                    repository.carStatus.drop(1).collectLatest { status ->
                        currentStatus = status
                        invalidate()
                    }
                }
                scope.launch {
                    repository.connectionState.drop(1).collectLatest { state ->
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
        val isConnected = connectionState == VF3GattServer.BleConnectionState.Connected
        val status = currentStatus
        val odoOn = status?.controls?.odoScreen == 1
        val cameraOn = status?.controls?.insideCameras == 1
        val dashcamOn = status?.controls?.dashcam == 1

        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("ODO Screen")
                .addText(if (odoOn) "Đang bật" else "Đang tắt")
                .setToggle(Toggle.Builder { isChecked ->
                    if (isConnected) {
                        scope.launch { repository.setOdoScreen(isChecked) }
                    }
                }.setChecked(odoOn).build())
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Camera trong xe")
                .addText(if (cameraOn) "Đang bật" else "Đang tắt")
                .setToggle(Toggle.Builder { isChecked ->
                    if (isConnected) {
                        scope.launch { repository.setInsideCameras(isChecked) }
                    }
                }.setChecked(cameraOn).build())
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Dashcam")
                .addText(if (dashcamOn) "Đang bật" else "Đang tắt")
                .setToggle(Toggle.Builder { isChecked ->
                    if (isConnected) {
                        scope.launch { repository.setDashcam(isChecked) }
                    }
                }.setChecked(dashcamOn).build())
                .build()
        )

        val title = if (isConnected) "Cấu hình màn hình ODO" else "Cấu hình màn hình ODO (Mất kết nối)"

        return ListTemplate.Builder()
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setTitle(title)
            .build()
    }
}
