package com.daotranbang.vfsmart.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Records an RTSP stream to the device library via FFmpegKit (stream-copy, no
 * re-encode). Independent of any UI — [start] kicks off a background capture and
 * [stop] ends it; the finished clip is saved to Movies/VF3Smart automatically.
 *
 * This opens its own RTSP connection, so it can run alongside an ExoPlayer
 * preview of the same stream.
 */
class RtspRecorder(private val context: Context) {
    private var activeSessionId: Long = -1L
    @Volatile private var active = false

    val isRecording: Boolean get() = active

    fun start(url: String) {
        if (active || url.isBlank()) return
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "vf3_${System.currentTimeMillis()}.mp4"
        )
        active = true
        val session = FFmpegKit.executeAsync(
            "-rtsp_transport tcp -i \"$url\" -c copy -y \"${file.absolutePath}\""
        ) { completed ->
            active = false
            val rc = completed.returnCode
            if ((ReturnCode.isSuccess(rc) || ReturnCode.isCancel(rc)) &&
                file.exists() && file.length() > 0L
            ) {
                runCatching { saveToLibrary(file) }
                    .onFailure { Log.w(TAG, "Save failed: ${it.message}"); file.delete() }
            } else {
                file.delete()
            }
        }
        activeSessionId = session.sessionId
    }

    fun stop() {
        if (activeSessionId != -1L) {
            FFmpegKit.cancel(activeSessionId)
            activeSessionId = -1L
        }
    }

    // Runs on the FFmpegKit completion thread (off the main thread).
    private fun saveToLibrary(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VF3Smart")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri: Uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv
            ) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            cv.clear()
            cv.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
            file.delete()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "VF3Smart"
            ).also { it.mkdirs() }
            val dest = File(dir, file.name)
            file.copyTo(dest, overwrite = true)
            file.delete()
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
        }
    }

    companion object { private const val TAG = "RtspRecorder" }
}
