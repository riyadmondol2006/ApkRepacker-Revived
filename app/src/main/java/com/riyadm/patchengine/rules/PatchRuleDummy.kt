package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.IOException
import java.util.zip.ZipFile

class PatchRuleDummy : PatchRule() {

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
                    logger.error(R.string.patch_error_cannot_parse, br.currentLine, line2)
                }
                line = br.readLine()
            } else {
                return
            }
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        return null
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        return true
    }

    override fun isSmaliNeeded(): Boolean {
        return false
    }

    companion object {
        private const val strEnd = "[/DUMMY]"
    }
}
