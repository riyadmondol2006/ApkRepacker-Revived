package com.riyadm.apkrepacker.task

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import com.riyadm.apkrepacker.fragment.FindFragment
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.ui.findresult.ChildData
import com.riyadm.apkrepacker.ui.findresult.ParentData
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.StringUtils
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.utils.grep.ExtGrep
import java.io.File

class SearchStringsTask(
    private val mContext: Context,
    private val mFindFragment: FindFragment
) : CoroutinesAsyncTask<String, Int, Void?>() {

    private var mParentList: List<ParentData>? = null
    private val mFindFiles = ArrayList<String>()
    private val mExtGrep: ExtGrep? = StringUtils.extGreps

    override fun doInBackground(vararg params: String?): Void? {
        mParentList = getList(mExtGrep!!.execute())
        return null
    }

    override fun onPreExecute() {
        mFindFragment.showProgress()
    }

    override fun onProgressUpdate(vararg values: Int?) {
        mFindFragment.updateProgress(*values)
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        mFindFragment.setStringResult(mParentList!!, mFindFiles)
        mFindFragment.hideProgress()
    }

    @SuppressLint("DefaultLocale")
    private fun getList(results: List<ExtGrep.Result>): List<ParentData> {
        var file: File? = null

        val findResultsKeywordColor = MaterialColors.getColor(mContext, com.google.android.material.R.attr.colorTertiaryContainer, 0)
        val findResultsKeywordTextColor = MaterialColors.getColor(mContext, com.google.android.material.R.attr.colorOnTertiaryContainer, 0)

        val parentDataList: MutableList<ParentData> = ArrayList()
        var childDataList: MutableList<ChildData>? = null

        for (res in results) {
            if (res.file!! != file) {
                file = res.file!!
                childDataList = ArrayList()
                mFindFiles.add(file.absolutePath)
                parentDataList.add(ParentData(file.absolutePath.substring((ProjectUtils.getProjectPath() + "/").length), childDataList))
            }

            val ssb = SpannableStringBuilder()
            ssb.append(String.format("%1\$4d :", res.lineNumber))
            val start = ssb.length
            ssb.append(res.line)

            ssb.setSpan(BackgroundColorSpan(findResultsKeywordColor), start + res.matchStart, start + res.matchEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(ForegroundColorSpan(findResultsKeywordTextColor), start + res.matchStart, start + res.matchEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            childDataList!!.add(ChildData(ssb, file.absolutePath, res.lineNumber))
        }

        return parentDataList
    }
}
