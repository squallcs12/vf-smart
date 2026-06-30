package com.daotranbang.vfsmart.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.repository.VF3Repository
import com.daotranbang.vfsmart.data.network.ConnectionState
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
    private var connectionState: ConnectionState = repository.connectionState.value

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
        val isConnected = connectionState == ConnectionState.Connected
        val status = currentStatus
        val cameraOn = status?.controls?.insideCameras == 1

        val listBuilder = ItemList.Builder()

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

        val title = if (isConnected) "Cấu hình màn hình ODO" else "Cấu hình màn hình ODO (Mất kết nối)"

        return ListTemplate.Builder()
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setTitle(title)
            .build()
    }
}
