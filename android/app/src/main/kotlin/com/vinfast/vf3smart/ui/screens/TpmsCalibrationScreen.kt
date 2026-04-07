package com.vinfast.vf3smart.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.model.TpmsSensorAssignments
import com.vinfast.vf3smart.data.model.TpmsSensorInfo
import com.vinfast.vf3smart.data.model.TpmsTire
import com.vinfast.vf3smart.data.network.WebSocketManager
import com.vinfast.vf3smart.viewmodel.CarStatusViewModel
import com.vinfast.vf3smart.viewmodel.TpmsCalibrationViewModel

@Composable
fun TpmsCalibrationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TpmsCalibrationViewModel = hiltViewModel(),
    statusViewModel: CarStatusViewModel = hiltViewModel()
) {
    val state       by viewModel.state.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val carStatus   by statusViewModel.carStatus.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state) {
        when (val s = state) {
            is TpmsCalibrationViewModel.State.Success -> {
                snackbarHost.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            is TpmsCalibrationViewModel.State.Error -> {
                snackbarHost.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            else -> {}
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Assignments?") },
            text  = { Text("This clears all 4 sensor mappings. The next 4 unique sensors received will be auto-assigned as FL → FR → RL → RR.\n\nTrigger each sensor by slightly inflating/deflating the tire.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.reset()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("TPMS Calibration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        modifier = modifier
    ) { padding ->
        val isLoading = state is TpmsCalibrationViewModel.State.Loading

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Info card ────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Sensor IDs are auto-learned when a tire transmits. " +
                           "Use Swap to fix incorrect assignments after tire rotation.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // ── Tire grid ─────────────────────────────────────────────────
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TireGrid(
                    assignments = assignments,
                    carStatus   = carStatus,
                    onSwap      = { a, b -> viewModel.swap(a, b) }
                )
            }

            // ── Reset button ──────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = { showResetDialog = true },
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset All & Re-learn")
            }
        }
    }
}

// ── Tire 2×2 grid with swap buttons ──────────────────────────────────────────

@Composable
private fun TireGrid(
    assignments: TpmsSensorAssignments?,
    carStatus:   CarStatus?,
    onSwap:      (String, String) -> Unit
) {
    val tpms = carStatus?.tpms

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Front row: FL ⟷ FR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TireBox("FL", assignments?.fl, tpms?.fl, Modifier.weight(1f))
            IconButton(onClick = { onSwap("fl", "fr") }) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap FL ↔ FR")
            }
            TireBox("FR", assignments?.fr, tpms?.fr, Modifier.weight(1f))
        }

        // Middle swap row: FL↔RL and FR↔RR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onSwap("fl", "rl") }) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Swap FL ↔ RL")
                }
                Text("FL↔RL", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onSwap("fr", "rr") }) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Swap FR ↔ RR")
                }
                Text("FR↔RR", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Rear row: RL ⟷ RR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TireBox("RL", assignments?.rl, tpms?.rl, Modifier.weight(1f))
            IconButton(onClick = { onSwap("rl", "rr") }) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap RL ↔ RR")
            }
            TireBox("RR", assignments?.rr, tpms?.rr, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TireBox(
    position:   String,
    sensorInfo: TpmsSensorInfo?,
    tire:       TpmsTire?,
    modifier:   Modifier = Modifier
) {
    val learned  = sensorInfo?.learned == true
    val hasData  = tire?.valid == true && tire.stale == false
    val borderColor = when {
        tire?.alarm == true         -> MaterialTheme.colorScheme.error
        hasData && tire.pressureKpa < 180f -> MaterialTheme.colorScheme.error
        hasData                     -> MaterialTheme.colorScheme.primary
        learned                     -> MaterialTheme.colorScheme.outline
        else                        -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier.border(
            width = if (learned || hasData) 2.dp else 1.dp,
            color = borderColor,
            shape = RoundedCornerShape(8.dp)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Position label
            Text(
                text = position,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = borderColor
            )

            // Sensor ID
            Text(
                text = if (learned) sensorInfo!!.id else "Not learned",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (learned)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Live pressure
            if (hasData) {
                Text(
                    text = String.format("%.0f kPa", tire!!.pressureKpa),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        tire.alarm              -> MaterialTheme.colorScheme.error
                        tire.pressureKpa < 180f -> MaterialTheme.colorScheme.error
                        else                    -> MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = "${tire.tempC}°C  ${if (tire.batteryOk) "🔋 OK" else "🔋 Low"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (learned) "Waiting…" else "--",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
