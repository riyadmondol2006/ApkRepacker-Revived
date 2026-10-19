package com.riyadm.apkrepacker.task

import android.content.Context
import android.widget.Toast
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.fragment.SimpleEditorFragment
import com.riyadm.apkrepacker.model.QickEdit
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.SignUtil
import com.riyadm.apkrepacker.utils.common.DLog
import org.apache.commons.io.FileUtils
import java.io.File

class SimpleEditTask(
    private val mContext: Context?,
    private val simpleEditorFragment: SimpleEditorFragment,
    signTool: SignUtil?
) : CoroutinesAsyncTask<File, Int, Boolean>() {

    private var resultFile: File? = null
    private var preferenceHelper: PreferenceHelper? = null

    init {
        Companion.signTool = signTool
    }

    override fun onPreExecute() {
        simpleEditorFragment.showProgress()
    }

    override fun doInBackground(vararg params: File?): Boolean {
        var success = true
        for (file in params) {
            if (!process(file!!))
                success = false
        }
        return success
    }

    override fun onPostExecute(result: Boolean?) {
        // null when the task itself failed: report it like any other failure, never leave the progress up.
        val success = result == true
        val signed = preferenceHelper?.isSignResultApk == true
        simpleEditorFragment.hideProgress(success, signed)
        if (!simpleEditorFragment.isAdded) {
            // The editor was closed meanwhile: still say how it went.
            val context = mContext?.applicationContext ?: return
            val text = resultFile?.takeIf { success }?.let { context.getString(R.string.simple_edit_saved_to, it.absolutePath) }
                ?: context.getString(R.string.simple_edit_failed)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            return
        }
        if (success) {
            resultFile?.let { SignUtil.v4SignatureFile(it) }?.takeIf { it.isFile }?.let {
                simpleEditorFragment.showTaskMessage(R.string.toast_v4_signature_written, it.name)
            }
        }
    }

    private fun process(input: File): Boolean {
        val preferenceHelper = PreferenceHelper.getInstance(mContext!!)
        this.preferenceHelper = preferenceHelper
        val outApk: String
        try {
            val tmp = File.createTempFile("temp", ".apk")
            try {
                if (preferenceHelper.isSignResultApk) {
                    outApk = FileUtil.genNameApk(mContext, input.absolutePath, input.name, "_signed", 0)
                } else {
                    outApk = FileUtil.genNameApk(mContext, input.absolutePath, input.name, "_unsigned", 0)
                }
                val buildApkPath = File(preferenceHelper.decodingPath + "/output")
                if (!buildApkPath.exists() && !buildApkPath.mkdirs()) {
                    return false
                }
                DLog.i("start")
                val out = File(buildApkPath, outApk)
                val qickEdit = QickEdit()
                qickEdit.build(input, tmp)
                DLog.i("edited done")
                if (preferenceHelper.isSignResultApk) {
                    DLog.i("start sign apk")
                    if (!signTool!!.sign(tmp, out, 14)) return false
                    DLog.i("temp file: " + tmp.absolutePath)
                    //FileUtils.copyFile(tmp, out);
                    setResult(out)
                } else {
                    try {
                        if (out.exists()) {
                            FileUtil.deleteFile(out)
                        }
                        DLog.i("done")
                        FileUtils.copyFile(tmp, out)
                        setResult(out)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                /*
                if (preference.isSignResultApk()) {
                    outApk = FileUtil.genNameApk(mContext, f.getAbsolutePath(), AppUtils.getApkName(mContext, f.getAbsolutePath()), "_signed", 0);
                } else {
                    outApk = FileUtil.genNameApk(mContext, f.getAbsolutePath(),AppUtils.getApkName(mContext, f.getAbsolutePath()), "_unsigned", 0);
                }
                File buildApkPath = new File(preference.getDecodingPath() + "/output");
                if (!buildApkPath.exists() && !buildApkPath.mkdirs()) {
                    return false;
                }
                File out = new File(buildApkPath, outApk);
                if (preference.isSignResultApk()) {
                    signTool.sign(tmp, out, 14, this);
                    setResult(out);
                } else {
                    try {
                        if (out.exists()) {
                            FileUtil.deleteFile(out);
                        }
                        FileUtils.copyFile(tmp, out);
                        setResult(out);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                 */
            } finally {
                tmp.delete()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun setResult(f: File) {
        resultFile = f
        DLog.i(resultFile!!.absolutePath)
        simpleEditorFragment.setOutputFile(f)
    }

    fun setResult(f: String) {
        try {
            resultFile = File(f)
            DLog.i(resultFile!!.absolutePath)
            simpleEditorFragment.setOutputFile(resultFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private var signTool: SignUtil? = null
    }
}
