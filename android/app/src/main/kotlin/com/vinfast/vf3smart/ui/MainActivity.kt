package com.vinfast.vf3smart.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vinfast.vf3smart.ui.screens.ControlScreen
import com.vinfast.vf3smart.ui.screens.HomeScreen
import com.vinfast.vf3smart.ui.screens.SetupScreen
import com.vinfast.vf3smart.ui.theme.VF3SmartTheme
import com.vinfast.vf3smart.viewmodel.SetupViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VF3SmartTheme {
                val navController = rememberNavController()
                val setupViewModel: SetupViewModel = hiltViewModel()
                val isConfigured = remember { setupViewModel.isConfigured() }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = if (isConfigured) "home" else "setup",
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
                                onNavigateToControls = {
                                    navController.navigate("controls")
                                }
                            )
                        }

                        composable("controls") {
                            ControlScreen(
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
