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

        coroutineScope.launch {
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
     * Warn about open windows when car is locked
     */
    fun warnWindowsOpen() {
        speak("Cửa sổ đang mở", repeatCount = 2)
    }

    /**
     * Warn that headlights are not turned on (nighttime driving)
     */
    fun warnLightsOff() {
        speak("Bạn chưa bật đèn", repeatCount = 2)
    }

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
