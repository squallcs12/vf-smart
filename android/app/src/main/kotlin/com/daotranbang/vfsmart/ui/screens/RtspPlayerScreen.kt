package com.daotranbang.vfsmart.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.data.local.SecurePreferences
import com.daotranbang.vfsmart.ui.components.RtspVideoPlayer

/**
 * Full-screen live RTSP player. Reuses the RTSP URL stored during capture setup.
 * Back button or system back returns to the previous screen.
 */
@Composable
fun RtspPlayerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rtspUrl = remember { SecurePreferences.getInstance(context).getRtspUrl() }

    BackHandler { onNavigateBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!rtspUrl.isNullOrBlank()) {
            RtspVideoPlayer(url = rtspUrl, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = stringResource(R.string.rtsp_error_empty_url),
                color = Color.White
            )
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }
    }
}
