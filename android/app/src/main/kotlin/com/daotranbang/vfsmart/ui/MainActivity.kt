package com.daotranbang.vfsmart.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daotranbang.vfsmart.autolink.AccessibilityDisclosure
import com.daotranbang.vfsmart.autolink.AutoLinkAccessibilityService
import com.daotranbang.vfsmart.autolink.AutoLinkService
import com.daotranbang.vfsmart.autolink.RootPermissionGranter
import com.daotranbang.vfsmart.billing.BillingManager
import com.daotranbang.vfsmart.navigation.DrivingState
import com.daotranbang.vfsmart.ui.screens.AccessibilityDisclosureDialog
import com.daotranbang.vfsmart.ui.screens.PermissionsRationaleDialog
import com.daotranbang.vfsmart.ui.screens.CameraPreviewScreen
import com.daotranbang.vfsmart.ui.screens.RtspCaptureScreen
import com.daotranbang.vfsmart.ui.screens.DebugScreen
import com.daotranbang.vfsmart.ui.screens.HomeScreen
import com.daotranbang.vfsmart.ui.screens.MirrorScreen
import com.daotranbang.vfsmart.ui.screens.RedLightDetectorScreen
import com.daotranbang.vfsmart.ui.screens.SetupScreen
import com.daotranbang.vfsmart.ui.screens.TrafficLightLiveScreen
import com.daotranbang.vfsmart.ui.screens.TpmsCalibrationScreen
import com.daotranbang.vfsmart.ui.theme.VF3SmartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var billingManager: BillingManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Once the runtime permissions are resolved, prompt for notification access
        // (used to read Google Maps turn-by-turn directions for the ODO screen).
        requestNotificationListenerAccess()
    }

    /** Opens the Play subscription sheet for the "premium" plan. Hook to a buy button. */
    fun purchasePremium() {
        billingManager.launchPurchaseFlow(this)
    }

    private val navigateToMirror = MutableStateFlow(false)

    /** Outcome of the startup root-grant attempt; gates the fallback dialogs. */
    private enum class RootGrant { PENDING, GRANTED, UNAVAILABLE }
    private val rootGrant = MutableStateFlow(RootGrant.PENDING)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(AutoLinkService.EXTRA_NAVIGATE_MIRROR, false)) {
            navigateToMirror.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Connect to Play Billing and restore the "premium" subscription entitlement.
        billingManager.start()
        // On the rooted head unit, silently grant every permission (runtime, notification
        // listener, accessibility, screen-capture) so no dialog ever appears. If root is
        // unavailable (e.g. the S20+), fall back to the in-app prompts below.
        Thread {
            val granted = RootPermissionGranter.grantAll(this, runtimePermissions())
            if (granted) {
                // Accessibility was enabled via root — record disclosure consent (Play
                // policy gate) and kick off the AutoLink connect flow.
                AccessibilityDisclosure.setAccepted(this, true)
                AutoLinkService.start(this)
            }
            rootGrant.value = if (granted) RootGrant.GRANTED else RootGrant.UNAVAILABLE
        }.start()

        setContent {
            VF3SmartTheme {
                val navController = rememberNavController()
                val currentRoute by navController.currentBackStackEntryAsState()
                val isAndroidAutoConnected by AutoLinkService.androidAutoConnected.collectAsStateWithLifecycle()
                val isMoving by DrivingState.isMoving.collectAsStateWithLifecycle()
                val shouldGoMirror by navigateToMirror.collectAsStateWithLifecycle()

                fun navigateMirror() {
                    if (currentRoute?.destination?.route != "mirror") {
                        navController.navigate("mirror") {
                            popUpTo("mirror") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }

                LaunchedEffect(isAndroidAutoConnected, isMoving) {
                    if (isAndroidAutoConnected && isMoving) navigateMirror()
                }
                LaunchedEffect(shouldGoMirror) {
                    if (shouldGoMirror) {
                        navigateMirror()
                        navigateToMirror.value = false
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "mirror",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("home") {
                        HomeScreen(
                            onNavigateToDebug    = { navController.navigate("debug") },
                            onNavigateToMirror   = {
                                navController.navigate("mirror") {
                                    popUpTo("mirror") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToSetup    = { navController.navigate("setup") },
                            onNavigateToTpmsCalibration = { navController.navigate("tpms_calibration") },
                            onNavigateToCamera       = { navController.navigate("camera") },
                            onNavigateToRedLight     = { navController.navigate("red_light") },
                            onNavigateToTrafficLight = { navController.navigate("traffic_light_live") },
                            onNavigateToRtspCapture  = { navController.navigate("rtsp_capture") }
                        )
                    }

                    composable("setup") {
                        SetupScreen(
                            onSetupComplete = { navController.popBackStack() }
                        )
                    }

                    composable("mirror") {
                        MirrorScreen(
                            onNavigateBack = {
                                navController.navigate("home") {
                                    popUpTo("mirror") { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable("red_light") {
                        RedLightDetectorScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("traffic_light_live") {
                        TrafficLightLiveScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("camera") {
                        CameraPreviewScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("rtsp_capture") {
                        RtspCaptureScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("tpms_calibration") {
                        TpmsCalibrationScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("debug") {
                        DebugScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                // First-launch prompts, shown one at a time: explain the runtime
                // permissions before requesting them, then the accessibility disclosure.
                // Only shown once the root grant has failed (non-rooted device); on the
                // head unit root grants everything and these never appear.
                val grant by rootGrant.collectAsStateWithLifecycle()
                var showPermsRationale by remember {
                    mutableStateOf(needsRuntimePermissions())
                }
                var showA11yDisclosure by remember {
                    mutableStateOf(!AccessibilityDisclosure.isAccepted(this@MainActivity))
                }
                if (grant != RootGrant.UNAVAILABLE) {
                    // Root grant still pending or succeeded — show no prompts.
                } else if (showPermsRationale) {
                    PermissionsRationaleDialog(
                        onContinue = {
                            showPermsRationale = false
                            // Launch the system permission dialogs. If nothing needs
                            // requesting, still chain to the notification-access prompt.
                            if (!requestRuntimePermissions()) {
                                requestNotificationListenerAccess()
                            }
                        },
                    )
                } else if (showA11yDisclosure) {
                    AccessibilityDisclosureDialog(
                        onAgree = {
                            AccessibilityDisclosure.setAccepted(this@MainActivity, true)
                            showA11yDisclosure = false
                            // Enable via root (head unit); on a non-rooted device the
                            // root attempt fails, so guide the user to enable it manually.
                            AutoLinkService.start(this@MainActivity) {
                                if (!AutoLinkAccessibilityService.isServiceEnabled(this@MainActivity)) {
                                    AutoLinkAccessibilityService.openAccessibilitySettings(this@MainActivity)
                                }
                            }
                        },
                        onDecline = { showA11yDisclosure = false },
                    )
                }
            }
        }
    }

    private fun requestNotificationListenerAccess() {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat?.contains(packageName) != true) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    /** Runtime permissions the app needs, by SDK level. Car comms are over WiFi
     *  (HTTP + WebSocket), so no Bluetooth permissions are required. */
    private fun runtimePermissions(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun missingPermissions(): List<String> = runtimePermissions()
        .filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

    private fun needsRuntimePermissions(): Boolean = missingPermissions().isNotEmpty()

    /** Launches the system permission dialogs. Returns true if a request was made. */
    private fun requestRuntimePermissions(): Boolean {
        val perms = missingPermissions()
        if (perms.isEmpty()) return false
        permissionLauncher.launch(perms.toTypedArray())
        return true
    }
}
