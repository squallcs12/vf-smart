package com.daotranbang.vfsmart.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.daotranbang.vfsmart.R

/**
 * Plays the "turn your lights on" voice reminder once, ducking other audio via
 * a transient navigation-guidance audio-focus request. Shared by the MirrorScreen
 * speed cell (night auto-trigger) and the HomeScreen debug button.
 */
fun playLightReminder(context: Context) {
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    val audioManager = context.getSystemService(AudioManager::class.java)
    val focusRequest = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener {}
        .build()
    if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
    try {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            val afd = context.resources.openRawResourceFd(R.raw.light_reminder)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            setOnPreparedListener { it.start() }
            setOnCompletionListener {
                it.release()
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
            prepareAsync()
        }
    } catch (e: Exception) {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
