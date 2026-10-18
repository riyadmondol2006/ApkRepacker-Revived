package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.NativeScan
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.PathFinder
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.IOException
import java.util.regex.Pattern
import java.util.zip.ZipFile

class PatchRuleMatchAssign : PatchRule() {

    private val TAG = "PatchRuleMatchAssign"

    private val assigns: MutableList<String> = ArrayList()
    private var bDotall = false
    private var bRegex = false
    private val keywords: MutableList<String> = ArrayList()
    private val matches: MutableList<String> = ArrayList()
    private var pathFinder: PathFinder? = null

    init {
        this.keywords.add(TARGET)
        this.keywords.add(MATCH)
        this.keywords.add(REGEX)
        this.keywords.add(ASSIGN)
        this.keywords.add(DOTALL)
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
                        TARGET -> this.pathFinder = PathFinder(logger, br.readLine()!!.trim { it <= ' ' }, br.currentLine)
                        REGEX -> this.bRegex = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                        DOTALL -> this.bDotall = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                        else -> {
                            if (MATCH == line) {
                                line = readMultiLines(br, this.matches, true, this.keywords)
                                continue
                            }
                            if (ASSIGN == line) {
                                line = readMultiLines(br, this.assigns, false, this.keywords)
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
        preProcessing(iPatchContext, this.matches)
        // A broken regex is reported once and nothing is assigned (it used to throw for every
        // file and abort the whole patch). Checked after preProcessing: variables are part of it.
        regexError(this.matches[0], this.bDotall)?.let {
            iPatchContext.error(R.string.patch_error_regex_syntax, it)
            return null
        }
        // Files are taken a chunk at a time, and the ones that can't match are skipped without
        // being read (see NativeScan); the first match still ends the search.
        val regex = validRegex(this.matches[0], this.bDotall)
        val chunk = ArrayList<String>(NativeScan.CHUNK)
        val finder = this.pathFinder!!.restart()
        var nextPath = finder.getNextPath()
        while (nextPath != null) {
            chunk.clear()
            while (nextPath != null && chunk.size < NativeScan.CHUNK) {
                chunk += nextPath
                nextPath = finder.getNextPath()
            }
            val canMatch = regex?.let { NativeScan.candidates("${projectHelper.getProjectPath()}", chunk, regexes = listOf(it)) }
            for ((i, path) in chunk.withIndex()) {
                if (canMatch != null && !canMatch[i]) continue
                if (executeOnEntry(projectHelper, zipFile, iPatchContext, path)) return null
            }
        }
        return null
    }

    private fun executeOnEntry(
        activity: ProjectHelper,
        patchZip: ZipFile,
        patchCtx: IPatchContext,
        targetFile: String
    ): Boolean {
        val pattern: Pattern
        val filepath = activity.getProjectPath() + "/" + targetFile
        DLog.d(TAG, "Start assigning: $filepath")
        try {
            val fileContent = readFileContent(filepath)
            val regStr = matches[0]
            if (bDotall) {
                pattern = Pattern.compile(regexSource(regStr), Pattern.DOTALL)
            } else {
                pattern = Pattern.compile(regexSource(regStr))
            }
            val m = pattern.matcher(fileContent)
            if (m.find(0)) {
                var groupStrs: MutableList<String?>? = null
                val groupCount = m.groupCount()
                if (groupCount > 0) {
                    groupStrs = ArrayList(groupCount)
                    for (i in 0 until groupCount) {
                        groupStrs.add(m.group(i + 1))
                    }
                }
                for (strAssign in assigns) {
                    val strAssign2 = strAssign.trim { it <= ' ' }
                    val position = strAssign2.indexOf("=")
                    if (position != -1) {
                        val name = strAssign2.substring(0, position).trim { it <= ' ' }
                        // "NAME = value" is NAME and value, like NAME=value.
                        val assignedVal = getRealValue(strAssign2.substring(position + 1).trim { it <= ' ' }, groupStrs)
                        patchCtx.setVariableValue(name, assignedVal)
                        patchCtx.info("%s=\"%s\"", false, name, assignedVal)
                    }
                }
                return true
            }
            return false
        } catch (e: IOException) {
            patchCtx.error(R.string.patch_error_read_from, filepath)
            return false
        }
    }

    private fun getRealValue(valueBefore: String, groupStrs: List<String?>?): String {
        // No capturing groups (regex with none, e.g. a constant ASSIGN value): nothing to expand.
        val groups = groupStrs ?: return valueBefore
        var result = valueBefore
        for (i in groups.indices) {
            // A group that didn't participate in the match is null -> expand to "" instead of NPE.
            result = result.replace("\${GROUP" + (i + 1) + "}", groups[i] ?: "")
        }
        return result
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        val pathFinder2 = this.pathFinder
        if (pathFinder2 == null || !pathFinder2.isValid()) {
            return false
        }
        if (this.matches.isEmpty()) {
            iPatchContext.error(R.string.patch_error_no_match_content)
            return false
        } else if (this.bRegex) {
            return true
        } else {
            iPatchContext.error(R.string.patch_error_regex_not_true)
            return false
        }
    }

    override fun isSmaliNeeded(): Boolean {
        return this.pathFinder!!.isSmaliNeeded()
    }

    companion object {
        private const val ASSIGN = "ASSIGN:"
        private const val DOTALL = "DOTALL:"
        private const val MATCH = "MATCH:"
        private const val REGEX = "REGEX:"
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/MATCH_ASSIGN]"
    }
}
