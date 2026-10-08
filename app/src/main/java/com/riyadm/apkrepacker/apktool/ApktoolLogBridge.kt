package com.riyadm.apkrepacker.apktool

import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * Forwards apktool's java.util.logging output to the listeners of the running operations.
 *
 * apktool logs through `brut.common.Log`, i.e. JUL loggers named after its classes ("brut....").
 * The aapt2 stdout/stderr lines are logged by `brut.util.OS` with an empty tag, i.e. on the root
 * logger, from separate reader threads. A single handler on the root logger therefore sees all of
 * it; it is installed once and only dispatches while at least one [Session] is open.
 */
internal object ApktoolLogBridge {

    private val sessions = CopyOnWriteArraySet<Session>()
    private val formatter = SimpleFormatter()

    // LogManager only keeps weak references to loggers: hold the root logger strongly so the
    // handler can't disappear with it.
    private val rootLogger: Logger = Logger.getLogger("")

    private val handler = object : Handler() {
        override fun publish(record: LogRecord?) {
            if (record == null || sessions.isEmpty()) return
            val name = record.loggerName ?: ""
            if (name.isNotEmpty() && !name.startsWith("brut.") && !name.startsWith("com.android.tools.smali")) {
                return
            }
            val message = try {
                formatter.formatMessage(record)
            } catch (e: Exception) {
                record.message
            } ?: return
            val thrown = record.thrown
            val text = if (thrown != null) "$message: ${ApktoolEngine.describe(thrown)}" else message
            for (session in sessions) {
                session.publish(record.level ?: Level.INFO, name, text)
            }
        }

        override fun flush() {}

        override fun close() {}
    }

    @Volatile
    private var installed = false

    private fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            handler.level = Level.ALL
            rootLogger.addHandler(handler)
            installed = true
        }
    }

    fun open(listener: ApktoolLogListener?): Session {
        install()
        val session = Session(listener)
        sessions.add(session)
        return session
    }

    /** One decode/build: collects the aapt2 output so a failure can be explained. */
    class Session(private val listener: ApktoolLogListener?) : AutoCloseable {
        private val toolOutput = ArrayDeque<String>()

        internal fun publish(level: Level, loggerName: String, message: String) {
            if (loggerName.isEmpty()) {
                // Output of an external tool (aapt2).
                synchronized(toolOutput) {
                    toolOutput.addLast(message)
                    while (toolOutput.size > MAX_TOOL_LINES) toolOutput.removeFirst()
                }
            }
            try {
                listener?.onLog(level, message)
            } catch (ignored: Exception) {
                // A broken listener must not break apktool.
            }
        }

        /** Error lines printed by aapt2 (or its last lines if none look like an error). */
        fun toolErrors(): List<String> {
            val lines = synchronized(toolOutput) { toolOutput.toList() }
            val errors = lines.filter { it.contains("error", ignoreCase = true) }
            return errors.ifEmpty { lines.takeLast(10) }
        }

        override fun close() {
            sessions.remove(this)
        }
    }

    private const val MAX_TOOL_LINES = 200
}
