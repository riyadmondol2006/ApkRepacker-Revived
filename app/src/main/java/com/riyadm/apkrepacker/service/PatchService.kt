package com.riyadm.apkrepacker.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolEngine
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.apktool.ProjectMeta
import com.riyadm.apkrepacker.apktool.DecodeMode
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.utils.JobWakeLock
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.utils.manifestparser.SdkConstants
import com.riyadm.apkrepacker.utils.manifestparser.xml.AndroidManifestParser
import com.riyadm.patchengine.PatchExecutor
import com.riyadm.patchengine.rules.PatchRuleMatchReplace
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.interfaces.IRulesInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.zip.ZipFile

/**
 * Applies `.zip` patches to the open project, in order, on a background coroutine. Modeled on
 * [BuildService]: a dataSync foreground service with an ongoing progress notification, a
 * completion notification when it finishes, and a [LocalBinder] + LiveData the Patcher screen
 * observes. Running here (not on the UI thread, where it used to) means patching no longer freezes
 * the app, survives rotation/backgrounding, and keeps going with the screen off.
 */
class PatchService : Service(), IPatchContext, IRulesInfo {

    companion object {
        const val NOTIFICATION_ID = 3
        const val DONE_NOTIFICATION_ID = 4

        const val EXTRA_PROJECT_DIR = "projectDir"
        const val EXTRA_PATCH_PATHS = "patchPaths"
        const val EXTRA_DECODE_SMALI = "decodeSmali"

        private const val MAX_LOG_CHARS = 200_000
        private const val LOG_FLUSH_MS = 200L

        /** Advanced switch: false runs every rule on its own pass, as before single-pass execution. */
        const val PREF_SINGLE_PASS = "pref_patch_single_pass"
    }

    private val binder = LocalBinder()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.IO + job)
    private var mInForeground = false

    private val globalVariables = HashMap<String?, String?>()
    private val logBuffer = StringBuilder()

    private lateinit var projectHelper: ProjectHelper
    private var wakeLock: JobWakeLock? = null

    /** The patch being applied right now (GOTO / MATCH_GOTO validate their target against its rule names). */
    @Volatile
    private var currentExecutor: PatchExecutor? = null

    /** Number of error() lines logged during this run. */
    private val errorCount = AtomicInteger(0)

    private val mLog = MutableLiveData<String>()
    val logLiveData: LiveData<String> get() = mLog

    private val mPatchSize = MutableLiveData(0)
    val patchCount: LiveData<Int> get() = mPatchCount
    private val mPatchCount = MutableLiveData(0)
    val patchSize: LiveData<Int> get() = mPatchSize

    private val mRulesSize = MutableLiveData(0)
    val patchRulesSize: LiveData<Int> get() = mRulesSize
    private val mCurrentRules = MutableLiveData(0)
    val patchCurrentRules: LiveData<Int> get() = mCurrentRules

    private val mDone = MutableLiveData(false)
    /** Flips to true once every patch has been applied (or the run was stopped). */
    val done: LiveData<Boolean> get() = mDone

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        wakeLock = JobWakeLock(this, "patch")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        job.start()
        val projectDir = intent?.getStringExtra(EXTRA_PROJECT_DIR)
        val patchPaths = intent?.getStringArrayListExtra(EXTRA_PATCH_PATHS).orEmpty()
        val decodeSmali = intent?.getBooleanExtra(EXTRA_DECODE_SMALI, false) ?: false

        addNotification(getString(R.string.title_patcher), startForeground = true)

        // One run at a time: a second start (double tap, or a tap after rotating mid-run) would reset the
        // log and project state under the run that is still patching the same project.
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY

        if (projectDir.isNullOrEmpty() || patchPaths.isEmpty()) {
            finish(success = false, summary = getString(R.string.patcher_nothing_to_do))
            return START_NOT_STICKY
        }

        PatchRuleMatchReplace.batchingEnabled =
            PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_SINGLE_PASS, true)
        projectHelper = buildProjectHelper(projectDir)
        synchronized(logBuffer) { logBuffer.setLength(0) }
        mLog.postValue("")
        mPatchSize.postValue(patchPaths.size)
        mPatchCount.postValue(0)
        mDone.postValue(false)
        errorCount.set(0)

        wakeLock?.acquire()
        uiScope.launch {
            try {
                if (decodeSmali) decodeSmaliInto(projectDir)
                patchPaths.forEachIndexed { index, path ->
                    mRulesSize.postValue(0)
                    mCurrentRules.postValue(0)
                    val name = File(path).name
                    appendInfo(getString(R.string.patcher_applying_patch, name), bold = true)
                    addNotification(getString(R.string.patcher_applying_n_of_m, index + 1, patchPaths.size), step = index, stepTotal = patchPaths.size)
                    val executor = PatchExecutor(projectHelper, path, this@PatchService, this@PatchService)
                    currentExecutor = executor
                    try {
                        executor.applyPatch()
                    } finally {
                        currentExecutor = null
                    }
                    mPatchCount.postValue(index + 1)
                }
                val errors = errorCount.get()
                if (errors > 0) {
                    finish(success = false, summary = resources.getQuantityString(R.plurals.patcher_done_with_errors_summary, errors, errors))
                } else {
                    finish(success = true, summary = resources.getQuantityString(R.plurals.patcher_done_summary, patchPaths.size, patchPaths.size))
                }
            } catch (e: Throwable) {
                DLog.e("PatchService", e)
                appendText(getString(R.string.general_error, e.message) + "\n")
                finish(success = false, summary = getString(R.string.patcher_failed_summary))
            }
        }
        return START_NOT_STICKY
    }

    private fun buildProjectHelper(projectDir: String): ProjectHelper = ProjectHelper().apply {
        mContext = applicationContext
        mProject = projectDir
        mDataPath = applicationContext.filesDir.path
        mCache = applicationContext.externalCacheDir
        mSmaliCliced = true
        // Set the source apk + package so rules like SIGNATURE_REVISE / EXECUTE_DEX have them
        // (they used to be null, which crashed those rules on the worker thread).
        mApkPath = ProjectMeta.getString(ProjectMeta.read(File(projectDir)), "apkFilePatch")
        // Parse from projectDir directly: the projectHelper field isn't assigned yet here, so
        // getApplicationManifest() (which reads it) can't be used during construction.
        mApkPackage = runCatching {
            AndroidManifestParser.parse(File(projectDir + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML)).`package`
        }.getOrNull()
    }

    /**
     * Decodes only the smali from the project's source apk into a temp folder and moves the
     * `smali*` folders into the project, leaving already-decoded resources untouched.
     */
    private fun decodeSmaliInto(projectDir: String) {
        val apkPath = ProjectMeta.getString(ProjectMeta.read(File(projectDir)), "apkFilePatch")
        val apk = apkPath?.let { File(it) }
        if (apk == null || !apk.isFile) {
            appendInfo(getString(R.string.patcher_decode_no_source), bold = true)
            return
        }
        appendInfo(getString(R.string.patcher_decoding_smali), bold = true)
        val temp = File(applicationContext.externalCacheDir ?: applicationContext.cacheDir, "patch_smali_" + System.currentTimeMillis())
        val log = ApktoolLogListener { level, message ->
            if (level.intValue() >= Level.INFO.intValue()) appendText(message + "\n")
        }
        try {
            ApktoolEngine.decode(this, apk, temp, DecodeOptions(mode = DecodeMode.SOURCES_ONLY, force = true), log)
            val project = File(projectDir)
            temp.listFiles()?.filter { it.isDirectory && (it.name == "smali" || it.name.startsWith("smali_")) }?.forEach { src ->
                val dest = File(project, src.name)
                if (!dest.exists()) FileUtils.moveDirectory(src, dest) else FileUtils.copyDirectory(src, dest)
            }
            appendInfo(getString(R.string.patcher_decoded_smali), bold = false)
        } catch (e: Throwable) {
            DLog.e("PatchService", e)
            appendText(getString(R.string.general_error, e.message) + "\n")
        } finally {
            runCatching { FileUtils.deleteDirectory(temp) }
        }
    }

    private val running = AtomicBoolean(false)

    private fun finish(success: Boolean, summary: String) {
        running.set(false)
        appendInfo(summary, bold = true)
        mainHandler.post { flushLog() }
        wakeLock?.release()
        leaveForeground()
        showCompletion(success, summary)
        mDone.postValue(true)
        stopSelf()
    }

    // region notifications
    private fun buildNotification(message: String, step: Int, stepTotal: Int): Notification {
        val openApp = NotificationHelper.openAppIntent(this)
        return NotificationHelper.progressBuilder(this, NotificationHelper.CHANNEL_PATCH, R.drawable.ic_m3_notif_build)
            .setContentTitle(getString(R.string.title_patcher))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(stepTotal, step, stepTotal <= 0)
            .setContentIntent(openApp)
            .apply { if (openApp != null) addAction(0, getString(R.string.notification_action_open), openApp) }
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

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
                DLog.e("PatchService", e)
            }
        } else if (NotificationHelper.canPostNotifications(this)) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked meanwhile.
            }
        }
    }

    private fun showCompletion(success: Boolean, text: String) {
        if (!NotificationHelper.canPostNotifications(this)) return
        val icon = if (success) R.drawable.ic_m3_notif_done else R.drawable.ic_m3_notif_error
        val title = getString(if (success) R.string.patcher_done_title else R.string.patcher_failed_title)
        val notification = NotificationHelper.completionBuilder(this, icon)
            .setContentTitle(title)
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
        if (mInForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            mInForeground = false
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        job.cancel()
        appendInfo(getString(R.string.notification_build_timeout), bold = true)
        mainHandler.post { flushLog() }
        wakeLock?.release()
        leaveForeground()
        mDone.postValue(true)
        stopSelf()
    }
    // endregion

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        job.cancel()
    }

    inner class LocalBinder : Binder() {
        fun getService(): PatchService = this@PatchService
    }

    // region IPatchContext / IRulesInfo
    private fun parseManifest() =
        AndroidManifestParser.parse(File(getDecodeRootPath() + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML))

    override fun getActivities(): List<String>? = runCatching { parseManifest().activities.mapNotNull { it.name } }.getOrNull()

    /** The `<application android:name>` class (what `[APPLICATION]` targets need), or null when the app has no custom one. */
    override fun getApplicationManifest(): String? = runCatching {
        parseManifest().keepClasses.firstOrNull { it.type == "application" }?.name
    }.getOrNull()

    override fun getDecodeRootPath(): String? = projectHelper.getProjectPath()

    override fun getLauncherActivities(): List<String>? =
        runCatching { listOfNotNull(parseManifest().launcherActivity?.name) }.getOrNull()

    override fun getPatchNames(): List<String>? = currentExecutor?.getRuleNames()?.filterNotNull()

    override fun getSmaliFolders(): List<String> {
        val folders = mutableListOf("smali")
        runCatching {
            ZipFile(projectHelper.getApkPath()).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".dex") && '/' !in it && it != "classes.dex" }
                    .mapTo(folders) { "smali_" + it.removeSuffix(".dex") }
            }
        }
        return folders
    }

    override fun getVariableValue(str: String?): String? = globalVariables[str]

    override fun setVariableValue(key: String?, value: String?) {
        globalVariables[key] = value
    }

    override fun error(resourceId: Int, vararg objArr: Any?) {
        errorCount.incrementAndGet()
        appendText(safeFormat(getString(resourceId), objArr) + "\n")
    }

    override fun info(resourceId: Int, bold: Boolean, vararg objArr: Any?) {
        appendInfo(safeFormat(getString(resourceId), objArr), bold)
    }

    override fun info(str: String?, bold: Boolean, vararg objArr: Any?) {
        val text = str ?: ""
        appendInfo(if (objArr.isEmpty()) text else safeFormat(text, objArr), bold)
    }

    /** String.format that never throws: falls back to the raw text (it may hold '%' from a file path). */
    private fun safeFormat(format: String, args: Array<out Any?>): String =
        try {
            String.format(format, *args)
        } catch (e: Exception) {
            format
        }

    override fun patchFinished() {
        DLog.d("PatchService", "patch done")
    }

    override fun allRules(count: Int) {
        mRulesSize.postValue(count)
    }

    override fun currentRules(count: Int) {
        mCurrentRules.postValue(count)
    }

    private fun appendInfo(text: String, bold: Boolean) {
        appendText((if (bold) "\n" else "") + text + "\n")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val logFlushScheduled = AtomicBoolean(false)

    /**
     * Adds to the log. The screen gets a fresh copy of the text at most every 200 ms: copying the
     * whole log for every line (a patch can log thousands of replacements) is quadratic and was
     * the slowest part of a big patch. The log is also capped, so a huge run can't eat memory.
     */
    private fun appendText(text: String) {
        DLog.i("PatchLog", text.trim())
        synchronized(logBuffer) {
            logBuffer.append(text)
            if (logBuffer.length > MAX_LOG_CHARS) {
                logBuffer.delete(0, logBuffer.length - MAX_LOG_CHARS / 2)
                logBuffer.insert(0, "…\n")
            }
        }
        if (logFlushScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(::flushLog, LOG_FLUSH_MS)
        }
    }

    private fun flushLog() {
        logFlushScheduled.set(false)
        val snapshot = synchronized(logBuffer) { logBuffer.toString() }
        mLog.value = snapshot
    }
    // endregion
}
