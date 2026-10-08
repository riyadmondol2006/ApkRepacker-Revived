package com.riyadm.apkrepacker.task

import android.content.Context
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.fragment.MyFilesFragment
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.SignUtil
import java.io.File

class SignTask(
    private val mContext: Context?,
    private val dialog: MyFilesFragment,
    private val signTool: SignUtil?
) : CoroutinesAsyncTask<File, CharSequence, Boolean>() {

    @JvmField
    var resultFile: File? = null

    override fun doInBackground(vararg params: File?): Boolean {
        var success = true
        for (file in params) {
            if (!process(file!!))
                success = false
        }
        return success
    }

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)
        dialog.hideProgress()
        dialog.signedApk = resultFile
        if (result != true) {
            dialog.showTaskMessage(R.string.toast_error_sign_failed)
        } else {
            resultFile?.let { SignUtil.v4SignatureFile(it) }?.takeIf { it.isFile }?.let {
                dialog.showTaskMessage(R.string.toast_v4_signature_written, it.name)
            }
        }
    }

    override fun onProgressUpdate(vararg values: CharSequence?) {
        dialog.updateProgress()
    }

    override fun onPreExecute() {
        dialog.showProgress()
    }

    private fun process(f: File): Boolean {
        val outApk: String
        val dir = f.parent
        try {
            outApk = FileUtil.genNameApk(mContext, f.absolutePath, f.name, "_signed", 0)
            val out = File(dir, outApk)
            if (!signTool!!.sign(f, out, 14)) return false
            resultFile = out
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
