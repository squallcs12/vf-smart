package com.daotranbang.vfsmart.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single app-wide source of vehicle speed. The MirrorScreen's GPS (the speed
 * cell) is the only producer; other components — AutoLinkService, MainActivity —
 * read from here instead of registering their own location listeners.
 *
 * Speed resets to 0 whenever the producing screen stops tracking (paused).
 */
object DrivingState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    /** True while moving faster than 5 km/h. */
    val isMoving: StateFlow<Boolean> =
        _speedKmh.map { it > MOVING_THRESHOLD_KMH }
            .stateIn(scope, SharingStarted.Eagerly, false)

    fun setSpeedKmh(value: Float) { _speedKmh.value = value }

    private const val MOVING_THRESHOLD_KMH = 5f
}
