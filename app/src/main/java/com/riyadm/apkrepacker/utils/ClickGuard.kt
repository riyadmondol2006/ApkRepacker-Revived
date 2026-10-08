package com.riyadm.apkrepacker.utils

import android.os.SystemClock

/** Swallows repeated clicks that arrive within [windowMs] of the last accepted one (double taps). */
class ClickGuard(private val windowMs: Long = 700L) {
    private var last = 0L

    fun allow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (last != 0L && now - last < windowMs) return false
        last = now
        return true
    }
}
