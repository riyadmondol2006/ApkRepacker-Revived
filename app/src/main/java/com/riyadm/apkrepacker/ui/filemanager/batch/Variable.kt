package com.riyadm.apkrepacker.ui.filemanager.batch

import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder

interface Variable {

    fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int

    fun describe(): String

    fun pattern(): String
}
