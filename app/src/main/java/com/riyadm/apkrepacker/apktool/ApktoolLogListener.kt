package com.riyadm.apkrepacker.apktool

import java.util.logging.Level

/**
 * Receives apktool's log output (and the app's own progress lines) while a decode/build runs.
 * May be called from apktool's worker threads and from the aapt2 output reader threads.
 */
fun interface ApktoolLogListener {
    fun onLog(level: Level, message: String)

    companion object {
        /** "I", "W", "E" or "D", the prefix the log screens use for a line. */
        @JvmStatic
        fun prefix(level: Level): String = when {
            level.intValue() >= Level.SEVERE.intValue() -> "E"
            level.intValue() >= Level.WARNING.intValue() -> "W"
            level.intValue() >= Level.INFO.intValue() -> "I"
            else -> "D"
        }

        /** Formats a line the way the old apktool fork did: "I: message". */
        @JvmStatic
        fun format(level: Level, message: String): String = "${prefix(level)}: $message"
    }
}

/** Failure of a decode, build, sign or framework install, with a message meant for the user. */
class ApktoolException(message: String, cause: Throwable? = null) : Exception(message, cause)
