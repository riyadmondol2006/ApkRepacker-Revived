package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.PathFinder
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.Section
import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.IOException
import java.util.regex.Pattern
import java.util.zip.ZipFile

class PatchRuleMatchGoto : PatchRule() {

    private var bDotall = false
    private var isUseRegex = false
    private var gotoRule: String? = null
    private val keywords: MutableList<String> = ArrayList()
    private val matches: MutableList<String> = ArrayList()
    private var pathFinder: PathFinder? = null

    init {
        keywords.add(TARGET)
        keywords.add(MATCH)
        keywords.add(REGEX)
        keywords.add(GOTO)
        keywords.add(DOTALL)
        keywords.add(strEnd)
    }

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            line = line.trim { it <= ' ' }
            if (strEnd != line) {
                if (!super.parseAsKeyword(line, br)) {
                    when (line) {
                        TARGET -> pathFinder = PathFinder(logger, br.readLine()!!.trim { it <= ' ' }, br.currentLine)
                        REGEX -> isUseRegex = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                        DOTALL -> bDotall = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                        else -> {
                            if (MATCH == line) {
                                line = readMultiLines(br, matches, true, keywords)
                                continue
                            }
                            if (GOTO == line) {
                                gotoRule = br.readLine()!!.trim { it <= ' ' }
                                line = br.readLine()
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
        preProcessing(iPatchContext, matches)
        var nextPath = pathFinder!!.getNextPath()
        while (nextPath != null) {
            if (entryMatches(projectHelper, iPatchContext, nextPath)) {
                return gotoRule
            }
            nextPath = pathFinder!!.getNextPath()
        }
        return null
    }

    private fun entryMatches(projectHelper: ProjectHelper, patchCtx: IPatchContext, targetFile: String): Boolean {
        val pattern: Pattern
        val filepath = projectHelper.getProjectPath() + "/" + targetFile
        if (isUseRegex) {
            try {
                val content = readFileContent(filepath)
                val sections: MutableList<Section> = ArrayList()
                val regStr = matches[0]
                if (bDotall) {
                    pattern = Pattern.compile(regStr.trim { it <= ' ' }, Pattern.DOTALL)
                } else {
                    pattern = Pattern.compile(regStr.trim { it <= ' ' })
                }
                val m = pattern.matcher(content)
                var position = 0
                while (m.find(position)) {
                    var groupStrs: MutableList<String?>? = null
                    val groupCount = m.groupCount()
                    if (groupCount > 0) {
                        groupStrs = ArrayList(groupCount)
                        for (i in 0 until groupCount) {
                            groupStrs.add(m.group(i + 1))
                        }
                    }
                    sections.add(Section(m.start(), m.end(), groupStrs))
                    position = m.end()
                }
                return !sections.isEmpty()
            } catch (e: IOException) {
                patchCtx.error(R.string.patch_error_read_from, targetFile)
                return false
            }
        } else {
            try {
                val lines = super.readFileLines(filepath)
                var matches2 = false
                var i2 = 0
                while (i2 < (lines.size - matches.size) + 1) {
                    matches2 = checkMatch(lines, i2)
                    if (matches2) {
                        break
                    }
                    i2++
                }
                return matches2
            } catch (e2: IOException) {
                patchCtx.error(R.string.patch_error_read_from, targetFile)
                return false
            }
        }
    }

    private fun checkMatch(lines: List<String>, idx: Int): Boolean {
        var i = 0
        while (i < matches.size && lines[idx + i].trim { it <= ' ' } == matches[i]) {
            i++
        }
        return i == matches.size
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        val logger = iPatchContext
        val pathFinder = pathFinder
        if (pathFinder == null || !pathFinder.isValid()) {
            return false
        }
        if (matches.isEmpty()) {
            logger.error(R.string.patch_error_no_match_content)
            return false
        } else if (gotoRule == null) {
            logger.error(R.string.patch_error_no_goto_target)
            return false
        } else {
            val allRuleName = logger.getPatchNames()
            if (allRuleName != null && allRuleName.contains(gotoRule)) {
                return true
            }
            logger.error(R.string.patch_error_goto_target_notfound, gotoRule)
            return false
        }
    }

    override fun isSmaliNeeded(): Boolean {
        return pathFinder!!.isSmaliNeeded()
    }

    companion object {
        private const val DOTALL = "DOTALL:"
        private const val GOTO = "GOTO:"
        private const val MATCH = "MATCH:"
        private const val REGEX = "REGEX:"
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/MATCH_GOTO]"
    }
}
