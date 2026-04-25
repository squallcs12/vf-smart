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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.daotranbang.vfsmart.ui.screens.AppLauncherScreen
import com.daotranbang.vfsmart.ui.screens.ControlScreen
import com.daotranbang.vfsmart.ui.screens.DebugScreen
import com.daotranbang.vfsmart.ui.screens.HomeScreen
import com.daotranbang.vfsmart.ui.screens.MirrorScreen
import com.daotranbang.vfsmart.ui.screens.SetupScreen
import com.daotranbang.vfsmart.ui.screens.TpmsCalibrationScreen
import com.daotranbang.vfsmart.ui.theme.VF3SmartTheme
import com.daotranbang.vfsmart.autolink.AutoLinkService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { AutoLinkService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Thread { try { Runtime.getRuntime().exec("su") } catch (_: Exception) {} }.start()
        requestNotificationListenerAccess()
        requestRuntimePermissions()

        setContent {
            VF3SmartTheme {
                val navController = rememberNavController()
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
                                onNavigateToApps     = { navController.navigate("apps") },
                                onNavigateToMirror   = {
                                    navController.navigate("mirror") {
                                        popUpTo("mirror") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable("apps") {
                            AppLauncherScreen(
                                onNavigateBack = { navController.popBackStack() }
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
                                }
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
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            AutoLinkService.start(this)
        }
    }
}
