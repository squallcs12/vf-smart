package com.daotranbang.vfsmart.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
)

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.coerceAtLeast(1)
    val h = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLauncherScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var apps  by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            context.packageManager
                .queryIntentActivities(intent, 0)
                .map { ri ->
                    AppInfo(
                        label        = ri.loadLabel(context.packageManager).toString(),
                        packageName  = ri.activityInfo.packageName,
                        activityName = ri.activityInfo.name
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Search apps…") },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns            = GridCells.Adaptive(minSize = 88.dp),
                    contentPadding     = PaddingValues(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier           = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { "${it.packageName}/${it.activityName}" }) { app ->
                        AppCell(app = app, onClick = {
                            try {
                                context.startActivity(
                                    Intent().apply {
                                        component = ComponentName(app.packageName, app.activityName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCell(app: AppInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(app.packageName, app.activityName) {
        icon = withContext(Dispatchers.IO) {
            try {
                context.packageManager
                    .getActivityIcon(ComponentName(app.packageName, app.activityName))
                    .toBitmap()
                    .asImageBitmap()
            } catch (_: Exception) { null }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Image(bitmap = icon!!, contentDescription = app.label,
                modifier = Modifier.size(52.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text      = app.label,
            fontSize  = 11.sp,
            textAlign = TextAlign.Center,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis,
            color     = MaterialTheme.colorScheme.onSurface
        )
    }
}
