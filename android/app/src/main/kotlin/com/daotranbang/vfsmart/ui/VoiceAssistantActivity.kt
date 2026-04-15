package com.daotranbang.vfsmart.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.daotranbang.vfsmart.assistant.AssistantCommandHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Invisible activity that handles Google Assistant deep links
 * Receives voice commands via App Actions and delegates to AssistantCommandHandler
 *
 * Deep link format: vf3smart://command/<action>?param1=value1&param2=value2
 */
@AndroidEntryPoint
class VoiceAssistantActivity : ComponentActivity() {

    @Inject
    lateinit var commandHandler: AssistantCommandHandler

    companion object {
        private const val TAG = "VoiceAssistantActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Process the intent
        handleIntent(intent)

        // Finish immediately - this is a transparent activity
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    handleDeepLink(uri)
                } ?: run {
                    Log.w(TAG, "No data URI in intent")
                }
            }
            else -> {
                Log.w(TAG, "Unsupported intent action: ${intent.action}")
            }
        }
    }

    private fun handleDeepLink(uri: Uri) {
        Log.d(TAG, "Handling deep link: $uri")

        // Validate scheme
        if (uri.scheme != AssistantCommandHandler.SCHEME) {
            Log.w(TAG, "Invalid scheme: ${uri.scheme}")
            return
        }

        // Validate host
        if (uri.host != AssistantCommandHandler.HOST_COMMAND) {
            Log.w(TAG, "Invalid host: ${uri.host}")
            return
        }

        // Extract action from path (e.g., /lock, /unlock, /charger/unlock)
        val action = uri.pathSegments.joinToString("/")
        if (action.isEmpty()) {
            Log.w(TAG, "No action specified in URI")
            return
        }

        // Extract query parameters
        val params = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { paramName ->
            uri.getQueryParameter(paramName)?.let { paramValue ->
                params[paramName] = paramValue
            }
        }

        // Execute command
        commandHandler.handleCommand(action, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: Don't shutdown commandHandler here as it's a singleton
        // It should be cleaned up when the app is destroyed
    }
}
