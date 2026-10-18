package com.riyadm.patchengine.utils

import java.io.Closeable
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Opens a patch zip the way people actually make them. The format wants `patch.txt` at the root,
 * but zipping a folder gives `MyPatch/patch.txt`, PowerShell's Compress-Archive writes `a\b`
 * names, some tools upper-case `PATCH.TXT`, and old Windows zippers store names that aren't
 * UTF-8. Rules look their files up by name relative to patch.txt, so such a zip is copied once
 * into a normalized one (patch.txt at the root, `/` separators) and the rules run on the copy.
 */
object PatchZip {

    const val PATCH_TXT = "patch.txt"

    class Opened(val zip: ZipFile, val patchTxt: ZipEntry?, private val temp: File?) : Closeable {
        /** Where patch.txt was found when it was not plainly at the root (for the log), else null. */
        var normalizedFrom: String? = null

        override fun close() {
            try {
                zip.close()
            } finally {
                temp?.delete()
            }
        }
    }

    /** Opens [path] and finds its patch.txt; with an unusual layout the result is a normalized copy in [tempDir]. */
    @JvmStatic
    fun open(path: String, tempDir: File?): Opened {
        val zip = openZip(path)
        val entry = findPatchTxt(zip)
        if (entry == null || (entry.name == PATCH_TXT && zip.entries().asSequence().none { '\\' in it.name })) {
            return Opened(zip, entry, null)
        }
        val from = entry.name
        val temp = File(tempDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"), "patch_" + System.nanoTime() + ".zip")
        try {
            normalize(zip, from.replace('\\', '/').substringBeforeLast('/', ""), temp)
        } catch (e: Exception) {
            temp.delete()
            zip.close()
            throw e
        }
        zip.close()
        val copy = ZipFile(temp)
        return Opened(copy, copy.getEntry(PATCH_TXT), temp).also { it.normalizedFrom = from }
    }

    /**
     * Opens [path] as a zip. Entry names that aren't valid UTF-8 make ZipFile fail (when opening
     * or when listing): they are then read as CP437, the zip default, which keeps ASCII names as they are.
     */
    @JvmStatic
    fun openZip(path: String): ZipFile {
        val zip = try {
            ZipFile(path)
        } catch (e: Exception) {
            return try {
                ZipFile(File(path), ZipFile.OPEN_READ, Charset.forName("IBM437"))
            } catch (e2: Exception) {
                throw e
            }
        }
        try {
            zip.entries().asSequence().forEach { it.name }
            return zip
        } catch (e: IllegalArgumentException) {
            zip.close()
            return ZipFile(File(path), ZipFile.OPEN_READ, Charset.forName("IBM437"))
        }
    }

    /** The patch.txt entry: at the root, else the least nested one in any folder, in any letter case. */
    @JvmStatic
    fun findPatchTxt(zip: ZipFile): ZipEntry? {
        zip.getEntry(PATCH_TXT)?.let { if (!it.isDirectory) return it }
        return zip.entries().asSequence()
            .filter { !it.isDirectory && !it.name.startsWith("__MACOSX") }
            .filter { it.name.replace('\\', '/').substringAfterLast('/').equals(PATCH_TXT, ignoreCase = true) }
            .minByOrNull { e -> e.name.count { it == '/' || it == '\\' } }
    }

    /** Copies [zip] to [dest] with `/` separators and the folder [root] (holding patch.txt) as the new root. */
    private fun normalize(zip: ZipFile, root: String, dest: File) {
        val prefix = if (root.isEmpty()) "" else "$root/"
        val written = HashSet<String>()
        ZipOutputStream(dest.outputStream().buffered()).use { out ->
            // Files next to patch.txt first: they win over a same-named file outside its folder.
            val entries = zip.entries().asSequence().sortedBy { if (it.name.replace('\\', '/').startsWith(prefix)) 0 else 1 }
            for (e in entries) {
                if (e.isDirectory || e.name.startsWith("__MACOSX")) continue
                var name = e.name.replace('\\', '/').trimStart('/')
                if (prefix.isNotEmpty() && name.startsWith(prefix)) name = name.substring(prefix.length)
                if (name.equals(PATCH_TXT, ignoreCase = true)) name = PATCH_TXT
                if (name.isEmpty() || !written.add(name)) continue
                out.putNextEntry(ZipEntry(name))
                zip.getInputStream(e).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
}
