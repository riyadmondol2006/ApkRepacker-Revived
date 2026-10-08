package com.riyadm.apkrepacker.ui.stringlist

import java.io.File

/** Finds `res/values/strings.xml` and every `res/values-<lang>/strings.xml` next to it. */
class DirectoryScanner {

    fun findStringFiles(pathToLoad: String?): ArrayList<StringFile> {
        val found = ArrayList<StringFile>()
        val resDir = File("$pathToLoad/res")
        val valuesDir = File(resDir, "values")
        if (!File(valuesDir, "strings.xml").isFile) return found

        found += StringFile(valuesDir, "strings.xml", "default")
        resDir.list { _, name -> VALUES_FOLDER.containsMatchIn(name) }
            .orEmpty()
            .sorted()
            .forEach { folder ->
                val file = StringFile(resDir, "$folder/strings.xml", folder.removePrefix("values-"))
                if (file.isFile) found += file
            }
        return found
    }

    private companion object {
        val VALUES_FOLDER = Regex("values-[a-z\\-]{2,}")
    }
}
