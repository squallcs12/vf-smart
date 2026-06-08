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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daotranbang.vfsmart.autolink.AutoLinkService
import com.daotranbang.vfsmart.ui.screens.CameraPreviewScreen
import com.daotranbang.vfsmart.ui.screens.RtspCaptureScreen
import com.daotranbang.vfsmart.ui.screens.ControlScreen
import com.daotranbang.vfsmart.ui.screens.DebugScreen
import com.daotranbang.vfsmart.ui.screens.HomeScreen
import com.daotranbang.vfsmart.ui.screens.MirrorScreen
import com.daotranbang.vfsmart.ui.screens.RedLightDetectorScreen
import com.daotranbang.vfsmart.ui.screens.SetupScreen
import com.daotranbang.vfsmart.ui.screens.TpmsCalibrationScreen
import com.daotranbang.vfsmart.ui.theme.VF3SmartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    private val navigateToMirror = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(AutoLinkService.EXTRA_NAVIGATE_MIRROR, false)) {
            navigateToMirror.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Thread {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "appops set com.link.autolink.pro PROJECT_MEDIA allow"))
            } catch (_: Exception) {}
        }.start()
        requestNotificationListenerAccess()
        requestRuntimePermissions()

        setContent {
            VF3SmartTheme {
                val navController = rememberNavController()
                val currentRoute by navController.currentBackStackEntryAsState()
                val isAndroidAutoConnected by AutoLinkService.androidAutoConnected.collectAsStateWithLifecycle()
                val isMoving by AutoLinkService.isMoving.collectAsStateWithLifecycle()
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
                    composable("setup") {
                        SetupScreen(
                            onSetupComplete = {
                                navController.navigate("home") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("home") {
                        HomeScreen(
                            onNavigateToControls = { navController.navigate("controls") },
                            onNavigateToDebug    = { navController.navigate("debug") },
                            onNavigateToSetup    = { navController.navigate("setup") },
                            onNavigateToMirror   = {
                                navController.navigate("mirror") {
                                    popUpTo("mirror") { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
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

                    composable("controls") {
                        ControlScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToTpmsCalibration = {
                                navController.navigate("tpms_calibration")
                            },
                            onNavigateToCamera = {
                                navController.navigate("camera")
                            },
                            onNavigateToRedLight = {
                                navController.navigate("red_light")
                            },
                            onNavigateToRtspCapture = {
                                navController.navigate("rtsp_capture")
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
            }
        }
    }

    private fun requestNotificationListenerAccess() {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat?.contains(packageName) != true) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun requestRuntimePermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}
