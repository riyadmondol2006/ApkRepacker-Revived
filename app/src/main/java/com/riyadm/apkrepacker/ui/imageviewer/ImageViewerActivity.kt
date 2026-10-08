package com.riyadm.apkrepacker.ui.imageviewer

import android.content.Context
import android.os.Bundle
import com.riyadm.apkrepacker.activity.BaseActivity
import com.riyadm.apkrepacker.ui.filemanager.FileAdapter
import com.riyadm.apkrepacker.utils.FileUtil
import com.sdsmdg.harjot.vectormaster.VectorMasterView
import java.io.File

class ImageViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ImageViewerFragment.newInstance(paths.orEmpty(), position), FRAGMENT_TAG)
                .commit()
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "ImageViewerFragment"

        private var paths: List<String>? = null
        private var position = 0

        /** Collects the images (and vector XMLs) of the listed folder and remembers which one was opened. */
        @JvmStatic
        fun setViewerData(context: Context?, adapter: FileAdapter, path: File) {
            val shown = (0 until adapter.itemCount).mapNotNull { index ->
                val file = adapter.get(index).file
                val type = FileUtil.FileType.getFileType(file)
                val isVector = type == FileUtil.FileType.XML && VectorMasterView(context, file).isVector
                file.absolutePath.takeIf { type == FileUtil.FileType.IMAGE || it == path.path || isVector }
            }
            val index = shown.indexOf(path.path)
            if (index == -1) return
            paths = shown
            position = index
        }
    }
}
