package com.riyadm.apkrepacker.viewmodel.projects

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import com.google.gson.Gson
import com.riyadm.apkrepacker.apktool.ProjectMeta
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.projectlist.ProjectItem
import com.riyadm.apkrepacker.utils.common.DLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale

/** Live list of decompiled projects. [loadProjects] rescans the disk; refreshes run one at a time, off the main thread. */
class ProjectLoader(context: Context) : LiveData<List<ProjectItem>>() {

    private val appContext: Context = context.applicationContext
    private val preferenceHelper: PreferenceHelper = PreferenceHelper.getInstance(appContext)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    init {
        sInstance = this
        loadProjects()
    }

    fun loadProjects() {
        scope.launch { postValue(scanProjects()) }
    }

    private fun scanProjects(): List<ProjectItem> {
        val start = System.currentTimeMillis()
        val items = ArrayList<ProjectItem>()
        try {
            val root = preferenceHelper.projectsDir
            File(preferenceHelper.decodingPath, ".nomedia").createNewFile()
            if (!root.exists() && !root.mkdirs()) return items

            // Projects decompiled by older versions live in /sdcard/ApkRepacker/projects; keep
            // listing them (when readable) so they don't disappear after the move to Android/data.
            @Suppress("DEPRECATION")
            val legacyRoot = File(Environment.getExternalStorageDirectory(), "ApkRepacker/projects")
            val roots = if (legacyRoot.isDirectory && legacyRoot.canRead() &&
                legacyRoot.canonicalPath != root.canonicalPath
            ) listOf(root, legacyRoot) else listOf(root)

            for (projectsRoot in roots) {
                for (dir in projectsRoot.listFiles().orEmpty()) {
                    readProject(dir)?.let(items::add)
                }
            }
        } catch (io: IOException) {
            DLog.e(TAG, io)
        }
        items.sortBy { it.appName.orEmpty().lowercase(Locale.getDefault()) }
        DLog.d(TAG, String.format(Locale.ENGLISH, "Loaded projects in %d ms", System.currentTimeMillis() - start))
        return items
    }

    private fun readProject(dir: File): ProjectItem? {
        val dataFile = ProjectMeta.file(dir)
        if (!dataFile.exists()) {
            DLog.i(TAG, String.format(Locale.ENGLISH, "Is not project in %s", dir.absolutePath))
            return null
        }
        return try {
            val project = gson.fromJson(dataFile.readText(Charsets.UTF_8), ProjectItemJson::class.java) ?: return null
            ProjectItem(
                project.apkFileIcon,
                project.apkFileName ?: dir.name,
                project.apkFilePackageName,
                dir.absolutePath,
                project.apkFilePatch,
                project.versionInfo?.versionName,
                project.versionInfo?.versionCode,
            )
        } catch (e: Exception) {
            DLog.e(TAG, e)
            null
        }
    }

    companion object {
        private const val TAG = "ProjectLoader"
        private var sInstance: ProjectLoader? = null

        @JvmStatic
        fun getInstance(context: Context): ProjectLoader =
            synchronized(ProjectLoader::class.java) { sInstance ?: ProjectLoader(context) }
    }
}
