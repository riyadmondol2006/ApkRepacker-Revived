package com.riyadm.apkrepacker.filepicker

import java.io.File
import java.io.FileFilter
import java.util.Locale

/** Hides dot-files and keeps only the entries a picker of the given type may show. */
class ExtensionFilter(private val selectType: Int, extensions: Array<String>?) : FileFilter {

    private val validExtensions: List<String> =
        (extensions ?: arrayOf("")).map { it.lowercase(Locale.getDefault()) }

    override fun accept(file: File): Boolean = when {
        file.name.startsWith(".") -> false
        // Directories are always listed so they can be navigated into.
        file.isDirectory -> file.canRead()
        // A folder picker still shows plain files (greyed out by the adapter) for context.
        selectType == FilePickerDialog.TYPE_DIR -> true
        else -> {
            val name = file.name.lowercase(Locale.getDefault())
            validExtensions.any { name.endsWith(it) }
        }
    }
}
