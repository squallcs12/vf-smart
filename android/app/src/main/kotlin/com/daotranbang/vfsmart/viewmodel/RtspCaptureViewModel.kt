package com.daotranbang.vfsmart.viewmodel

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.daotranbang.vfsmart.data.ImouScanner
import com.daotranbang.vfsmart.data.local.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RtspCaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    sealed class RecordState {
        object Idle : RecordState()
        object Recording : RecordState()
        object Saving : RecordState()
        data class Saved(val message: String) : RecordState()
        data class Error(val message: String) : RecordState()
    }

    private val _rtspUrl = MutableStateFlow(securePreferences.getRtspUrl() ?: "")
    val rtspUrl: StateFlow<String> = _rtspUrl.asStateFlow()

    private val _recordState = MutableStateFlow<RecordState>(RecordState.Idle)
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    sealed class ScanState {
        object Idle : ScanState()
        object Scanning : ScanState()
        data class Found(val ip: String) : ScanState()
        object NotFound : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var timerJob: Job? = null
    private var scanJob: Job? = null
    private var activeSessionId: Long = -1L
    private var tempFile: File? = null

    fun setRtspUrl(url: String) {
        _rtspUrl.value = url.trim()
        securePreferences.saveRtspUrl(url.trim())
    }

    /**
     * Scans the local subnet(s) for an Imou camera (see [ImouScanner]). On success
     * the detected IP is spliced into the RTSP URL, preserving any credentials the
     * user already entered.
     */
    fun detectCamera() {
        if (_scanState.value is ScanState.Scanning) return
        _scanState.value = ScanState.Scanning
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val ip = ImouScanner.scan()
            if (ip != null) {
                val url = ImouScanner.buildRtspUrl(ip, _rtspUrl.value)
                _rtspUrl.value = url
                securePreferences.saveRtspUrl(url)
                _scanState.value = ScanState.Found(ip)
            } else {
                _scanState.value = ScanState.NotFound
            }
        }
    }

    fun acknowledgeScan() {
        _scanState.value = ScanState.Idle
    }

    fun startRecording() {
        val url = _rtspUrl.value
        if (url.isEmpty()) {
            _recordState.value = RecordState.Error("Vui lòng nhập địa chỉ RTSP")
            return
        }
        if (_recordState.value == RecordState.Recording) return

        tempFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "vf3_${System.currentTimeMillis()}.mp4"
        )

        _elapsedSeconds.value = 0L
        _recordState.value = RecordState.Recording

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _elapsedSeconds.update { it + 1 }
            }
        }

        val session = FFmpegKit.executeAsync(
            "-rtsp_transport tcp -i \"$url\" -c copy -y \"${tempFile!!.absolutePath}\""
        ) { completed ->
            timerJob?.cancel()
            val rc = completed.returnCode
            when {
                ReturnCode.isSuccess(rc) || ReturnCode.isCancel(rc) -> saveRecording()
                else -> _recordState.value = RecordState.Error("Lỗi ghi hình: ${completed.failStackTrace?.take(120)}")
            }
        }
        activeSessionId = session.sessionId
    }

    fun stopRecording() {
        timerJob?.cancel()
        if (activeSessionId != -1L) {
            FFmpegKit.cancel(activeSessionId)
            activeSessionId = -1L
        }
        _recordState.value = RecordState.Saving
    }

    private fun saveRecording() {
        val file = tempFile
        if (file == null || !file.exists() || file.length() == 0L) {
            _recordState.value = RecordState.Error("File ghi hình trống hoặc không tồn tại")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VF3Smart")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val uri: Uri? = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv
                    )
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                        cv.clear()
                        cv.put(MediaStore.Video.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, cv, null, null)
                        file.delete()
                        _recordState.value = RecordState.Saved("Đã lưu vào thư viện")
                    } else {
                        _recordState.value = RecordState.Error("Không thể tạo mục trong thư viện")
                    }
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "VF3Smart"
                    ).also { it.mkdirs() }
                    val dest = File(dir, file.name)
                    file.copyTo(dest, overwrite = true)
                    file.delete()
                    MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                    _recordState.value = RecordState.Saved("Đã lưu: ${dest.name}")
                }
            } catch (e: Exception) {
                _recordState.value = RecordState.Error("Lỗi lưu: ${e.message}")
            }
        }
    }

    fun acknowledgeResult() {
        _recordState.value = RecordState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        scanJob?.cancel()
        if (activeSessionId != -1L) FFmpegKit.cancel(activeSessionId)
    }
}
