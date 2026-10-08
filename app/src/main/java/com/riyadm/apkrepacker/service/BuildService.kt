package com.riyadm.apkrepacker.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.riyadm.apkrepacker.utils.JobWakeLock
import com.riyadm.apkrepacker.utils.NotificationHelper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.jecelyin.common.utils.DLog
import com.jecelyin.common.utils.IOUtils
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.task.BuildTask
import com.riyadm.apkrepacker.ui.apkbuilder.IBuilderCallback
import com.riyadm.apkrepacker.ui.apkbuilder.TaskStepInfo
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.SignUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.logging.Level

class BuildService : Service(), IBuilderCallback, ApktoolLogListener {

    companion object {
        const val CHANNEL_ID = NotificationHelper.CHANNEL_BUILD
        const val CHANNEL_NAME = "Build Apk"
        const val NOTIFICATION_ID = 1
    }

    private val LINE_SEPARATOR_WIN = "\r\n"
    private val binder = LocalBinder()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.IO + job)
    private var mProjectDir: String? = ""
    private var wakeLock: JobWakeLock? = null

    private val mCompileLogMutable = StringBuilder()
    var compileLog: String? = ""

    private val mStepMutable = MutableLiveData<String>()
    val stepInfo: LiveData<String> = mStepMutable

    private val mTimeMutable = MutableLiveData<Long>(0)
    val time: LiveData<Long> = mTimeMutable

    private val mFaliedMutable = MutableLiveData<String?>()
    val falied: LiveData<String?> = mFaliedMutable

    private val mSuccessMutable = MutableLiveData<File?>()
    val success: LiveData<File?> = mSuccessMutable

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        wakeLock = JobWakeLock(this, "build")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        job.start()
        mProjectDir = intent?.getStringExtra("projectDir")
        // Started with startForegroundService(): go foreground right away, every time.
        addNotification(getString(R.string.title_build_apk), startForeground = true)
        val projectDir = mProjectDir
        if (projectDir.isNullOrEmpty()) {
            taskFailed("No project to build")
            return START_NOT_STICKY
        }
        synchronized(mCompileLogMutable) { mCompileLogMutable.setLength(0) }
        compileLog = ""
        wakeLock?.acquire()
        uiScope.launch {
            val options = ApktoolOptionsStore.loadBuildOptions(baseContext)
            val build = Runnable {
                if (options.sign) {
                    var started = false
                    SignUtil.loadKey(baseContext) { signTool: SignUtil? ->
                        started = true
                        BuildTask(baseContext, signTool, this@BuildService, this@BuildService, options).execute(
                            File(projectDir)
                        )
                    }
                    // The default key loads synchronously; a custom one may still be asking for its password.
                    if (!started && !PreferenceHelper.getInstance(baseContext).isCustomSign) {
                        taskFailed("Could not load the signing key")
                    }
                } else {
                    BuildTask(baseContext, null, this@BuildService, this@BuildService, options).execute(File(projectDir))
                }
            }
            build.run()
        }
        return START_NOT_STICKY
    }

    private var mInForeground = false

    /**
     * Ongoing progress notification: determinate once the step count is known, indeterminate
     * before. Tapping it brings the existing task back.
     */
    private fun buildNotification(message: String, step: Int, stepTotal: Int): Notification {
        val openApp = NotificationHelper.openAppIntent(this)
        return NotificationHelper.progressBuilder(this, CHANNEL_ID, R.drawable.ic_m3_notif_build)
            .setContentTitle(getString(R.string.title_build_apk))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(stepTotal, step, stepTotal <= 0)
            .setContentIntent(openApp)
            .apply {
                if (openApp != null) addAction(0, getString(R.string.notification_action_open), openApp)
            }
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Goes foreground the first time (dataSync: local file processing), then just updates the
     * notification. Without POST_NOTIFICATIONS (API 33+) the service still runs; the
     * notification is only hidden from the shade.
     */
    private fun addNotification(message: String, startForeground: Boolean = false, step: Int = 0, stepTotal: Int = 0) {
        val notification = buildNotification(message, step, stepTotal)
        if (startForeground || !mInForeground) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                )
                mInForeground = true
            } catch (e: IllegalStateException) {
                // API 31+: ForegroundServiceStartNotAllowedException (app in background, or the
                // dataSync time limit of API 35+ is used up). Keep running as a plain service.
                DLog.e("BuildService", e)
            }
        } else if (NotificationHelper.canPostNotifications(this)) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked meanwhile.
            }
        }
    }

    private fun leaveForeground() {
        wakeLock?.release()
        if (mInForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            mInForeground = false
        }
    }

    /**
     * Android 15+: dataSync foreground services may run 6 hours per day. When the system calls
     * this the service must stop right away or the app is ANR'd.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job.cancel()
        mFaliedMutable.postValue(getString(R.string.notification_build_timeout))
        mSuccessMutable.postValue(null)
        leaveForeground()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private fun createNotificationChannel() {
        NotificationHelper.createChannels(this)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BuildService = this@BuildService
    }

    /** Build log line from apktool, aapt2 or the signer, saved to compile_log.txt. */
    override fun onLog(level: Level, message: String) {
        val line = ApktoolLogListener.format(level, message)
        synchronized(mCompileLogMutable) {
            mCompileLogMutable.append(line)
            mCompileLogMutable.append(LINE_SEPARATOR_WIN)
        }
        when {
            level.intValue() >= Level.SEVERE.intValue() -> DLog.e("BuildService", line)
            level.intValue() >= Level.WARNING.intValue() -> DLog.w("BuildService", line)
            else -> DLog.i("BuildService", line)
        }
    }

    override fun taskTime(time: Long) {
        mTimeMutable.postValue(time)
    }

    override fun setTaskStepInfo(taskStepInfo: TaskStepInfo?) {
        val desc = String.format(
            getString(R.string.step),
            taskStepInfo?.stepIndex?.let { Integer.valueOf(it) },
            taskStepInfo?.stepTotal?.let { Integer.valueOf(it) },
            taskStepInfo?.stepDescription
        )

        addNotification(desc, step = taskStepInfo?.stepIndex ?: 0, stepTotal = taskStepInfo?.stepTotal ?: 0)
        mStepMutable.postValue(desc)
    }

    override fun taskSucceed(file: File?) {
        saveCompileLog()
        mSuccessMutable.postValue(file)
        leaveForeground()
        showCompletion(true, getString(R.string.notification_build_done_title), file?.name ?: getString(R.string.build_successful))
    }

    /** Called once per failed build with the error (aapt2's error lines included). */
    override fun taskFailed(str: String?) {
        compileLog = str
        saveCompileLog()
        mFaliedMutable.postValue(str)
        mSuccessMutable.postValue(null)
        leaveForeground()
        showCompletion(false, getString(R.string.notification_build_failed_title), str ?: getString(R.string.error_build_failed))
    }

    /** One-shot "finished" notification so the user is told the result when the app is backgrounded. */
    private fun showCompletion(success: Boolean, title: String, text: String) {
        if (!NotificationHelper.canPostNotifications(this)) return
        val icon = if (success) R.drawable.ic_m3_notif_done else R.drawable.ic_m3_notif_error
        val notification = NotificationHelper.completionBuilder(this, icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(NotificationHelper.openAppIntent(this))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID + 100, notification)
        } catch (e: SecurityException) {
            // Permission revoked meanwhile.
        }
    }

    private fun saveCompileLog() {
        val text = synchronized(mCompileLogMutable) { mCompileLogMutable.toString() }
        try {
            IOUtils.writeFile(
                File(PreferenceHelper.getInstance(baseContext).decodingPath + "/" + "compile_log.txt"),
                text
            )
        } catch (e: Exception) {
            DLog.e("BuildService", e)
        }
    }

}