package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.IOException
import java.util.zip.ZipFile

class PatchRuleGoto : PatchRule() {

    private var targetRule: String? = null

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (strEnd != line2) {
                if (!super.parseAsKeyword(line2, br)) {
                    if (GOTO == line2) {
                        targetRule = br.readLine()!!.trim { it <= ' ' }
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
        return targetRule
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        val logger = iPatchContext
        if (targetRule == null) {
            logger.error(R.string.patch_error_no_goto_target)
            return false
        }
        val allRuleName = logger.getPatchNames()
        if (allRuleName != null && allRuleName.contains(targetRule)) {
            return true
        }
        logger.error(R.string.patch_error_goto_target_notfound, targetRule)
        return false
    }

    override fun isSmaliNeeded(): Boolean {
        return false
    }

    companion object {
        private const val GOTO = "GOTO:"
        private const val strEnd = "[/GOTO]"
    }
}
