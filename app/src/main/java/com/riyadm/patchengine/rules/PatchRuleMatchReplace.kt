package com.riyadm.patchengine.rules

import android.annotation.SuppressLint
import android.util.Log
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.PathFinder
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.Section
import com.riyadm.patchengine.interfaces.IPatchContext
import org.apache.commons.io.IOUtils.closeQuietly
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import java.util.zip.ZipFile

class PatchRuleMatchReplace : PatchRule() {

    private var bDotall = false
    private var bRegex = false
    private var isWildMatch = false
    private val keywords: MutableList<String> = ArrayList()
    private val matches: MutableList<String> = ArrayList()
    private var pathFinder: PathFinder? = null
    private var targetText: String? = null
    private val replaces: MutableList<String> = ArrayList()
    private var replacingStr: String? = null

    init {
        keywords.add(TARGET)
        keywords.add(MATCH)
        keywords.add(REGEX)
        keywords.add(REPLACE)
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
            if (strEnd == line) {
                break
            }
            if (!super.parseAsKeyword(line, br)) {
                Log.d(TAG, "Parse MatchReplace rule")
                when (line) {
                    TARGET -> {
                        val target = br.readLine()!!.trim { it <= ' ' }
                        targetText = target
                        pathFinder = PathFinder(logger, target, br.currentLine)
                    }
                    REGEX -> bRegex = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                    DOTALL -> bDotall = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                    else -> {
                        if (MATCH == line) {
                            line = readMultiLines(br, matches, true, keywords)
                            // Log.d("MatchReplace", String.format("Match: %s", line));
                            continue
                        }
                        if (REPLACE == line) {
                            line = readMultiLines(br, replaces, false, keywords)
                            // Log.d("MatchReplace", String.format("Replace: %s", line));
                            continue
                        }
                        logger.error(R.string.patch_error_cannot_parse, br.currentLine, line)
                    }
                }
            }
            line = br.readLine()
        }
        //  PathFinder pathFinder2 = pathFinder;
        val pathFinder = pathFinder
        if (pathFinder != null) {
            isWildMatch = pathFinder.isWildMatch()
        }
    }

    @SuppressLint("WrongConstant")
    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        val logger = iPatchContext
        Log.d(TAG, "Execute MatchReplace rule")
        var pattern: Pattern? = null
        preProcessing(logger, matches)
        preProcessing(logger, replaces)
        if (bRegex) {
            val regStr = matches[0]
            DLog.d(TAG, regStr)
            try {
                if (bDotall) {
                    pattern = Pattern.compile(regStr.trim { it <= ' ' }, Pattern.DOTALL)
                } else {
                    pattern = Pattern.compile(regStr.trim { it <= ' ' })
                }
            } catch (e: PatternSyntaxException) {
                logger.error(R.string.patch_error_regex_syntax, e.message)
                return null
            }
        } else {
            pattern = null
        }

        //происходит перебор файлов для патчинга
        // String nextPath = pathFinder.getNextPath();
        var nextPath = pathFinder!!.getNextPath()
        while (nextPath != null) {
            Log.d(TAG, "nextPath: $nextPath")
            //     Log.d(TAG, pattern.pattern());
            executeOnEntry(
                projectHelper,
                logger,
                nextPath,
                pattern
            )
            // nextPath = pathFinder.getNextPath();
            nextPath = pathFinder!!.getNextPath()
        }
        //Log.d(TAG, "next patch null");
        return null
    }

    /**
     * матчим файл, если совпадения находятся патчим его
     *
     * @param projectHelper
     * @param patchCtx      контекст патча
     * @param targetFile    файл для парсинга
     * @param pattern       паттерн
     */
    private fun executeOnEntry(projectHelper: ProjectHelper, patchCtx: IPatchContext, targetFile: String, pattern: Pattern?) {
        var modified = false
        val filepath = projectHelper.getProjectPath() + "/" + targetFile
        Log.d(TAG, "Start match of target file: $filepath")
        if (pattern != null) {
            try {
                val content = readFileContent(filepath)
                val sections: MutableList<Section> = ArrayList()
                val m = pattern.matcher(content)
                while (m.find()) {
                    var groupStrs: MutableList<String?>? = null
                    val groupCount = m.groupCount()
                    if (groupCount > 0) {
                        groupStrs = ArrayList(groupCount)
                        for (i in 0 until groupCount) {
                            groupStrs.add(m.group(i + 1))
                        }
                    }
                    val found = content.substring(m.start(), m.end())
                    DLog.d(TAG, "Found [$found]")
                    sections.add(Section(m.start(), m.end(), groupStrs))
                }
                if (!sections.isEmpty()) {
                    try {
                        writeReplaces(filepath, content, sections)
                        modified = true
                        val message = projectHelper.mContext!!.getString(R.string.patch_info_num_replaced)
                        patchCtx.info(targetFile + ": " + String.format(message, sections.size), false)
                    } catch (e: IOException) {
                        patchCtx.error(R.string.patch_error_write_to, targetFile)
                    }
                } else if (!isWildMatch) {
                    patchCtx.error(R.string.patch_error_no_match, targetFile)
                }
            } catch (e: IOException) {
                patchCtx.error(R.string.patch_error_read_from, targetFile)
                e.printStackTrace()
            }
        } else {
            Log.d(TAG, "Pattern is null")
            try {
                val lines = readFileLines(filepath)
                val matchedIndexes: MutableList<Int> = ArrayList()
                //  int i2 = 0;
                var i = 0
                while (i < (lines.size - matches.size) + 1) {
                    if (checkMatch(lines, i)) {
                        matchedIndexes.add(i)
                        i += matches.size - 1
                    }
                    //  i2++;
                    i++
                }
                if (matchedIndexes.isEmpty()) {
                    patchCtx.error(R.string.patch_error_no_match, targetFile)
                } else {
                    try {
                        writeReplaces(filepath, lines, matchedIndexes)
                        modified = true
                        patchCtx.info(R.string.patch_info_num_replaced, false, matchedIndexes.size)
                    } catch (e: IOException) {
                        patchCtx.error(R.string.patch_error_write_to, targetFile)
                        e.printStackTrace()
                    }
                }
            } catch (e: IOException) {
                patchCtx.error(R.string.patch_error_read_from, targetFile)
                e.printStackTrace()
            }
        }
    }

    //проверяем ли матчинг валиден
    private fun checkMatch(lines: List<String>, idx: Int): Boolean {
        var i = 0
        while (i < matches.size && lines[idx + i].trim { it <= ' ' } == matches[i]) {
            i++
        }
        return i == matches.size
    }

    //region Writing patched file
    @Throws(IOException::class)
    private fun writeReplaces(filepath: String, content: String, sections: List<Section>) {
        var curReplace: String
        val replaceStr = getReplaceString()
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(filepath)
            var startPos = 0
            for (sec in sections) {
                // for (int i = 0; i < sections.size(); i++) {
                //Section sec = sections.get(i);
                fos.write(content.substring(startPos, sec.start).toByteArray(Charset.defaultCharset()))
                startPos = sec.end
                if (sec.getGroupStrs() == null || sec.getGroupStrs()!!.isEmpty()) {
                    curReplace = replaceStr
                } else {
                    curReplace = getRealReplace(replaceStr, sec)
                }
                fos.write(curReplace.toByteArray(Charset.defaultCharset()))
            }
            fos.write(content.substring(startPos).toByteArray(Charset.defaultCharset()))
            closeQuietly(fos)
            Log.d(TAG, "Writing patched files")
        } finally {
            closeQuietly(fos)
        }
    }

    @Throws(IOException::class)
    private fun writeReplaces(filepath: String, lines: List<String>, matchedIndexes: List<Int>) {
        val replaceStr = getReplaceString()
        var out: BufferedOutputStream? = null
        try {
            out = BufferedOutputStream(FileOutputStream(filepath))
            var startIdx = 0
            for (i in matchedIndexes.indices) {
                val curIdx = matchedIndexes[i]
                writeLines(out, lines, startIdx, curIdx)
                out.write(replaceStr.toByteArray(Charset.defaultCharset()))
                out.write("\n".toByteArray(Charset.defaultCharset()))
                startIdx = curIdx + matches.size
            }
            writeLines(out, lines, startIdx, lines.size)
            Log.d(TAG, "Wring patched files")
        } finally {
            closeQuietly(out)
        }
    }

    @Throws(IOException::class)
    private fun writeLines(out: BufferedOutputStream, lines: List<String>, startIdx: Int, endIdx: Int) {
        for (i in startIdx until endIdx) {
            out.write(lines[i].toByteArray(Charset.defaultCharset()))
            out.write("\n".toByteArray(Charset.defaultCharset()))
        }
    }
    //endregion

    private fun getRealReplace(replaceStr: String, sec: Section): String {
        var result = replaceStr
        val groups = sec.getGroupStrs()
        for (i in groups!!.indices) {
            result = result.replace("\${GROUP" + (i + 1) + "}", groups[i]!!)
        }
        return result
    }

    private fun getReplaceString(): String {
        if (replacingStr == null) {
            if (replaces.isEmpty()) {
                replacingStr = ""
            } else {
                val sb = StringBuilder()
                sb.append(replaces[0])
                for (i in 1 until replaces.size) {
                    sb.append("\n")
                    sb.append(replaces[i])
                }
                replacingStr = sb.toString()
            }
        }
        return replacingStr!!
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        val pathFinder = pathFinder
        if (pathFinder == null || !pathFinder.isValid()) {
            return false
        }
        if (!matches.isEmpty()) {
            return true
        }
        iPatchContext.error(R.string.patch_error_no_match_content)
        return false
    }

    override fun isSmaliNeeded(): Boolean {
        return pathFinder!!.isSmaliNeeded()
    }

    /**
     * Non-null when this rule can run together with neighbouring rules on the same files: a valid
     * regex rule. Rules with an equal key target the same set of files, see [runBatch].
     */
    internal fun batchKey(): String? {
        val finder = pathFinder ?: return null
        if (!bRegex || !finder.isValid() || matches.isEmpty()) return null
        return targetText
    }

    companion object {
        private const val TAG = "PatchRuleMatchReplace"

        /** Single-pass execution of consecutive rules (see [runBatch]). Only turned off to compare against one-by-one runs. */
        @Volatile
        internal var batchingEnabled = true

        /** A rule readied for [runBatch]: its compiled pattern and final replacement text. */
        private class Prepared(val rule: PatchRuleMatchReplace, val pattern: Pattern, val replace: String)

        /**
         * Runs [rules] (consecutive regex rules with one target, see [batchKey]) in a single pass:
         * each file is read once, every rule is applied to it in order in memory, and it is
         * written once if anything changed. Applying rule after rule to the same text gives the
         * same file as running the rules one by one, but costs one read and one write per file
         * instead of one per rule: a 19-rule patch over a few thousand smali files drops from
         * minutes to seconds. Files are processed on several threads (storage latency dominates).
         * The log looks like the one-rule-at-a-time run: a header and the matches per rule.
         */
        @JvmStatic
        internal fun runBatch(
            rules: List<PatchRuleMatchReplace>,
            projectHelper: ProjectHelper,
            ctx: IPatchContext,
            onRuleDone: ((Int) -> Unit)? = null,
        ) {
            val prepared = ArrayList<Prepared?>(rules.size)
            for (rule in rules) {
                rule.preProcessing(ctx, rule.matches)
                rule.preProcessing(ctx, rule.replaces)
                prepared += try {
                    val flags = if (rule.bDotall) Pattern.DOTALL else 0
                    Prepared(rule, Pattern.compile(rule.matches[0].trim { it <= ' ' }, flags), rule.getReplaceString())
                } catch (e: PatternSyntaxException) {
                    null.also { ctx.error(R.string.patch_error_regex_syntax, e.message) }
                }
            }

            // One walk of the project for all rules: they share the same target.
            val tWalk = System.nanoTime()
            val files = LinkedHashSet<String>()
            val finder = rules[0].pathFinder!!
            var next = finder.getNextPath()
            while (next != null) {
                files += next
                next = finder.getNextPath()
            }

            val tScan = System.nanoTime()
            val root = projectHelper.getProjectPath()
            // Per rule: (file, number of replacements) and the files without a match.
            val replaced = Array(rules.size) { java.util.concurrent.ConcurrentLinkedQueue<Pair<String, Int>>() }
            val missed = Array(rules.size) { java.util.concurrent.ConcurrentLinkedQueue<String>() }
            val errors = java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, String>>()
            val reader = rules[0]

            val threads = com.riyadm.apkrepacker.utils.WorkerThreads.count()
            val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
            try {
                val futures = files.map { file ->
                    pool.submit(Runnable {
                        val path = "$root/$file"
                        try {
                            val original = reader.readFileContent(path)
                            var text = original
                            for ((i, p) in prepared.withIndex()) {
                                if (p == null) continue
                                val matcher = p.pattern.matcher(text)
                                var count = 0
                                var out: StringBuilder? = null
                                var last = 0
                                while (matcher.find()) {
                                    count++
                                    if (out == null) out = StringBuilder(text.length + 64)
                                    out.append(text, last, matcher.start())
                                    out.append(expand(p.replace, matcher))
                                    last = matcher.end()
                                }
                                if (count > 0 && out != null) {
                                    out.append(text, last, text.length)
                                    text = out.toString()
                                    replaced[i].add(file to count)
                                } else if (!p.rule.isWildMatch) {
                                    missed[i].add(file)
                                }
                            }
                            if (text !== original) {
                                // Encode first: an OutOfMemoryError here must not leave a truncated file.
                                val bytes = text.toByteArray(Charset.defaultCharset())
                                java.io.FileOutputStream(path).use { it.write(bytes) }
                            }
                        } catch (e: IOException) {
                            errors.add(-1 to file)
                        } catch (t: Throwable) {
                            DLog.e(TAG, t)
                            errors.add(-2 to file)
                        }
                    })
                }
                for (f in futures) f.get()
            } finally {
                pool.shutdownNow()
            }

            val tDone = System.nanoTime()
            ctx.info(
                "Applied ${rules.size} rules to ${files.size} files: finding files " +
                    "${"%.1f".format((tScan - tWalk) / 1e9)} s, reading and patching ${"%.1f".format((tDone - tScan) / 1e9)} s",
                false,
            )
            // Log per rule, in order, like the one-by-one run.
            val message = projectHelper.mContext!!.getString(R.string.patch_info_num_replaced)
            for ((i, rule) in rules.withIndex()) {
                ctx.info(R.string.patch_start_apply, true, rule.startLine)
                replaced[i].sortedBy { it.first }.forEach { (file, count) ->
                    ctx.info(file + ": " + String.format(message, count), false)
                }
                missed[i].sorted().forEach { ctx.error(R.string.patch_error_no_match, it) }
                onRuleDone?.invoke(i)
            }
            errors.sortedBy { it.second }.forEach { (kind, file) ->
                ctx.error(if (kind == -1) R.string.patch_error_read_from else R.string.general_error, file)
            }
        }

        /** The replacement for one match: `${GROUP1}`.. are replaced by the match's capture groups. */
        private fun expand(replace: String, matcher: java.util.regex.Matcher): String {
            if (matcher.groupCount() == 0 || !replace.contains("\${GROUP")) return replace
            var result = replace
            for (g in 1..matcher.groupCount()) {
                result = result.replace("\${GROUP$g}", matcher.group(g) ?: "")
            }
            return result
        }

        private const val DOTALL = "DOTALL:"
        private const val MATCH = "MATCH:"
        private const val REGEX = "REGEX:"
        private const val REPLACE = "REPLACE:"
        private const val TARGET = "TARGET:"
        private const val strEnd = "[/MATCH_REPLACE]"
    }
}
