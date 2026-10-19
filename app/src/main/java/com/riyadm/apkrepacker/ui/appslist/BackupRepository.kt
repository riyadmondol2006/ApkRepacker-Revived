package com.riyadm.apkrepacker.ui.appslist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.riyadm.apkrepacker.utils.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installed packages, kept current by package add/change/remove broadcasts. A scan lists only what
 * the Apps list shows, sorts and filters by (label, package, version, flags); icons are loaded per
 * row (see [PackageIcon]).
 */
class BackupRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Labels load on a few threads at once: each one opens that app's resources. */
    private val labelDispatcher =
        Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))

    // The scan state, used under scanLock only.
    private val scanLock = Mutex()
    private val labels = LabelCache()
    private var scanned = false

    /** Set by every broadcast; a queued scan that finds it already cleared has nothing new to do. */
    private val rescan = AtomicBoolean(false)

    private val scanLiveData = MutableLiveData<Scan?>(null)

    /**
     * The latest scan, sorted by label; null until the first one has listed the user apps. The
     * first scan lists them before loading the system apps' labels (hidden by default), so the list
     * shows up sooner; [Scan.systemApps] tells whether they are in yet.
     */
    val scan: LiveData<Scan?>
        get() = scanLiveData

    class Scan(val packages: List<PackageMeta>, val systemApps: Boolean)

    init {
        sInstance = this

        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        // Only the system sends these, so the receiver needn't be exported (API 34 requires a flag).
        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    fetchPackages()
                }
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        fetchPackages()
    }

    private fun fetchPackages() {
        rescan.set(true)
        // The lock keeps scans in order: one may suspend while its labels load on other threads.
        scope.launch {
            scanLock.withLock { scan() }
        }
    }

    private suspend fun scan() {
        // Broadcasts come in bursts (an update is removed + added + replaced): one scan covers them.
        if (!rescan.getAndSet(false)) return
        val start = SystemClock.elapsedRealtime()
        val pm = appContext.packageManager

        // One call: each PackageInfo carries its ApplicationInfo.
        val installed = installedPackages(pm).filter { it.applicationInfo != null }
        labels.useLocales(ConfigurationCompat.getLocales(appContext.resources.configuration).toLanguageTags())
        labels.retainAll(installed.mapTo(HashSet()) { it.packageName })

        val (system, user) = installed.partition { it.applicationInfo!!.isSystem() }
        loadLabels(pm, user)
        if (!scanned) {
            scanLiveData.postValue(Scan(toMetas(user), systemApps = false))
            Log.d(TAG, "Listed ${user.size} user apps in ${SystemClock.elapsedRealtime() - start} ms")
        }
        loadLabels(pm, system)
        val packages = toMetas(installed)
        scanned = true

        Log.d(TAG, "Loaded ${packages.size} packages in ${SystemClock.elapsedRealtime() - start} ms")
        scanLiveData.postValue(Scan(packages, systemApps = true))
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }

    /** Puts the labels [labels] doesn't have yet into it, loading them in parallel. */
    private suspend fun loadLabels(pm: PackageManager, packages: List<PackageInfo>) {
        val missing = packages.filter { labels.get(it.packageName, it.lastUpdateTime) == null }
        if (missing.isEmpty()) return
        val loaded = coroutineScope {
            missing.chunked(LABEL_BATCH).map { batch ->
                async(labelDispatcher) { batch.map { it to it.applicationInfo!!.loadLabel(pm).toString() } }
            }.awaitAll()
        }
        loaded.forEach { batch -> batch.forEach { (info, label) -> labels.put(info.packageName, info.lastUpdateTime, label) } }
    }

    /** The packages as list rows, sorted by label; their labels must be in [labels]. */
    @Suppress("DEPRECATION")
    private fun toMetas(packages: List<PackageInfo>): List<PackageMeta> =
        packages.map { packageInfo ->
            val applicationInfo = packageInfo.applicationInfo!!
            PackageMeta.Builder(packageInfo.packageName)
                .setLabel(labels.get(packageInfo.packageName, packageInfo.lastUpdateTime) ?: packageInfo.packageName)
                .setHasSplits(!applicationInfo.splitPublicSourceDirs.isNullOrEmpty())
                .setIsSystemApp(applicationInfo.isSystem())
                .serVersionCode(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode
                    else packageInfo.versionCode.toLong(),
                )
                .setVersionName(packageInfo.versionName)
                .setIcon(applicationInfo.icon)
                .build()
        }.sortedBy { it.label.orEmpty().lowercase() }

    private fun ApplicationInfo.isSystem() = (flags and ApplicationInfo.FLAG_SYSTEM) != 0

    companion object {
        private const val TAG = "BackupRepository"
        private const val LABEL_BATCH = 16
        private var sInstance: BackupRepository? = null

        @JvmStatic
        fun getInstance(context: Context): BackupRepository =
            synchronized(BackupRepository::class.java) { sInstance ?: BackupRepository(context) }
    }
}
