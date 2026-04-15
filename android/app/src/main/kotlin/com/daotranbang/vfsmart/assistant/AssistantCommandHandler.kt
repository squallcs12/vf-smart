package com.daotranbang.vfsmart.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.daotranbang.vfsmart.data.repository.VF3Repository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Google Assistant voice commands for VF3 Smart car control
 * Translates deep link commands to API calls and provides voice feedback
 */
@Singleton
class AssistantCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VF3Repository
) {
    companion object {
        private const val TAG = "AssistantCommandHandler"

        // Deep link scheme
        const val SCHEME = "vf3smart"
        const val HOST_COMMAND = "command"
    }

    private var textToSpeech: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                Log.d(TAG, "TextToSpeech initialized successfully")
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    /**
     * Process a voice command from Google Assistant
     * @param action The action from the deep link (e.g., "lock", "unlock")
     * @param params Map of parameters from the deep link
     */
    fun handleCommand(action: String, params: Map<String, String> = emptyMap()) {
        Log.d(TAG, "Handling command: $action with params: $params")

        scope.launch {
            try {
                when (action.lowercase()) {
                    "lock" -> handleLockCommand()
                    "unlock" -> handleUnlockCommand()
                    "open" -> handleOpenCommand(params["object"])
                    "close" -> handleCloseCommand(params["object"])
                    "honk" -> handleHonkCommand()
                    "accessory" -> handleAccessoryPowerCommand(params["state"])
                    "charger/unlock" -> handleChargerUnlockCommand()
                    else -> {
                        speak("Unknown command: $action")
                        Log.w(TAG, "Unknown command: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling command: $action", e)
                speak("Error executing command: ${e.message}")
            }
        }
    }

    private suspend fun handleLockCommand() {
        withContext(Dispatchers.IO) {
            repository.lockCar()
        }.fold(
            onSuccess = { speak("Car locked successfully") },
            onFailure = { speak("Failed to lock car: ${it.message}") }
        )
    }

    private suspend fun handleUnlockCommand() {
        withContext(Dispatchers.IO) {
            repository.unlockCar()
        }.fold(
            onSuccess = { speak("Car unlocked successfully") },
            onFailure = { speak("Failed to unlock car: ${it.message}") }
        )
    }

    private suspend fun handleOpenCommand(objectName: String?) {
        when (objectName?.lowercase()) {
            "windows", "window" -> {
                speak("Cannot open windows remotely. Use close windows instead")
            }
            "mirrors", "side_mirrors", "side mirrors" -> {
                withContext(Dispatchers.IO) {
                    repository.openSideMirrors()
                }.fold(
                    onSuccess = { speak("Opening side mirrors") },
                    onFailure = { speak("Failed to open mirrors: ${it.message}") }
                )
            }
            else -> speak("I don't know how to open $objectName")
        }
    }

    private suspend fun handleCloseCommand(objectName: String?) {
        when (objectName?.lowercase()) {
            "windows", "window" -> {
                withContext(Dispatchers.IO) {
                    repository.closeWindows()
                }.fold(
                    onSuccess = { speak("Closing windows for 30 seconds") },
                    onFailure = { speak("Failed to close windows: ${it.message}") }
                )
            }
            "mirrors", "side_mirrors", "side mirrors" -> {
                withContext(Dispatchers.IO) {
                    repository.closeSideMirrors()
                }.fold(
                    onSuccess = { speak("Closing side mirrors") },
                    onFailure = { speak("Failed to close mirrors: ${it.message}") }
                )
            }
            else -> speak("I don't know how to close $objectName")
        }
    }

    private suspend fun handleHonkCommand() {
        withContext(Dispatchers.IO) {
            repository.beepHorn(durationMs = 1000)
        }.fold(
            onSuccess = { speak("Honking horn") },
            onFailure = { speak("Failed to honk: ${it.message}") }
        )
    }

    private suspend fun handleAccessoryPowerCommand(state: String?) {
        when (state?.lowercase()) {
            "on" -> {
                withContext(Dispatchers.IO) {
                    repository.setAccessoryPower(true)
                }.fold(
                    onSuccess = { speak("Accessory power turned on") },
                    onFailure = { speak("Failed to turn on accessory power: ${it.message}") }
                )
            }
            "off" -> {
                withContext(Dispatchers.IO) {
                    repository.setAccessoryPower(false)
                }.fold(
                    onSuccess = { speak("Accessory power turned off") },
                    onFailure = { speak("Failed to turn off accessory power: ${it.message}") }
                )
            }
            "toggle" -> {
                withContext(Dispatchers.IO) {
                    repository.toggleAccessoryPower()
                }.fold(
                    onSuccess = { speak("Accessory power toggled") },
                    onFailure = { speak("Failed to toggle accessory power: ${it.message}") }
                )
            }
            else -> speak("Invalid accessory power state: $state")
        }
    }

    private suspend fun handleChargerUnlockCommand() {
        withContext(Dispatchers.IO) {
            repository.unlockCharger()
        }.fold(
            onSuccess = { speak("Charger unlocked") },
            onFailure = { speak("Failed to unlock charger: ${it.message}") }
        )
    }

    /**
     * Speak text using TextToSpeech
     */
    private fun speak(text: String) {
        Log.d(TAG, "Speaking: $text")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
