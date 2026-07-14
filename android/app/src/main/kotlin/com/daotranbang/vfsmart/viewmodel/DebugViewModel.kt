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
     * Test the "close the windows" voice (played when the Android Auto session ends)
     */
    fun testCloseWindows() {
        Log.d(TAG, "Testing close-windows voice")
        voiceWarningManager.warnCloseWindows()
    }

    // Note: do NOT shut down VoiceWarningManager here — it is an app-scoped @Singleton
    // shared with CarStatusViewModel and AutoLinkService. Tearing it down when this
    // screen-scoped ViewModel is cleared would kill TTS for the whole app.
}
