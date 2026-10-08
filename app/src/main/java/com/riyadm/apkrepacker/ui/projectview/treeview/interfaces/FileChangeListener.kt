package com.riyadm.apkrepacker.ui.projectview.treeview.interfaces

import java.io.File

/** Implemented by the editor activities that host the project tree. */
interface FileChangeListener {

    fun onFileDeleted(deleted: File)

    fun onFileCreated(newFile: File)

    fun doOpenFile(toEdit: String)
}
