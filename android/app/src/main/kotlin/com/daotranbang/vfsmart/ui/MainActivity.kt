package com.daotranbang.vfsmart.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.daotranbang.vfsmart.ui.screens.ControlScreen
import com.daotranbang.vfsmart.ui.screens.DebugScreen
import com.daotranbang.vfsmart.ui.screens.HomeScreen
import com.daotranbang.vfsmart.ui.screens.MirrorScreen
import com.daotranbang.vfsmart.ui.screens.SetupScreen
import com.daotranbang.vfsmart.ui.screens.TpmsCalibrationScreen
import com.daotranbang.vfsmart.ui.theme.VF3SmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VF3SmartTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "mirror",
                        modifier = Modifier.padding(innerPadding)
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
    }
}
