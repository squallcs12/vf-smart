package com.daotranbang.vfsmart.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Vietnamese voice warnings using Android TTS
 */
@Singleton
class VoiceWarningManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentSpeechJob: Job? = null

    companion object {
        private const val TAG = "VoiceWarningManager"
        private const val VIETNAMESE_LOCALE = "vi-VN"
    }

    init {
        initializeTTS()
    }

    /**
     * Initialize Text-to-Speech engine with Vietnamese locale
     */
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("vi", "VN")
                val result = tts?.setLanguage(locale)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Vietnamese language not supported")
                    isInitialized = false
                } else {
                    Log.d(TAG, "TTS initialized successfully with Vietnamese")
                    isInitialized = true
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
                isInitialized = false
            }
        }
    }

    /**
     * Speak a message in Vietnamese
     * @param message The message to speak
     * @param repeatCount Number of times to repeat (default: 1)
     */
    fun speak(message: String, repeatCount: Int = 1) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            return
        }

        currentSpeechJob?.cancel()
        currentSpeechJob = coroutineScope.launch {
            repeat(repeatCount) { index ->
                val utteranceId = "warning_$index"

                // Set up listener for the last utterance
                if (index == repeatCount - 1) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.v(TAG, "Speaking: $message")
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.v(TAG, "Finished speaking")
                        }

                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS error")
                        }
                    })
                }

                tts?.speak(message, TextToSpeech.QUEUE_ADD, null, utteranceId)

                // Add delay between repetitions (except after the last one)
                if (index < repeatCount - 1) {
                    delay(800) // 800ms delay between repetitions
                }
            }
        }
    }

    /**
     * Remind the driver to close the windows (e.g. when the Android Auto session ends).
     */
    fun warnCloseWindows() {
        speak("Vui lòng đóng cửa sổ", repeatCount = 2)
    }

    /**
     * No-op that exists to force creation of this singleton so its TTS engine starts
     * initializing ahead of the first [speak] (called when an Android Auto session begins,
     * so the "close windows" reminder is ready by the time the session ends).
     */
    fun prepare() { /* touching the singleton is enough — TTS inits in the constructor */ }

    /**
     * Stop any ongoing speech
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        coroutineScope.cancel()
    }
}
