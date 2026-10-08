package com.riyadm.apkrepacker.task

import android.content.Context
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolEngine
import com.riyadm.apkrepacker.apktool.ApktoolException
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.fragment.MyFilesFragment
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.File

/** Installs a framework apk (framework-res.apk of a ROM) for apktool, in the app's framework folder. */
class ImportFrameworkTask(private val mContext: Context, filesFragment: MyFilesFragment) :
    CoroutinesAsyncTask<File, CharSequence, Boolean>() {

    private val dialog: MyFilesFragment = filesFragment

    override fun doInBackground(vararg params: File?): Boolean {
        var success = true
        for (file in params) {
            if (file == null || !process(file)) success = false
        }
        return success
    }

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)
        dialog.hideProgress()
        if (result != true)
            dialog.showTaskMessage(R.string.toast_error_import_framework_failed)
    }

    override fun onPreExecute() {
        dialog.showProgress()
    }

    private fun process(file: File): Boolean {
        return try {
            ApktoolEngine.installFramework(mContext, file, ApktoolLogListener { level, message ->
                DLog.d("ImportFrameworkTask", ApktoolLogListener.format(level, message))
            })
            true
        } catch (e: ApktoolException) {
            // MyFilesFragment.updateProgress() expects a percentage, so only log the error.
            DLog.e("ImportFrameworkTask", e.message, e)
            false
        }
    }
}
