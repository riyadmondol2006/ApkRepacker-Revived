package com.riyadm.apkrepacker.task

import android.content.Context
import com.riyadm.apkrepacker.apktool.ApktoolEngine
import com.riyadm.apkrepacker.apktool.ApktoolException
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.BuildOptions
import com.riyadm.apkrepacker.apktool.ProjectMeta
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.ui.apkbuilder.IBuilderCallback
import com.riyadm.apkrepacker.ui.apkbuilder.TaskStepInfo
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.SignUtil
import com.riyadm.apkrepacker.utils.common.DLog
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.logging.Level

/**
 * Rebuilds a project with apktool 3 into `<work folder>/output/<app name>.apk`, then signs it when
 * [BuildOptions.sign] is on. Progress goes to [mBuilderCallback] (steps, time, result) and every
 * log line to [logger].
 *
 * @param signTool the loaded signing key; only needed when signing.
 * @param options build options; the saved defaults when null.
 */
class BuildTask @JvmOverloads constructor(
    context: Context,
    private val signTool: SignUtil?,
    private val logger: ApktoolLogListener?,
    private val mBuilderCallback: IBuilderCallback,
    options: BuildOptions? = null,
) : CoroutinesAsyncTask<File, CharSequence, Boolean>() {

    private val mContext: Context = context.applicationContext
    private val options: BuildOptions = options ?: ApktoolOptionsStore.loadBuildOptions(mContext)
    private val stepInfo = TaskStepInfo()

    override fun doInBackground(vararg params: File?): Boolean {
        var success = true
        for (file in params) {
            if (file == null || !process(file)) success = false
        }
        return success
    }

    private fun log(level: Level, message: String) {
        try {
            logger?.onLog(level, message)
        } catch (ignored: Exception) {
        }
    }

    /** Turns apktool's progress lines into build steps. */
    private val engineLog = ApktoolLogListener { level, message ->
        log(level, message)
        if (level == Level.INFO && (STEP_PREFIXES.any { message.startsWith(it) } || message.endsWith(" has not changed."))) {
            nextStep(message)
        }
    }

    private fun nextStep(description: String) {
        synchronized(stepInfo) {
            stepInfo.stepIndex++
            if (stepInfo.stepIndex > stepInfo.stepTotal) stepInfo.stepTotal = stepInfo.stepIndex
            stepInfo.stepDescription = description
            mBuilderCallback.setTaskStepInfo(stepInfo)
        }
    }

    private fun countSteps(projectDir: File): Int {
        var steps = 1 // preparing
        projectDir.listFiles()?.forEach { f ->
            if (f.isDirectory && (f.name == "smali" || f.name.startsWith("smali_"))) steps++
        }
        if (File(projectDir, "res").isDirectory || File(projectDir, "AndroidManifest.xml").isFile) steps++
        steps++ // apk file
        if (options.sign) steps++
        return steps
    }

    private fun process(projectDir: File): Boolean {
        val preferenceHelper = PreferenceHelper.getInstance(mContext)
        val start = System.currentTimeMillis()
        synchronized(stepInfo) {
            stepInfo.stepIndex = 0
            stepInfo.stepTotal = countSteps(projectDir)
        }
        nextStep("Preparing ${projectDir.name}")
        var tmp: File? = null
        try {
            tmp = File.createTempFile("APKTOOL", ".apk", mContext.cacheDir)
            //start building
            ApktoolEngine.build(mContext, projectDir, tmp, options, engineLog)

            val meta = ProjectMeta.read(projectDir)
            // apktool.json may come from an imported project: reduce the name to a plain file name
            // so "../../x" can't point the output (or its deletion) outside the output folder.
            val apkFileName = ProjectMeta.getString(meta, "apkFileName")
                ?.replace('\\', '/')?.let { File(it).name }
                ?.replace(Regex("[/\\u0000]"), "_")?.trim()
                ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
                ?: projectDir.name
            val outApk = FileUtil.genNameApk(
                mContext, projectDir.absolutePath, apkFileName,
                if (options.sign) "_signed" else "_unsigned", 0
            )
            val buildApkPath = preferenceHelper.outputDir
            if (!buildApkPath.isDirectory) {
                throw ApktoolException("Can't create the output folder ${buildApkPath.path}")
            }
            val out = File(buildApkPath, outApk)
            if (options.sign) {
                nextStep("Signing ${out.name}")
                val signer = signTool ?: throw ApktoolException("The signing key could not be loaded")
                val min = ProjectMeta.minSdkVersion(projectDir) ?: 14
                if (!signer.sign(tmp, out, min, logger)) {
                    throw ApktoolException("Signing failed, see the log")
                }
            } else {
                if (out.exists()) {
                    FileUtil.deleteFile(out)
                }
                FileUtils.copyFile(tmp, out)
            }
            val buildTime = System.currentTimeMillis() - start
            log(Level.INFO, "Built ${out.path}")
            SignUtil.v4SignatureFile(out).takeIf { options.sign && it.isFile }?.let {
                log(Level.INFO, "V4 signature: ${it.path}")
            }
            mBuilderCallback.taskTime(buildTime)
            DLog.i("Build app time=$buildTime")
            mBuilderCallback.taskSucceed(out)
            return true
        } catch (e: Throwable) {
            // Throwable: an OutOfMemoryError while signing must end up on the build screen too.
            val message = if (e is ApktoolException) e.message ?: e.toString() else ApktoolEngine.describe(e)
            for (line in message.lines()) {
                if (line.isNotBlank()) log(Level.SEVERE, line)
            }
            DLog.e("BuildTask", message, e)
            mBuilderCallback.taskFailed(message)
            return false
        } finally {
            tmp?.delete()
        }
    }

    companion object {
        /** apktool 3 INFO lines that start a new build phase. */
        private val STEP_PREFIXES = listOf(
            "Smaling ", "Copying raw classes", "Building resources", "Building AndroidManifest.xml",
            "Copying raw resources.arsc", "Building apk file",
        )
    }
}
