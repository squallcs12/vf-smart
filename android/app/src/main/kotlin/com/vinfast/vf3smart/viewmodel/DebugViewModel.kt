package com.vinfast.vf3smart.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.vinfast.vf3smart.util.VoiceWarningManager
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

    override fun onCleared() {
        super.onCleared()
        voiceWarningManager.shutdown()
    }
}
