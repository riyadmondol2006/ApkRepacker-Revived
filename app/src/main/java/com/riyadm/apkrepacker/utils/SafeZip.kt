package com.riyadm.apkrepacker.utils

import java.io.File
import java.io.IOException

/**
 * Zip entry names are untrusted: "../x" or "/x" would write outside the target directory.
 * App.onCreate turns off the platform's own check (ZipPathValidator, targetSdk 34+) because
 * obfuscated APKs use such names on purpose, so every extraction must go through here.
 */
object SafeZip {

    /**
     * Resolves [entryName] inside [destDir], or returns null when the entry would end up
     * outside it (the caller should skip the entry).
     */
    @JvmStatic
    fun resolve(destDir: File, entryName: String): File? {
        val name = entryName.replace('\\', '/').trimStart('/')
        if (name.isEmpty()) return null
        return try {
            val root = destDir.canonicalFile
            val target = File(root, name).canonicalFile
            if (target.path == root.path || target.path.startsWith(root.path + File.separator)) target else null
        } catch (e: IOException) {
            null
        }
    }
}
