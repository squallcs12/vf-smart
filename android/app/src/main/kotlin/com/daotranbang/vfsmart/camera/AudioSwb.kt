package com.daotranbang.vfsmart.camera

import android.util.Log

object AudioSwb {
    private const val TAG = "AudioSwb"

    // ioctl code the system AUX app sends to /dev/AUDIOSWB to enable AV-IN display mode
    private const val IOCTL_AV_ENABLE = 0x5404

    init {
        try {
            System.loadLibrary("vfsmart_native")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "native lib load failed: ${e.message}")
        }
    }

    private external fun nativeOpen(): Int
    private external fun nativeIoctl(fd: Int, request: Int, arg: Int): Boolean
    private external fun nativeClose(fd: Int)

    private var fd = -1

    fun enable(): Boolean {
        if (fd >= 0) return true
        fd = nativeOpen()
        if (fd < 0) {
            Log.e(TAG, "could not open /dev/AUDIOSWB")
            return false
        }
        val ok = nativeIoctl(fd, IOCTL_AV_ENABLE, 1)
        if (!ok) Log.e(TAG, "ioctl 0x5404 failed")
        return ok
    }

    fun disable() {
        nativeClose(fd)
        fd = -1
    }
}
