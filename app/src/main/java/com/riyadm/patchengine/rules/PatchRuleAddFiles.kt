package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.utils.IOUtil
import com.riyadm.patchengine.utils.RandomHelper
import com.riyadm.apkrepacker.utils.SafeZip
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

class PatchRuleAddFiles : PatchRule() {

    private var isExtract = false
    private var sourceFile: String? = null
    private var targetFile: String? = null

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (strEnd == line2) {
                break
            } else if (super.parseAsKeyword(line2, br)) {
                line = br.readLine()
            } else {
                when (line2) {
                    SOURCE -> sourceFile = br.readLine()!!.trim { it <= ' ' }
                    TARGET -> targetFile = br.readLine()!!.trim { it <= ' ' }
                    EXTRACT -> isExtract = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                    else -> logger.error(R.string.patch_error_cannot_parse, br.currentLine, line2)
                }
                line = br.readLine()
            }
        }
        val str = targetFile
        if (str != null && str.endsWith("/")) {
            val str2 = str
            targetFile = str2.substring(0, str2.length - 1)
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        val patchZip = zipFile
        val logger = iPatchContext
        logger.info("Start Adding new files/folder", false)
        val entry = patchZip.getEntry(sourceFile)
        if (entry == null) {
            logger.error(R.string.patch_error_no_entry, sourceFile)
            return null
        }
        try {
            patchZip.getInputStream(entry).use { input ->
                if (!isExtract) {
                    val projectDir = File(projectHelper.getProjectPath() ?: "")
                    val resolved = SafeZip.resolve(projectDir, targetFile ?: "")
                    if (resolved == null) {
                        logger.error(R.string.general_error, "Unsafe TARGET skipped: $targetFile")
                        return null
                    }
                    val path = resolved.path
                    logger.info("Copying files from " + patchZip.name + " to " + path, false)
                    addFile(path, input)
                } else {
                    val path = projectHelper.getAppDataPath() + RandomHelper.getRandomString(6)
                    FileOutputStream(path).use { fos -> IOUtil.copy(input, fos) }
                    logger.info("Copying files from " + patchZip.name + " to " + path, false)
                    addFilesInZip(projectHelper, path, null, logger)
                }
            }
        } catch (e: Exception) {
            logger.error(R.string.general_error, e.message)
        }
        logger.info("Finish Adding new files/folder", false)
        return null
    }

    @Throws(IOException::class)
    fun addFile(targetPath: String, filePath: InputStream) {
        val pos = targetPath.lastIndexOf('/')
        val fileName = targetPath.substring(pos + 1)
        val dirPath = targetPath.substring(0, pos)
        val newDir = File(dirPath)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        FileOutputStream(targetPath).use { IOUtils.copy(filePath, it) }
    }

    override fun isSmaliNeeded(): Boolean {
        return super.isInSmaliFolder(targetFile)
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        if (sourceFile == null) {
            iPatchContext.error(R.string.patch_error_no_source_file)
            return false
        } else if (targetFile != null) {
            return true
        } else {
            iPatchContext.error(R.string.patch_error_no_target_file)
            return false
        }
    }

    companion object {
        private const val EXTRACT = "EXTRACT:"
        private const val SOURCE = "SOURCE:"
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/ADD_FILES]"
    }
}
