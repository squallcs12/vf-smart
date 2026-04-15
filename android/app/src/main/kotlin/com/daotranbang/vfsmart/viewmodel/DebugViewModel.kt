package com.daotranbang.vfsmart.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.daotranbang.vfsmart.util.VoiceWarningManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for debug screen
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val voiceWarningManager: VoiceWarningManager
) : ViewModel() {

    companion object {
        private const val TAG = "DebugViewModel"
    }

    /**
     * Test the window warning voice
     */
    fun testWindowWarning() {
        Log.d(TAG, "Testing window warning voice")
        voiceWarningManager.warnWindowsOpen()
    }

    /**
     * Test the light reminder voice
     */
    fun testLightReminder() {
        Log.d(TAG, "Testing light reminder voice")
        voiceWarningManager.warnLightsOff()
    }

    override fun onCleared() {
        super.onCleared()
        voiceWarningManager.shutdown()
    }
}
