package com.daotranbang.vfsmart.autolink

import android.content.Context

/**
 * Tracks the user's affirmative consent to the prominent disclosure that the app
 * shows before it uses the Android Accessibility Service to automate AutoLink Pro.
 *
 * Google Play policy requires a prominent in-app disclosure for any use of
 * [android.permission.BIND_ACCESSIBILITY_SERVICE]. The accessibility service must
 * not be activated until the user has seen that disclosure and agreed, so
 * [enableViaRoot][AutoLinkAccessibilityService.enableViaRoot] consults
 * [isAccepted] and does nothing until consent is given.
 */
object AccessibilityDisclosure {

    private const val PREFS = "accessibility_disclosure"
    private const val KEY_ACCEPTED = "accepted"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the user has accepted the accessibility-usage disclosure. */
    fun isAccepted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACCEPTED, false)

    fun setAccepted(context: Context, accepted: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACCEPTED, accepted).apply()
    }
}
