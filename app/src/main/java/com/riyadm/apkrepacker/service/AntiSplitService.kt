package com.riyadm.apkrepacker.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.antisplit.AntiSplit
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.project.ProjectTransfer
import com.riyadm.apkrepacker.utils.JobWakeLock
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.SignUtil
import com.riyadm.apkrepacker.utils.common.DLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

/**
 * Runs AntiSplit jobs in the background (dataSync foreground service, like the build service):
 * - [ACTION_SAVE]: merge the splits, sign the APK (unless [EXTRA_SIGN] is false) and write it where
 *   the user chose;
 * - [ACTION_PREPARE]: merge into an unsigned APK kept in the cache, for Decompile and Simple edit.
 *   A cached merge is reused while the app (or archive) hasn't changed.
 * The source is an installed app ([EXTRA_PACKAGE]) or a split-APK archive file ([EXTRA_ARCHIVE]).
 * Jobs run one at a time; each ends with a [Result] on [results] and a notification.
 */
class AntiSplitService : Service() {

    data class Result(
        val jobId: String,
        val success: Boolean,
        /** Ready to show. */
        val message: String,
        /** The prepared APK (PREPARE jobs). */
        val apk: File? = null,
        val warnings: List<String> = emptyList(),
    )

    /** The current step of a running job, for a progress dialog. */
    data class Progress(val jobId: String, val text: String)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val pending = AtomicInteger(0)
    private val latestStartId = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val doneIds = AtomicInteger(0)
    private var wakeLock: JobWakeLock? = null

    @Volatile
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        wakeLock = JobWakeLock(this, "antisplit")
        // Once per process, before its first job: what is left comes from a process that died mid-job.
        if (swept.compareAndSet(false, true)) sweepLeftovers(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId.set(startId)
        // Started with startForegroundService(): go foreground right away, every time.
        post(getString(R.string.antisplit_running), startForeground = true)

        val action = intent?.action
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        val source = Source.from(intent)
        if (action == null || jobId == null || source == null) {
            if (pending.get() == 0) stopWhenIdle()
            return START_NOT_STICKY
        }
        val output = intent.data
        val baseOnly = intent.getBooleanExtra(EXTRA_BASE_ONLY, false)
        val sign = intent.getBooleanExtra(EXTRA_SIGN, true)

        pending.incrementAndGet()
        wakeLock?.acquire()
        scope.launch {
            try {
                val result = serial.withLock { run(action, jobId, source, output, baseOnly, sign) }
                publish(result)
            } catch (e: CancellationException) {
                // Stopped before it could run (Android's time limit): say so instead of vanishing.
                if (action == ACTION_SAVE) output?.let { uri -> runCatching { DocumentsContract.deleteDocument(contentResolver, uri) } }
                publish(Result(jobId, false, getString(R.string.antisplit_failed, getString(R.string.antisplit_time_limit))))
                throw e
            } finally {
                wakeLock?.release()
                if (pending.decrementAndGet() == 0) mainHandler.post { if (pending.get() == 0) stopWhenIdle() }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopWhenIdle() {
        leaveForeground()
        stopSelf(latestStartId.get())
    }

    private fun run(action: String, jobId: String, source: Source, output: Uri?, baseOnly: Boolean, sign: Boolean): Result {
        val log = JobLog(jobId)
        return try {
            when (action) {
                ACTION_PREPARE -> {
                    val apk = if (baseOnly) baseOf(source, log) else prepared(source, log)
                    Result(jobId, true, getString(R.string.antisplit_prepared, apk.name), apk = apk, warnings = log.warnings)
                }
                ACTION_SAVE -> {
                    val target = requireNotNull(output) { "No destination" }
                    val name = save(source, target, sign, log)
                    // Mention "unsigned" unless the file name already does.
                    val unsignedNote = !sign && !name.contains("unsigned", ignoreCase = true)
                    Result(jobId, true, getString(if (unsignedNote) R.string.antisplit_saved_unsigned else R.string.antisplit_saved, name), warnings = log.warnings)
                }
                else -> Result(jobId, false, getString(R.string.antisplit_failed, action))
            }
        } catch (e: Throwable) {
            DLog.e("AntiSplit", e)
            if (action == ACTION_SAVE) output?.let { uri -> runCatching { DocumentsContract.deleteDocument(contentResolver, uri) } }
            val reason = (e as? AntiSplit.AntiSplitException)?.message ?: (e.message ?: e.javaClass.simpleName)
            Result(jobId, false, getString(R.string.antisplit_failed, reason))
        }
    }

    // region jobs

    /** Merged, unsigned APK of [source], from the cache when it is still current. */
    private fun prepared(source: Source, log: JobLog): File {
        val cached = cacheFile(this, source)
        if (cached != null && cached.isFile && cached.length() > 0) {
            log.onLog(Level.INFO, "Using the merged APK from the cache: ${cached.name}")
            return cached
        }
        val target = cached ?: File(File(workDir(this), ONE_OFF_PREFIX + System.currentTimeMillis()), "merged.apk")
        target.parentFile?.mkdirs()
        withInputs(source, log) { apks -> AntiSplit.merge(apks, target, log).also { log.warnings += it.warnings } }
        if (cached != null) pruneOlder(cached.parentFile!!)
        return target
    }

    /** Keeps only the newest cache folder of an app or archive ([keep]): older ones just fill the storage. */
    private fun pruneOlder(keep: File) {
        val prefix = keep.name.substringBeforeLast('@') + "@"
        keep.parentFile?.listFiles { f -> f.isDirectory && f.name.startsWith(prefix) && f != keep }
            ?.forEach { it.deleteRecursively() }
    }

    /** The base APK of an archive, extracted (an installed app's base needs no preparing). */
    private fun baseOf(source: Source, log: JobLog): File {
        return when (source) {
            is Source.Installed -> AntiSplit.installedApks(this, source.packageName).first()
            is Source.Archive -> {
                val name = ProjectTransfer.safeName(source.file.nameWithoutExtension) + "-base.apk"
                val dir = cacheFile(this, source)?.parentFile ?: File(workDir(this), ONE_OFF_PREFIX + System.currentTimeMillis())
                val target = File(dir, name)
                withInputs(source, log) { apks ->
                    val base = AntiSplit.findBase(apks) ?: throw AntiSplit.AntiSplitException("There is no base APK in ${source.file.name}")
                    dir.mkdirs()
                    val part = File(dir, "$name.part")
                    try {
                        base.copyTo(part, overwrite = true)
                        if (target.exists()) target.delete()
                        if (!part.renameTo(target)) throw java.io.IOException("Cannot write $target")
                    } finally {
                        part.delete()
                    }
                }
                if (!dir.name.startsWith(ONE_OFF_PREFIX)) pruneOlder(dir)
                log.onLog(Level.INFO, "Base APK: ${target.name}")
                target
            }
        }
    }

    /** Merges [source], signs it when [sign] is set, and writes it to [output]; returns the saved file's name. */
    private fun save(source: Source, output: Uri, sign: Boolean, log: JobLog): String {
        val merged = prepared(source, log)
        val signed = File(workDir(this), "signed_${System.currentTimeMillis()}.apk")
        try {
            val result = if (sign) {
                post(getString(R.string.antisplit_signing))
                val signer = loadSigner()
                val minSdk = runCatching { com.reandroid.apk.ApkModule.loadApkFile(merged).use { it.androidManifest?.minSdkVersion } }.getOrNull()
                // Signing also aligns stored native libraries to 16 KB pages, which apps with
                // extractNativeLibs="false" need to install.
                if (!signer.sign(merged, signed, minSdk ?: 21, log)) throw AntiSplit.AntiSplitException(getString(R.string.antisplit_sign_failed))
                signed
            } else {
                // The merge as it is; whoever signs it later aligns it too.
                merged
            }
            post(getString(R.string.antisplit_writing))
            val out = contentResolver.openOutputStream(output, "wt") ?: throw AntiSplit.AntiSplitException(getString(R.string.transfer_error_open))
            out.use { stream -> result.inputStream().use { it.copyTo(stream, 256 * 1024) } }
        } finally {
            signed.delete()
            File(signed.path + ".idsig").delete()
            // The cached merge stays (decompiling the same app or archive is then instant); only a
            // one-off merge without a cache key goes.
            if (merged.parentFile?.name?.startsWith(ONE_OFF_PREFIX) == true) merged.parentFile?.deleteRecursively()
        }
        return displayName(output) ?: "APK"
    }

    /** Runs [block] with the APK files of [source]; an archive is unpacked first and cleaned up after. */
    private fun <T> withInputs(source: Source, log: JobLog, block: (List<File>) -> T): T {
        return when (source) {
            is Source.Installed -> {
                post(getString(R.string.antisplit_merging))
                block(AntiSplit.installedApks(this, source.packageName))
            }
            is Source.Archive -> {
                val dir = File(workDir(this), "unpack_${System.currentTimeMillis()}")
                try {
                    post(getString(R.string.antisplit_unpacking))
                    val apks = AntiSplit.extractArchive(source.file, dir, log)
                    post(getString(R.string.antisplit_merging))
                    block(apks)
                } finally {
                    dir.deleteRecursively()
                }
            }
        }
    }

    private fun loadSigner(): SignUtil {
        var signer: SignUtil? = null
        var failure: String? = null
        // With a failure callback this never shows a dialog and runs synchronously.
        SignUtil.loadKey(this, { signer = it }, { failure = it })
        return signer ?: throw AntiSplit.AntiSplitException(failure ?: getString(R.string.build_sign_key_load_failed))
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Collects warnings and forwards the job's progress to the notification and [progress]. */
    private inner class JobLog(private val jobId: String) : ApktoolLogListener {
        val warnings = ArrayList<String>()
        private var last = 0L

        override fun onLog(level: Level, message: String) {
            DLog.d("AntiSplit", message)
            if (level.intValue() < Level.INFO.intValue()) return
            _progress.tryEmit(Progress(jobId, message))
            val now = System.currentTimeMillis()
            if (now - last > 500) {
                last = now
                post(getString(R.string.antisplit_running), detail = message)
            }
        }
    }

    // endregion

    // region notifications
    private fun buildNotification(text: String, detail: String?): Notification {
        val openApp = NotificationHelper.openAppIntent(this)
        return NotificationHelper.progressBuilder(this, NotificationHelper.CHANNEL_FILEOPS, R.drawable.ic_m3_notif_files)
            .setContentTitle(getString(R.string.antisplit_title))
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
                DLog.e("AntiSplit", e)
            }
        } else if (NotificationHelper.canPostNotifications(this)) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked meanwhile.
            }
        }
    }

    private fun publish(result: Result) {
        finished[result.jobId] = result
        _results.tryEmit(result)
        if (!NotificationHelper.canPostNotifications(this)) return
        val text = (listOf(result.message) + result.warnings).joinToString("\n")
        val notification = NotificationHelper.completionBuilder(this, if (result.success) R.drawable.ic_m3_notif_done else R.drawable.ic_m3_notif_error)
            .setContentTitle(getString(if (result.success) R.string.antisplit_done_title else R.string.antisplit_failed_title))
            .setContentText(result.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(NotificationHelper.openAppIntent(this))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(DONE_NOTIFICATION_BASE + doneIds.getAndIncrement() % 50, notification)
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

    /** Android 15+: dataSync services may run 6 hours a day; stop right away when told to. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        wakeLock?.release()
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

    /** Where the APKs come from. */
    sealed class Source {
        data class Installed(val packageName: String) : Source()
        data class Archive(val file: File) : Source()

        fun putInto(intent: Intent): Intent = when (this) {
            is Installed -> intent.putExtra(EXTRA_PACKAGE, packageName)
            is Archive -> intent.putExtra(EXTRA_ARCHIVE, file.absolutePath)
        }

        companion object {
            fun from(intent: Intent?): Source? {
                intent ?: return null
                intent.getStringExtra(EXTRA_PACKAGE)?.let { return Installed(it) }
                intent.getStringExtra(EXTRA_ARCHIVE)?.let { return Archive(File(it)) }
                return null
            }
        }
    }

    companion object {
        const val ACTION_SAVE = "com.riyadm.apkrepacker.antisplit.SAVE"
        const val ACTION_PREPARE = "com.riyadm.apkrepacker.antisplit.PREPARE"
        private const val EXTRA_JOB_ID = "jobId"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ARCHIVE = "archive"
        private const val EXTRA_BASE_ONLY = "baseOnly"
        private const val EXTRA_SIGN = "sign"

        private const val NOTIFICATION_ID = 9
        private const val DONE_NOTIFICATION_BASE = 700

        private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 8)

        /** Outcomes of finished jobs. */
        val results: SharedFlow<Result> = _results.asSharedFlow()

        private val _progress = MutableSharedFlow<Progress>(replay = 1, extraBufferCapacity = 32)
        val progress: SharedFlow<Progress> = _progress.asSharedFlow()

        /** Results by job id, so a screen recreated while its job ran still gets the outcome. */
        private val finished: MutableMap<String, Result> = Collections.synchronizedMap(object : LinkedHashMap<String, Result>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>?) = size > MAX_FINISHED
        })
        private const val MAX_FINISHED = 64

        @JvmStatic
        fun resultOf(jobId: String): Result? = finished[jobId]

        /** Merge [source], sign it when [sign] is set, and save the APK to [output] (a document the user created). */
        @JvmStatic
        fun save(context: Context, source: Source, output: Uri, sign: Boolean): String =
            start(context, ACTION_SAVE, source, output, baseOnly = false, sign = sign)

        /** Merge [source] (or, with [baseOnly], extract the base APK of an archive) for decompiling or editing. */
        @JvmStatic
        fun prepare(context: Context, source: Source, baseOnly: Boolean = false): String =
            start(context, ACTION_PREPARE, source, null, baseOnly, sign = false)

        /** The cached merged APK of [source] when it is current, without starting a job. */
        @JvmStatic
        fun cachedApk(context: Context, source: Source): File? = cacheFile(context, source)?.takeIf { it.isFile && it.length() > 0 }

        private fun start(context: Context, action: String, source: Source, output: Uri?, baseOnly: Boolean, sign: Boolean): String {
            val jobId = UUID.randomUUID().toString()
            val intent = source.putInto(Intent(context, AntiSplitService::class.java))
                .setAction(action)
                .setData(output)
                .putExtra(EXTRA_JOB_ID, jobId)
                .putExtra(EXTRA_BASE_ONLY, baseOnly)
                .putExtra(EXTRA_SIGN, sign)
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: IllegalStateException) {
                DLog.e("AntiSplit", e)
                val result = Result(jobId, false, context.getString(R.string.antisplit_failed, context.getString(R.string.build_service_start_failed)))
                finished[jobId] = result
                _results.tryEmit(result)
            }
            return jobId
        }

        private fun workDir(context: Context): File =
            File(context.externalCacheDir ?: context.cacheDir, "antisplit").also { it.mkdirs() }

        /**
         * One job at a time for the whole process: a job orphaned by [onTimeout] keeps running, and
         * the next service instance must wait for it rather than run alongside.
         */
        private val serial = Mutex()

        /** Leftovers are swept once per process, never under a job that is still running. */
        private val swept = AtomicBoolean(false)

        /** Folder of a merge without a cache key; removed after use. */
        private const val ONE_OFF_PREFIX = "merged_"

        /** Removes what jobs that never finished left behind: unpacked archives, half-written APKs. */
        private fun sweepLeftovers(context: Context) {
            val dir = workDir(context)
            val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            dir.listFiles()?.forEach { f ->
                val name = f.name
                val stale = name.startsWith("unpack_") || name.startsWith("signed_") || name.endsWith(".part") ||
                    // A one-off merge may still be open in Simple edit or the decompiler; give it a day.
                    (name.startsWith(ONE_OFF_PREFIX) && f.lastModified() < dayAgo)
                if (stale) f.deleteRecursively()
            }
            dir.walkTopDown().maxDepth(3).filter { it.isFile && it.name.endsWith(".part") }.forEach { it.delete() }
        }

        /**
         * The cache name of a merge: an installed app's merge is valid while its version and
         * install time are the same; an archive's while its size and date are.
         */
        private fun cacheFile(context: Context, source: Source): File? = when (source) {
            is Source.Installed -> {
                val info: PackageInfo? = runCatching { context.packageManager.getPackageInfo(source.packageName, 0) }.getOrNull()
                info?.let {
                    @Suppress("DEPRECATION")
                    val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
                    // The folder names the version; the file keeps a plain name, which Simple edit
                    // and the decompiler reuse for their output.
                    File(File(workDir(context), "installed/${source.packageName}@$version-${it.lastUpdateTime}"), "${source.packageName}.apk")
                }
            }
            is Source.Archive -> source.file.takeIf { it.isFile }?.let {
                val name = ProjectTransfer.safeName(it.nameWithoutExtension).replace('@', '_')
                // Two archives of the same name in different folders get separate caches.
                val where = Integer.toHexString(it.absolutePath.hashCode())
                File(File(workDir(context), "archive/$name-$where@${it.length()}-${it.lastModified()}"), "$name.apk")
            }
        }
    }
}
