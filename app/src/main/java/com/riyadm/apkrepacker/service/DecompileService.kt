package com.riyadm.apkrepacker.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.DecodeOptionsArgs
import com.riyadm.apkrepacker.task.DecodeSink
import com.riyadm.apkrepacker.task.DecodeTask
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.JobWakeLock
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.viewmodel.projects.ProjectLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The state of one decompile run: its log and its outcome. It lives in [DecompileService] (not in
 * a screen or ViewModel), so a screen that is closed, rotated or recreated just attaches to the
 * same session again instead of starting a second decompile. Log lines and [finished] change on
 * the main thread only.
 */
class DecompileSession internal constructor(val runId: String) : DecodeSink {

    /** Log lines in arrival order. */
    val lines = ArrayList<String>()

    private val _lineCount = MutableStateFlow(0)
    val lineCount: StateFlow<Int> = _lineCount

    /** The outcome of a finished run; [Outcome.project] is null if it failed. Null while running. */
    private val _finished = MutableStateFlow<Outcome?>(null)
    val finished: StateFlow<Outcome?> = _finished

    data class Outcome(val project: File?)

    internal var listener: Listener? = null

    /** Set by [DecompileService.forget] when the screen is closed for good while the run may still go on. */
    @Volatile
    internal var screenGone = false

    internal interface Listener {
        fun onLine(line: String)
        fun onFinished(project: File?)
    }

    override fun onDecodeLog(line: CharSequence) {
        val text = line.toString()
        lines += text
        _lineCount.value = lines.size
        listener?.onLine(text)
    }

    override fun onDecodeFinished(result: File?) {
        // A run ends once: a late result after the service timed it out must not contradict that.
        if (_finished.value != null) return
        _finished.value = Outcome(result)
        val l = listener
        // The service must not stay reachable from the session (nor the session from a dead screen).
        listener = null
        l?.onFinished(result)
        if (screenGone) DecompileService.drop(runId)
    }
}

/**
 * Decompiles an APK in a dataSync foreground service. Before this, decompile ran in the screen's
 * ViewModel with nothing keeping the process important: leaving the app mid-decompile let Android
 * kill it (the app "reloaded" with the work lost), and a recreated screen started a second run.
 * Now the work has a progress notification and a wake lock, survives the screen going away, and
 * ends with a "finished" notification.
 */
class DecompileService : Service(), DecompileSession.Listener {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var wakeLock: JobWakeLock? = null
    private var inForeground = false
    private var apkName: String = ""
    private var lastNotify = 0L
    private var activeRun: String? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        wakeLock = JobWakeLock(this, "decompile")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runId = intent?.getStringExtra(EXTRA_RUN_ID)
        val apkPath = intent?.getStringExtra(EXTRA_APK)
        // Started with startForegroundService(): go foreground right away, every time.
        post(getString(R.string.decompile_notification_starting), startForeground = true)

        val session = runId?.let { sessions[it] }
        val options = DecodeOptionsArgs.fromBundle(intent?.getBundleExtra(EXTRA_OPTIONS))
        if (session == null || apkPath.isNullOrEmpty() || options == null) {
            stopSelfSafely(startId)
            return START_NOT_STICKY
        }
        if (activeRun != null) {
            // One decompile at a time; a second request waits behind the first.
            pendingStarts += Triple(session, File(apkPath), options)
            return START_NOT_STICKY
        }
        begin(session, File(apkPath), options)
        return START_NOT_STICKY
    }

    private val pendingStarts = ArrayDeque<Triple<DecompileSession, File, DecodeOptions>>()

    private fun begin(session: DecompileSession, apk: File, options: DecodeOptions) {
        activeRun = session.runId
        session.listener = this
        wakeLock?.acquire()
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching { AppUtils.getApkName(applicationContext, apk.absolutePath).replace("/", "_") }.getOrNull()
            }
            apkName = name ?: apk.nameWithoutExtension
            post(getString(R.string.decompile_notification_running, apkName))
            try {
                DecodeTask(applicationContext, options, name, session).execute(apk)
            } catch (e: Throwable) {
                DLog.e("DecompileService", e)
                session.onDecodeFinished(null)
            }
        }
    }

    override fun onLine(line: String) {
        val now = System.currentTimeMillis()
        // The system rate-limits notification updates; a log line every ~600 ms is plenty.
        if (now - lastNotify >= 600) {
            lastNotify = now
            post(getString(R.string.decompile_notification_running, apkName), detail = line)
        }
    }

    override fun onFinished(project: File?) {
        ProjectLoader.getInstance(applicationContext).loadProjects()
        publishResult(project)
        activeRun = null
        // Pair this run's acquire with its release before begin() acquires again for the next one.
        wakeLock?.release()
        val next = pendingStarts.removeFirstOrNull()
        if (next != null) {
            begin(next.first, next.second, next.third)
        } else {
            leaveForeground()
            stopSelf()
        }
    }

    // region notifications
    private fun buildNotification(text: String, detail: String?): Notification {
        val openApp = NotificationHelper.openAppIntent(this)
        return NotificationHelper.progressBuilder(this, NotificationHelper.CHANNEL_DECOMPILE, R.drawable.ic_m3_notif_build)
            .setContentTitle(getString(R.string.decompile_notification_title))
            .setContentText(text)
            .apply { if (!detail.isNullOrEmpty()) setSubText(detail.take(60)) }
            .setProgress(0, 0, true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun post(text: String, startForeground: Boolean = false, detail: String? = null) {
        val notification = buildNotification(text, detail)
        if (startForeground || !inForeground) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                )
                inForeground = true
            } catch (e: IllegalStateException) {
                // API 31+: not allowed to start from the background; keep running as a plain service.
                DLog.e("DecompileService", e)
            }
        } else if (NotificationHelper.canPostNotifications(this)) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked meanwhile.
            }
        }
    }

    private fun publishResult(project: File?) {
        if (!NotificationHelper.canPostNotifications(this)) return
        val ok = project != null
        val text = if (ok) getString(R.string.decompile_notification_done, apkName) else getString(R.string.decompile_notification_failed, apkName)
        val notification = NotificationHelper.completionBuilder(this, if (ok) R.drawable.ic_m3_notif_done else R.drawable.ic_m3_notif_error)
            .setContentTitle(getString(if (ok) R.string.decompile_done_title else R.string.decompile_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(NotificationHelper.openAppIntent(this))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(DONE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked meanwhile.
        }
    }

    private fun leaveForeground() {
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
    }

    private fun stopSelfSafely(startId: Int) {
        if (activeRun == null) {
            leaveForeground()
            stopSelf(startId)
        }
    }

    /** Android 15+: dataSync services may run 6 hours a day; stop right away when told to. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        // The decode itself can't be interrupted; finishing the session now makes its late result
        // a no-op (see DecompileSession.onDecodeFinished). onFinished() releases the wake lock
        // of the running decode; the queue is dropped and the service stops.
        pendingStarts.clear()
        activeRun?.let { sessions[it]?.onDecodeFinished(null) }
        if (activeRun != null) {
            // The session was already over or gone, so onFinished() did not run.
            activeRun = null
            wakeLock?.release()
        }
        leaveForeground()
        stopSelf()
    }
    // endregion

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        job.cancel()
    }

    companion object {
        private const val EXTRA_RUN_ID = "runId"
        private const val EXTRA_APK = "apk"
        private const val EXTRA_OPTIONS = "options"
        private const val NOTIFICATION_ID = 7
        private const val DONE_NOTIFICATION_ID = 8

        /** Sessions by run id; kept for the life of the process so a recreated screen can re-attach. */
        private val sessions = ConcurrentHashMap<String, DecompileSession>()

        /** The session of run [runId], if one was started in this process. */
        @JvmStatic
        fun session(runId: String): DecompileSession? = sessions[runId]

        /**
         * Starts a decompile run (once per [runId]) and returns its session. Calling it again with
         * the same id, e.g. from a recreated screen, returns the existing session without
         * starting another run.
         */
        @JvmStatic
        fun start(context: Context, runId: String, apk: File, options: DecodeOptions): DecompileSession {
            sessions[runId]?.let { return it }
            val session = DecompileSession(runId)
            val existing = sessions.putIfAbsent(runId, session)
            if (existing != null) return existing
            val intent = Intent(context, DecompileService::class.java)
                .putExtra(EXTRA_RUN_ID, runId)
                .putExtra(EXTRA_APK, apk.absolutePath)
                .putExtra(EXTRA_OPTIONS, DecodeOptionsArgs.toBundle(options))
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: IllegalStateException) {
                // API 31+: the app is in the background. Run it in-process anyway (no notification).
                DLog.e("DecompileService", e)
                runWithoutService(context.applicationContext, session, apk, options)
            }
            return session
        }

        private fun runWithoutService(context: Context, session: DecompileSession, apk: File, options: DecodeOptions) {
            DecodeTask(context, options, null, session).execute(apk)
        }

        /**
         * Forgets a run once its screen is closed for good: a finished run goes now, a running one
         * as soon as it finishes (nobody is left to show it). Rotation never calls this.
         */
        @JvmStatic
        fun forget(runId: String) {
            val session = sessions[runId] ?: return
            session.screenGone = true
            if (session.finished.value != null) sessions.remove(runId)
        }

        internal fun drop(runId: String) {
            sessions.remove(runId)
        }
    }
}
