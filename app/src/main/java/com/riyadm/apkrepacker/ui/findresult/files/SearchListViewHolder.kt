package com.riyadm.apkrepacker.ui.findresult.files

import android.view.ViewGroup
import com.riyadm.apkrepacker.ui.filemanager.holder.FileListViewHolder
import com.riyadm.apkrepacker.utils.ProjectUtils
import java.io.File

/** A file row that shows the path relative to the project instead of the file size. */
class SearchListViewHolder internal constructor(parent: ViewGroup) : FileListViewHolder(parent) {

    override fun bind(filePath: File, position: Int, listener: OnItemClickListener, selected: Boolean, projectMode: Boolean) {
        super.bind(filePath, position, listener, selected, projectMode)
        mFileSize.text = filePath.absolutePath.removePrefix(ProjectUtils.getProjectPath() + "/")
    }
}
