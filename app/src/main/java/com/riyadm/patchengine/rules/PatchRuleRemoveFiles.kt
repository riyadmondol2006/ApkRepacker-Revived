package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.apkrepacker.utils.SafeZip
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class PatchRuleRemoveFiles : PatchRule() {

    private val targetList: MutableList<String> = ArrayList()

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        var next: String? = null
        this.startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (strEnd != line2) {
                if (super.parseAsKeyword(line2, br)) {
                    line = br.readLine()
                } else if (TARGET == line2) {
                    while (true) {
                        val readLine = br.readLine()
                        next = readLine
                        if (readLine == null) {
                            break
                        }
                        val trimmed = readLine.trim { it <= ' ' }
                        next = trimmed
                        if (trimmed.startsWith("[")) {
                            break
                        } else if ("" != trimmed) {
                            this.targetList.add(trimmed)
                        }
                    }
                    line = next
                } else {
                    logger.error(R.string.patch_error_cannot_parse, br.currentLine, line2)
                    line = br.readLine()
                }
            } else {
                return
            }
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        val rootPath = projectHelper.getProjectPath()
        //ResListAdapter resAdapter = activity.getResListAdapter();
        for (i in this.targetList.indices) {
            val resolved = SafeZip.resolve(File(rootPath ?: ""), this.targetList[i])
            if (resolved == null) {
                iPatchContext.error(R.string.general_error, "Unsafe TARGET skipped: " + this.targetList[i])
                continue
            }
            val filePath = resolved.path
            val pos = filePath.lastIndexOf('/')
            // String dirPath = filePath.substring(0, pos);
            //  String fileName = filePath.substring(pos + 1);
            val delete = File(filePath)
            if (delete.exists()) {
                if (delete.isFile) {
                    try {
                        FileUtils.deleteQuietly(File(filePath))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    try {
                        FileUtils.deleteDirectory(File(filePath))
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return null
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        if (!this.targetList.isEmpty()) {
            return true
        }
        iPatchContext.error(R.string.patch_error_no_target_file)
        return false
    }

    override fun isSmaliNeeded(): Boolean {
        for (file in this.targetList) {
            if (super.isInSmaliFolder(file)) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/REMOVE_FILES]"
    }
}
