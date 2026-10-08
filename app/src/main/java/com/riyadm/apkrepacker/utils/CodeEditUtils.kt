package com.riyadm.apkrepacker.utils

import java.io.File

object CodeEditUtils {
    @JvmStatic
    fun hasExtension(file: File, vararg exts: String): Boolean {
        for (ext in exts) {
            if (file.path.lowercase().endsWith(ext.lowercase())) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun canEdit(file: File): Boolean {
        val exts = arrayOf(".java", ".xml", ".txt", ".json", ".smali")
        return file.canWrite() && hasExtension(file, *exts)
    }
}
