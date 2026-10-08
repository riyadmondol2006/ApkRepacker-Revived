package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.utils.HexUtil
import com.riyadm.patchengine.utils.IOUtil
import org.apache.commons.io.IOUtils
import org.apache.commons.io.IOUtils.closeQuietly
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class PatchRuleReviseSig : PatchRule() {

    private val targetList: MutableList<String> = ArrayList()

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        this.startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (strEnd != line2) {
                if (!super.parseAsKeyword(line2, br)) {
                    if (TARGET == line2) {
                        this.targetList.add(br.readLine()!!.trim { it <= ' ' })
                    } else {
                        logger.error(R.string.patch_error_cannot_parse, br.currentLine, line2)
                    }
                }
                line = br.readLine()
            } else {
                return
            }
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        val logger = iPatchContext
        val hexRSA = getHexRSA(projectHelper.getApkPath())
        val packageName = projectHelper.getApkPackage()
        val targetFile = logger.getDecodeRootPath() + "/" + this.targetList[0]
        try {
            IOUtil.writeToFile(
                targetFile,
                readFileContent(targetFile).replace("%PACKAGE_NAME%", packageName!!).replace("%RSA_DATA%", hexRSA!!)
            )
            return null
        } catch (e: Exception) {
            logger.error(R.string.patch_error_write_to, targetFile)
            return null
        }
    }

    private fun getHexRSA(apkPath: String?): String? {
        var ze: ZipEntry? = null
        var zfile: ZipFile? = null
        var input: BufferedInputStream? = null
        var output: ByteArrayOutputStream? = null
        try {
            zfile = ZipFile(apkPath)
            val entries = zfile.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                ze = e
                if (!e.isDirectory) {
                    val entryName = e.name
                    if (entryName.endsWith(".RSA") || entryName.endsWith(".rsa") || entryName.endsWith(".DSA") || entryName.endsWith(".dsa")) {
                        input = BufferedInputStream(zfile.getInputStream(e))
                        output = ByteArrayOutputStream()
                        IOUtils.copy(input, output)
                    }
                }
            }
            input = BufferedInputStream(zfile.getInputStream(ze))
            output = ByteArrayOutputStream()
            IOUtils.copy(input, output)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        closeQuietly(input)
        closeQuietly(output)
        closeQuietly(zfile)
        if (output != null) {
            return HexUtil.bytesToHexString(output.toByteArray())
        }
        return null
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        return !this.targetList.isEmpty()
    }

    override fun isSmaliNeeded(): Boolean {
        return true
    }

    companion object {
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/SIGNATURE_REVISE]"
    }
}
