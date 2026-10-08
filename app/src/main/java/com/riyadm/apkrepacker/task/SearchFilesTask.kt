package com.riyadm.apkrepacker.task

import android.content.Context
import com.riyadm.apkrepacker.fragment.FindFragment
import com.riyadm.apkrepacker.model.SearchFinder
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import java.io.File

class SearchFilesTask(
    private val mContext: Context?,
    private val mFindFragment: FindFragment,
    private val mFinder: SearchFinder?
) : CoroutinesAsyncTask<String, Int, Void?>() {

    private var mPath: String? = null
    private var mSearchText: String? = null
    private var mExt: ArrayList<String>? = null

    fun setArguments(path: String?, searchText: String?, ext: ArrayList<String>) {
        mPath = path
        mSearchText = searchText
        mExt = ArrayList()
        mExt!!.addAll(ext)
    }

    override fun onPreExecute() {
        mFindFragment.showProgress()
    }

    override fun doInBackground(vararg params: String?): Void? {
        mFinder!!.currentPath = File(mPath!!)
        mFinder.setExtensions(mExt!!)
        mFinder.query(mSearchText!!)
        return null
    }

    override fun onProgressUpdate(vararg values: Int?) {
        mFindFragment.updateProgress(*values)
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        if (mFinder != null) {
            mFindFragment.setResult(mFinder.fileList)
        }
        mFindFragment.hideProgress()
    }
}
