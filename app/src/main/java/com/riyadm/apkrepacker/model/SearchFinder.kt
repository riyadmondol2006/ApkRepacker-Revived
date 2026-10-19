package com.riyadm.apkrepacker.model

import com.riyadm.apkrepacker.utils.grep.FileListing
import java.io.File
import java.util.Locale

/** Finds files under [currentPath] whose name contains the query, limited to the given extensions. */
class SearchFinder {

    var currentPath: File? = null
    var fileList: List<File> = emptyList()
        private set
    private var extensions: Array<String> = emptyArray()

    fun query(query: String) {
        val needle = query.lowercase(Locale.getDefault())
        fileList = FileListing.listFiles(currentPath!!, extensions, true)
            .filter { it.name.lowercase(Locale.getDefault()).contains(needle) }
            .sorted()
    }

    fun setExtensions(extensions: ArrayList<String>) {
        this.extensions = extensions.toTypedArray()
    }
}
