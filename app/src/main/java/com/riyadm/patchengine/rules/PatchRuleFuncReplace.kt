package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.IOException
import java.util.zip.ZipFile

class PatchRuleFuncReplace : PatchRule() {

    private val keywords: MutableList<String> = ArrayList()
    private val replaceContents: MutableList<String> = ArrayList()
    private var strFunction: String? = null
    private var targetFile: String? = null

    init {
        this.keywords.add(TARGET)
        this.keywords.add(FUNCTION)
        this.keywords.add(REPLACE)
        this.keywords.add(strEnd)
    }

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        this.startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            line = line.trim { it <= ' ' }
            if (strEnd != line) {
                if (!super.parseAsKeyword(line, br)) {
                    when (line) {
                        TARGET -> this.targetFile = br.readLine()!!.trim { it <= ' ' }
                        FUNCTION -> this.strFunction = br.readLine()!!.trim { it <= ' ' }
                        else -> {
                            if (REPLACE == line) {
                                line = readMultiLines(br, this.replaceContents, false, this.keywords)
                                continue
                            }
                            logger.error(R.string.patch_error_cannot_parse, br.currentLine, line)
                        }
                    }
                }
                line = br.readLine()
            } else {
                return
            }
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        iPatchContext.error(R.string.general_error, "Not supported yet.")
        return null
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        return false
    }

    override fun isSmaliNeeded(): Boolean {
        return false
    }

    companion object {
        private const val FUNCTION = "FUNCTION:"
        private const val REPLACE = "REPLACE:"
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/FUNCTION_REPLACE]"
    }
}
