package com.daotranbang.vfsmart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.ui.components.OutlinedControlButton
import com.daotranbang.vfsmart.viewmodel.SetupViewModel

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val configurationState by viewModel.configurationState.collectAsStateWithLifecycle()

    var manualIp by remember { mutableStateOf("192.168.4.1") }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }

    LaunchedEffect(configurationState) {
        if (configurationState is SetupViewModel.ConfigurationState.Success) {
            onSetupComplete()
        }
    }

    LaunchedEffect(Unit) {
        if (discoveryState is SetupViewModel.DiscoveryState.Idle) {
            viewModel.discoverDevice()
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_welcome),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.setup_find_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Discovery section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setup_step1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (val state = discoveryState) {
                        is SetupViewModel.DiscoveryState.Idle,
                        is SetupViewModel.DiscoveryState.Discovering -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = stringResource(R.string.setup_searching),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(text = stringResource(R.string.setup_searching_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }

                        is SetupViewModel.DiscoveryState.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = stringResource(R.string.setup_device_found),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(text = "IP: ${state.deviceInfo.ip}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(text = "MAC: ${state.deviceInfo.mac}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                }
                            }
                            manualIp = state.deviceInfo.ip
                        }

                        is SetupViewModel.DiscoveryState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = stringResource(R.string.setup_discovery_failed),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            OutlinedControlButton(
                                text = stringResource(R.string.try_again),
                                onClick = { viewModel.discoverDevice() }
                            )
                        }
                    }

                    TextButton(
                        onClick = { showManualEntry = !showManualEntry },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (showManualEntry)
                                stringResource(R.string.setup_hide_manual)
                            else
                                stringResource(R.string.setup_enter_ip)
                        )
                    }
                }
            }

            if (showManualEntry || discoveryState is SetupViewModel.DiscoveryState.Error) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text(stringResource(R.string.setup_ip_label)) },
                    placeholder = { Text("192.168.4.1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // API Key section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = stringResource(R.string.setup_step2),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = stringResource(R.string.setup_api_key_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.setup_api_key_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible)
                                        Icons.Default.VisibilityOff
                                    else
                                        Icons.Default.Visibility,
                                    contentDescription = if (apiKeyVisible)
                                        stringResource(R.string.setup_hide_api_key)
                                    else
                                        stringResource(R.string.setup_show_api_key)
                                )
                            }
                        },
                        isError = apiKey.isNotEmpty() && apiKey.length < 8
                    )
                    if (apiKey.isNotEmpty() && apiKey.length < 8) {
                        Text(text = stringResource(R.string.setup_api_key_too_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Save and continue
            when (val state = configurationState) {
                is SetupViewModel.ConfigurationState.Idle -> {
                    ControlButton(
                        text = stringResource(R.string.setup_save),
                        onClick = { viewModel.saveConfiguration(manualIp, apiKey) },
                        enabled = manualIp.isNotBlank() && apiKey.length >= 8
                    )
                }
                is SetupViewModel.ConfigurationState.Saving -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.setup_saving),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is SetupViewModel.ConfigurationState.Testing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.setup_connecting),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is SetupViewModel.ConfigurationState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp))
                        }
                        ControlButton(
                            text = stringResource(R.string.try_again),
                            onClick = { viewModel.saveConfiguration(manualIp, apiKey) },
                            enabled = manualIp.isNotBlank() && apiKey.length >= 8
                        )
                    }
                }
                is SetupViewModel.ConfigurationState.Success -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.setup_saved),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
