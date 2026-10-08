package com.riyadm.apkrepacker.ui.appslist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.riyadm.apkrepacker.utils.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Installed packages, kept current by package add/change/remove broadcasts. */
class BackupRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val packagesLiveData = MutableLiveData<List<PackageMeta>>(emptyList())
    private val loadedLiveData = MutableLiveData(false)

    val packages: LiveData<List<PackageMeta>>
        get() = packagesLiveData

    /** False until the first scan has finished (the list is empty before that, and also if nothing is installed). */
    val loaded: LiveData<Boolean>
        get() = loadedLiveData

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

    @Suppress("DEPRECATION")
    private fun fetchPackages() {
        scope.launch {
            val start = System.currentTimeMillis()
            val pm = appContext.packageManager

            val applicationInfos: List<ApplicationInfo>
            val packageInfos: List<PackageInfo>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationInfos = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                packageInfos = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                applicationInfos = pm.getInstalledApplications(0)
                packageInfos = pm.getInstalledPackages(0)
            }
            val packageInfoIndex = packageInfos.associateBy { it.packageName }

            val packages = applicationInfos.mapNotNull { applicationInfo ->
                val packageInfo = packageInfoIndex[applicationInfo.packageName]
                if (packageInfo == null) {
                    Log.wtf(TAG, "PackageInfo is null for ${applicationInfo.packageName}")
                    return@mapNotNull null
                }
                PackageMeta.Builder(applicationInfo.packageName)
                    .setLabel(applicationInfo.loadLabel(pm).toString())
                    .setHasSplits(!applicationInfo.splitPublicSourceDirs.isNullOrEmpty())
                    .setIsSystemApp((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                    .serVersionCode(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode
                        else packageInfo.versionCode.toLong(),
                    )
                    .setVersionName(packageInfo.versionName)
                    .setIcon(applicationInfo.icon)
                    .setIconDrawable(applicationInfo.loadIcon(pm))
                    .build()
            }.sortedBy { it.label.orEmpty().lowercase() }

            Log.d(TAG, "Loaded ${packages.size} packages in ${System.currentTimeMillis() - start} ms")
            packagesLiveData.postValue(packages)
            loadedLiveData.postValue(true)
        }
    }

    companion object {
        private const val TAG = "BackupRepository"
        private var sInstance: BackupRepository? = null

        @JvmStatic
        fun getInstance(context: Context): BackupRepository =
            synchronized(BackupRepository::class.java) { sInstance ?: BackupRepository(context) }
    }
}
