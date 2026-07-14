package com.daotranbang.vfsmart.autolink

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gates the "turn on your lights" voice reminder (MirrorScreen speed cell,
 * `R.raw.light_reminder`) to at most once per Android Auto session. [reset] is called
 * when a session starts (the `CarConnection` PROJECTION event in [AutoLinkService]); the
 * first [claim] afterwards returns true (play it), every later call in the same session
 * returns false.
 */
object LightReminderSession {
    private val played = AtomicBoolean(false)

    /** Start a fresh session — the next [claim] will play. */
    fun reset() = played.set(false)

    /** Returns true exactly once per session (claims the single play). */
    fun claim(): Boolean = played.compareAndSet(false, true)
}
