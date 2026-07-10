package com.daotranbang.vfsmart.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Large touch-friendly control button
 * Minimum 48dp touch target as per Material Design guidelines
 *
 * @param text Button text
 * @param onClick Click handler
 * @param modifier Modifier
 * @param enabled Whether button is enabled
 * @param icon Optional icon composable
 * @param containerColor Optional custom container color
 */
@Composable
fun ControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    containerColor: Color? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        contentPadding = contentPadding
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            icon?.let {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    it()
                }
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Press-and-hold control button. Fires [onPress] the moment the finger goes
 * down and [onRelease] when it lifts (or the gesture is cancelled / the button
 * leaves composition). Behaves like a physical car window switch — the action
 * runs only while the button is held, not on a single tap.
 *
 * @param text Button text
 * @param onPress Called when the press starts (hold begins)
 * @param onRelease Called when the press ends (hold released or cancelled)
 * @param modifier Modifier
 * @param enabled Whether button is enabled
 * @param icon Optional icon composable
 * @param contentPadding Inner content padding
 */
@Composable
fun HoldControlButton(
    text: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    var pressed by remember { mutableStateOf(false) }

    // Safety net: if the button leaves composition or gets disabled while still
    // held, make sure the release still fires so the windows don't keep rolling
    // up. (Disabling mid-hold cancels the gesture coroutine, so its own release
    // path can't run — this covers that case as well as composition exit.)
    DisposableEffect(enabled) {
        onDispose {
            if (pressed) {
                pressed = false
                onRelease()
            }
        }
    }

    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        pressed  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else     -> MaterialTheme.colorScheme.primary
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            // Re-arm the gesture when `enabled` flips; disabling mid-hold cancels
            // the gesture, which resolves tryAwaitRelease() and fires onRelease.
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress()
                        tryAwaitRelease()
                        pressed = false
                        onRelease()
                    }
                )
            },
        shape = ButtonDefaults.shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        it()
                    }
                }

                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Outlined control button for secondary actions
 */
@Composable
fun OutlinedControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        contentPadding = contentPadding
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            icon?.let {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    it()
                }
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Toggle button for on/off states
 */
@Composable
fun ToggleControlButton(
    text: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val containerColor = if (isOn) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isOn) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = contentPadding
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = if (isOn) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
