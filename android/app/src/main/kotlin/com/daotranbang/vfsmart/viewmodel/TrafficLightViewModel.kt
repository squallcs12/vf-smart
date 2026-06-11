package com.daotranbang.vfsmart.viewmodel

import androidx.lifecycle.ViewModel
import com.daotranbang.vfsmart.data.local.SecurePreferences
import com.daotranbang.vfsmart.vision.TrafficLightAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Holds the configured RTSP URL and the latest live traffic-light [Reading].
 *
 * [reading] is the single source of truth other screens can collect from (ODO
 * cell, home grid, a voice warning, …). The live [com.daotranbang.vfsmart.ui.components.RtspTrafficLightView]
 * pushes each frame's result in via [onReading].
 */
@HiltViewModel
class TrafficLightViewModel @Inject constructor(
    securePreferences: SecurePreferences
) : ViewModel() {

    /** The camera URL the live view should play (empty if not configured). */
    val rtspUrl: String = securePreferences.getRtspUrl() ?: ""

    private val _reading = MutableStateFlow(TrafficLightAnalyzer.Reading.EMPTY)
    val reading: StateFlow<TrafficLightAnalyzer.Reading> = _reading.asStateFlow()

    /** Called by the live view for every analysed frame. */
    fun onReading(reading: TrafficLightAnalyzer.Reading) {
        _reading.value = reading
    }
}
