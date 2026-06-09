package com.daotranbang.vfsmart.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daotranbang.vfsmart.R

/**
 * Rationale shown before the app requests its runtime permissions, explaining what
 * each permission is for. Required affirmative tap on "Continue" then triggers the
 * system permission dialogs. Mirrors [AccessibilityDisclosureDialog].
 */
@Composable
fun PermissionsRationaleDialog(onContinue: () -> Unit) {
    val items = listOf(
        Triple(Icons.Filled.Bluetooth, R.string.perm_bluetooth_title, R.string.perm_bluetooth_reason),
        Triple(Icons.Filled.PhotoCamera, R.string.perm_camera_title, R.string.perm_camera_reason),
        Triple(Icons.Filled.LocationOn, R.string.perm_location_title, R.string.perm_location_reason),
        Triple(Icons.Filled.Notifications, R.string.perm_notification_title, R.string.perm_notification_reason),
    )
    AlertDialog(
        onDismissRequest = { /* force an explicit choice */ },
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.perm_rationale_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.perm_rationale_intro))
                Spacer(Modifier.size(12.dp))
                items.forEach { (icon, title, reason) ->
                    PermissionRow(icon, stringResource(title), stringResource(reason))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.perm_rationale_continue))
            }
        },
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, reason: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}
