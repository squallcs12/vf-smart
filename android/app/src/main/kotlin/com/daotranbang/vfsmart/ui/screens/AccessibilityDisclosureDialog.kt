package com.daotranbang.vfsmart.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.daotranbang.vfsmart.R

/**
 * Prominent disclosure for the Android Accessibility Service, shown before the
 * service is activated. Required by Google Play policy for any app declaring
 * BIND_ACCESSIBILITY_SERVICE: it explains what the service does, what it accesses,
 * and that no personal data is collected — and requires an affirmative tap.
 *
 * Non-dismissable by tapping outside / back; the user must choose Agree or Decline.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* force an explicit choice */ },
        icon = { Icon(Icons.Filled.Accessibility, contentDescription = null) },
        title = { Text(stringResource(R.string.a11y_disclosure_title)) },
        text = {
            Text(
                text = stringResource(R.string.a11y_disclosure_body),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(stringResource(R.string.a11y_disclosure_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.a11y_disclosure_decline))
            }
        },
    )
}
