package com.riyadm.apkrepacker.task

import android.content.Context
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolEngine
import com.riyadm.apkrepacker.apktool.ApktoolException
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ProjectMeta
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.File
import java.util.logging.Level

/** Receives the progress of a [DecodeTask] on the main thread (implemented by the decompile screen's ViewModel). */
interface DecodeSink {
    /** One log line ("I: ...", "W: ...", "E: ..."). */
    fun onDecodeLog(line: CharSequence)

    /** The run ended: the project folder, or null if decompiling failed. */
    fun onDecodeFinished(result: File?)
}

/**
 * Decompiles an apk with apktool 3 into `<projects root>/<app name>` and reports the log lines
 * ("I: ...", "W: ...", "E: ...") and the result to [sink].
 *
 * @param name project folder name (the app label); defaults to the apk file name without extension.
 */
class DecodeTask(
    context: Context,
    private val options: DecodeOptions,
    private val name: String?,
    private val sink: DecodeSink,
) : CoroutinesAsyncTask<File, CharSequence, Boolean>() {

    private val mContext: Context = context.applicationContext

    /** The decoded project folder, or null if decompiling failed. */
    @JvmField
    var resultFile: File? = null

    override fun doInBackground(vararg params: File?): Boolean {
        var success = true
        for (file in params) {
            if (file == null || !process(file)) success = false
        }
        return success
    }

    override fun onPostExecute(result: Boolean?) {
        sink.onDecodeFinished(resultFile)
    }

    override fun onProgressUpdate(vararg values: CharSequence?) {
        values.firstOrNull()?.let(sink::onDecodeLog)
    }

    private fun log(level: Level, message: String) {
        val line = ApktoolLogListener.format(level, message)
        publishProgress(line)
        DLog.d(line)
    }

    private fun process(apk: File): Boolean {
        val dir = try {
            getOutDir(apk, name)
        } catch (e: Exception) {
            log(Level.SEVERE, "Can't create the project folder: ${ApktoolEngine.describe(e)}")
            null
        } ?: return false
        val start = System.currentTimeMillis()
        return try {
            val apkInfo = ApktoolEngine.decode(mContext, apk, dir, options, ApktoolLogListener(::log))
            ProjectMeta.write(mContext, apk, dir, name, apkInfo)
            log(Level.INFO, String.format("Done in %.1f s: %s", (System.currentTimeMillis() - start) / 1000.0, dir.path))
            resultFile = dir
            true
        } catch (e: ApktoolException) {
            for (line in (e.message ?: e.toString()).lines()) {
                if (line.isNotBlank()) log(Level.SEVERE, line)
            }
            DLog.e("DecodeTask", e.message, e)
            resultFile = null
            false
        } catch (t: Throwable) {
            // ApktoolEngine wraps everything in ApktoolException; this is a last resort so the
            // screen shows the failure instead of the app crashing.
            log(Level.SEVERE, "Decompiling failed: ${ApktoolEngine.describe(t)}")
            DLog.e("DecodeTask", t.message, t)
            resultFile = null
            false
        }
    }

    /** `<projects root>/<name>`; with options.force off, an existing folder gets a "(n)" suffix. */
    private fun getOutDir(apk: File, projectName: String?): File? {
        var baseName = projectName ?: apk.name
        val e = baseName.lastIndexOf('.')
        if (projectName == null && e >= 0) baseName = baseName.substring(0, e)
        baseName = baseName.replace('/', '_').replace('\\', '_').trim().ifEmpty { "project" }

        val root = PreferenceHelper.getInstance(mContext).projectsDir
        if (!root.exists() && !root.mkdirs()) {
            log(Level.WARNING, mContext.getString(R.string.output_directory_not_extsts, root.path))
            return null
        }
        if (!root.isDirectory) {
            log(Level.WARNING, mContext.getString(R.string.not_directory, root.path))
            return null
        }
        var dir = File(root, baseName)
        if (!options.force) {
            var n = 1
            while (dir.exists()) {
                dir = File(root, "$baseName($n)")
                n++
            }
        }
        return dir
    }
}
