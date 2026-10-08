package com.riyadm.patchengine.resource

import android.util.Log
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IBeforeAddFile
import com.riyadm.patchengine.rules.PatchRuleMerge
import org.apache.commons.io.IOUtils.closeQuietly
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ResourceMerger(private val patchRuleMerge: PatchRuleMerge, private val rootPath: String?) : IBeforeAddFile {

    @Throws(Exception::class)
    override fun consumeAddedFile(projectHelper: ProjectHelper, zipFile: ZipFile, zipEntry: ZipEntry): Boolean {
        val zfile = zipFile
        val entry = zipEntry
        val name = entry.name
        val targetPath = this.rootPath + "/" + name
        if ("res/values/public.xml" == name) {
            return true
        }
        if (patchRuleMerge.replacedIds == null || !name.endsWith(".smali") ||
            ((!name.startsWith("smali/") && !name.startsWith("smali_")) || name.indexOf('/') == -1)
        ) {
            val f = File(targetPath)
            if (!f.exists()) {
                if (name.startsWith("res/")) {
                    f.parentFile.mkdirs()
                }
                return false
            }
            if (name.endsWith(".xml")) {
                val paths = Pattern.compile("/").split(name)
                if (paths.size == 3 && paths[0] == "res" && (paths[1] == "values" || paths[1].startsWith("values-"))) {
                    mergeResourceFiles(targetPath, zfile, entry)
                    return true
                }
            }
            return false
        }
        refactorAndSaveSmaliFiles(targetPath, zfile, entry)
        //activity.getResListAdapter().fileModified(name.substring(0, pos + 1) + "a.smali", targetPath);
        return true
    }

    @Throws(IOException::class)
    private fun refactorAndSaveSmaliFiles(targetPath: String, zfile: ZipFile, entry: ZipEntry) {
        val lines = readZipEntry(zfile, entry)
        var bw: BufferedWriter? = null
        try {
            val parentFolder = File(targetPath).parentFile
            if (!parentFolder.exists()) {
                parentFolder.mkdirs()
            }
            bw = BufferedWriter(OutputStreamWriter(FileOutputStream(targetPath)))
            for (i in lines.indices) {
                bw.write(refactorId(lines[i]))
                bw.write(10)
            }
        } finally {
            closeQuietly(bw)
        }
    }

    private fun refactorId(line: String): String {
        var line = line
        var idModified = false
        var pos = line.indexOf("0x7f")
        while (pos != -1 && pos + 10 <= line.length) {
            val originStr = line.substring(pos, pos + 10)
            val newId = patchRuleMerge.replacedIds!!.get(ResourceItem.string2Id(originStr))
            if (newId != 0) {
                line = line.replace(originStr, ResourceItem.id2String(newId))
                idModified = true
            } else {
                Log.e("DEBUG", "Cannot find id $originStr")
            }
            pos = line.indexOf("0x7f", pos + 10)
        }
        if (!idModified || !line.trim { it <= ' ' }.startsWith("const/high16 v")) {
            return line
        }
        return line.replace("const/high16 v", "const v")
    }

    @Throws(IOException::class)
    private fun readZipEntry(zfile: ZipFile, entry: ZipEntry): List<String> {
        val lines: MutableList<String> = ArrayList()
        var br: BufferedReader? = null
        try {
            br = BufferedReader(InputStreamReader(zfile.getInputStream(entry)))
            while (true) {
                val readLine = br.readLine() ?: return lines
                lines.add(readLine)
            }
        } finally {
            closeQuietly(br)
        }
    }

    @Throws(Exception::class)
    private fun mergeResourceFiles(path: String, zfile: ZipFile, entry: ZipEntry) {
        var br: BufferedReader? = null
        try {
            br = BufferedReader(InputStreamReader(zfile.getInputStream(entry)))
            val items: MutableList<String> = ArrayList()
            while (true) {
                val readLine = br.readLine()
                if (readLine != null) {
                    val line2 = readLine.trim { it <= ' ' }
                    if (!line2.startsWith("<?xml") && !line2.startsWith("<resources>")) {
                        if (!line2.startsWith("</resources>")) {
                            items.add(line2)
                        }
                    }
                } else {
                    patchRuleMerge.appendResourceLines(path, items)
                    return
                }
            }
        } finally {
            closeQuietly(br)
        }
    }
}
