package com.riyadm.patchengine

import android.util.Log
import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.interfaces.IBeforeAddFile
import com.riyadm.patchengine.interfaces.IPatchContext
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import com.riyadm.apkrepacker.utils.SafeZip
import java.util.zip.ZipFile

abstract class PatchRule {

    /**
     * возвращвет имя правила патча
     * @return
     */
    var ruleName: String? = null
        protected set

    @JvmField
    var startLine: Int = 0

    /**
     * стартуем правило патча
     * @param projectHelper
     * @param zipFile файл патча
     * @param iPatchContext контекст
     * @return
     */
    abstract fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String?

    /**
     * нужен ли smali патчу
     * @return
     */
    abstract fun isSmaliNeeded(): Boolean

    /**
     * проверяет ли валиден патч
     * @param iPatchContext
     * @return
     */
    abstract fun isValid(iPatchContext: IPatchContext): Boolean

    /**
     * парсинг правил патча
     * @param linedReader
     * @param iPatchContext
     * @throws IOException
     */
    @Throws(IOException::class)
    abstract fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext)

    @Throws(IOException::class)
    fun readFileContent(filepath: String): String {
        // Buffered and closed (the original left the stream open).
        return BufferedInputStream(FileInputStream(File(filepath))).use {
            IOUtils.toString(it, StandardCharsets.UTF_8)
        }
        /*File f = new File(filepath);
        StringBuilder sb = new StringBuilder((int) f.length() + 32);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
        try {
            String line = br.readLine();
            if (line != null) {
                sb.append(line);
            }
            while (true) {
                String readLine = br.readLine();
                if (readLine == null) {
                    return sb.toString();
                }
                sb.append("\n");
                sb.append(readLine);
            }
        } finally {
            closeQuietly(br);
        }*/
    }

    @Throws(IOException::class)
    fun readFileLines(filepath: String): MutableList<String> {
        val lines: MutableList<String> = ArrayList()
        // The reader is closed once, after the whole file is read. The original closed it inside
        // the loop, so the second readLine() threw "Stream closed" and every non-regex MATCH on a
        // non-empty file failed with "Cannot read from …". Blank lines are still skipped.
        BufferedReader(InputStreamReader(FileInputStream(filepath))).use { br ->
            while (true) {
                val readLine = br.readLine() ?: return lines
                if ("" != readLine.trim { it <= ' ' }) {
                    lines.add(readLine)
                }
            }
        }
    }

    @Throws(IOException::class)
    protected fun readMultiLines(
        br: BufferedReader,
        lines: MutableList<String>,
        bTrim: Boolean,
        endKeywords: List<String>
    ): String? {
        while (true) {
            val line = br.readLine() ?: return null
            val trimmed = line.trim { it <= ' ' }
            // Keywords are found with surrounding spaces too, also in untrimmed (REPLACE) blocks:
            // an indented "[/MATCH_REPLACE]" used to be taken as replacement text, and the block
            // then swallowed the rest of the patch.
            if (endKeywords.contains(trimmed)) {
                return trimmed
            }
            if (bTrim) {
                // Blank lines in a trimmed (MATCH) block are dropped: files are compared without
                // their blank lines, and a blank first line would be an empty regex that matches
                // everywhere.
                if (trimmed.isNotEmpty()) {
                    lines.add(trimmed)
                }
            } else {
                lines.add(line)
            }
        }
    }

    /**
     * The trimmed regex of a MATCH, as the rule compiles it, or null when it doesn't compile (the
     * rule's own Pattern.compile then fails as before).
     */
    protected fun validRegex(regex: String, dotall: Boolean): String? {
        val trimmed = regexSource(regex)
        return try {
            Pattern.compile(trimmed, if (dotall) Pattern.DOTALL else 0)
            trimmed
        } catch (e: java.util.regex.PatternSyntaxException) {
            null
        }
    }

    /** The values of each [preProcessing] list as written in patch.txt, before variables were expanded. */
    private val rawValues = java.util.IdentityHashMap<MutableList<String>, List<String>>()

    protected fun preProcessing(ctx: IPatchContext, values: MutableList<String>) {
        // Expanded from the text as written each time, so a rule run again (GOTO loop) sees the
        // variables' current values instead of the ones from its first run.
        val raw = rawValues.getOrPut(values) { ArrayList(values) }
        for (i in values.indices) {
            values[i] = assignValues(ctx, raw[i]) ?: raw[i]
        }
    }

    /** The PatternSyntaxException message when [regex] (trimmed, as rules compile it) doesn't compile, else null. */
    protected fun regexError(regex: String, dotall: Boolean): String? {
        return try {
            Pattern.compile(regexSource(regex), if (dotall) Pattern.DOTALL else 0)
            null
        } catch (e: java.util.regex.PatternSyntaxException) {
            e.message ?: "invalid regex"
        }
    }

    @Throws(Exception::class)
    fun addFilesInZip(projectHelper: ProjectHelper, zipFile: String, hook: IBeforeAddFile?, logger: IPatchContext) {
        var consumed: Boolean
        val targetDir = projectHelper.getProjectPath()
        ZipFile(zipFile).use { zfile2 ->
            val entries = zfile2.entries()
            while (entries.hasMoreElements()) {
                val ze = entries.nextElement()
                // Never write outside the project for names like "../x" (see SafeZip). The resolved
                // file is also what gets written, so "a\b.xml" (zips made on Windows) lands in a/.
                val target = if (targetDir == null) null else SafeZip.resolve(File(targetDir), ze.name)
                if (target == null) {
                    logger.error(R.string.general_error, "Skipped unsafe entry name: " + ze.name)
                    continue
                }
                if (!ze.isDirectory) {
                    if (hook != null) {
                        consumed = hook.consumeAddedFile(projectHelper, zfile2, ze)
                    } else {
                        consumed = false
                    }
                    if (!consumed) {
                        addFileEntry(zfile2, ze, target, logger)
                    }
                }
            }
        }
    }

    private fun addFileEntry(zfile: ZipFile, entry: ZipEntry, target: File, logger: IPatchContext) {
        // The original created each missing folder by hand and gave up on the first one that
        // already existed; mkdirs() does both.
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            logger.error(R.string.failed_create_dir, parent.path)
            return
        }
        try {
            zfile.getInputStream(entry).use { input ->
                FileOutputStream(target).use { out -> IOUtils.copy(input, out) }
            }
        } catch (e2: Exception) {
            logger.error(R.string.general_error, e2.message)
        }
    }

    @Throws(IOException::class)
    fun parseAsKeyword(line: String?, br: LinedReader): Boolean {
        // A comment line between a rule's keywords (MATCH:/REPLACE: blocks keep theirs as text).
        if (line != null && line.startsWith("#")) return true
        if (NAME != line) {
            return false
        }
        val readLine = br.readLine()
        this.ruleName = readLine
        if (readLine == null) {
            return true
        }
        this.ruleName = readLine.trim { it <= ' ' }
        return true
    }

    /**
     * проверка ли папка является smali папкой
     * @param targetFile входная папка
     * @return true/false
     */
    fun isInSmaliFolder(targetFile: String?): Boolean {
        if (targetFile != null) {
            // The first folder: "smali/com/a/B.smali" is in smali (lastIndexOf only saw "smali/B.smali").
            val pos = targetFile.indexOf('/')
            if (pos != -1) {
                val firstDir = targetFile.substring(0, pos)
                return "smali" == firstDir || firstDir.startsWith("smali_")
            }
        }
        return false
    }

    companion object {

        private const val TAG = "PatchRule"

        private val SPACED_SELF_CLOSE = Regex(" +/>")

        /**
         * The regex a MATCH compiles to: trimmed, and with " />" also matching "/>". Patches were
         * written against APK Editor's decoder, which put a space before "/>" in XML; apktool
         * writes `<category android:name="..."/>`, so such a MATCH would silently miss.
         */
        @JvmStatic
        fun regexSource(raw: String): String = SPACED_SELF_CLOSE.replace(raw.trim { it <= ' ' }, "\\\\s*/>")

        private const val NAME = "NAME:"

        /**
         * cоздает переменные на основе регулярного выражения
         * пример : TEXT=${GROUP1}
         * @param ctx контекст
         * @param rawStr строка
         * @return возвращеает TEXT=TEST
         */
        @JvmStatic
        fun assignValues(ctx: IPatchContext, rawStr: String): String? {
            Log.d(TAG, "start assign values")
            val replaces = ArrayList<ReplaceRec>()
            var position = rawStr.indexOf("\${")
            while (position != -1) {
                position += 2
                val endPos = rawStr.indexOf("}", position)
                if (endPos != -1) {
                    val realVal = ctx.getVariableValue(rawStr.substring(position, endPos))
                    if (realVal != null) {
                        Log.d(TAG, "adding replaces $realVal")
                        replaces.add(ReplaceRec(position - 2, endPos + 1, realVal))
                    }
                    Log.d(TAG, "real value null")
                    position = rawStr.indexOf("\${", endPos)
                } else {
                    break
                }
            }
            if (!replaces.isEmpty()) {
                val sb = StringBuilder()
                var startPos = 0
                for (rec in replaces) {
                    val curPos = rec.startPos
                    if (curPos > startPos) {
                        sb.append(rawStr.substring(startPos, curPos))
                    }
                    sb.append(rec.replacing)
                    startPos = rec.endPos
                }
                if (startPos < rawStr.length) {
                    Log.i(TAG, rawStr.substring(startPos))
                    sb.append(rawStr.substring(startPos))
                }
                Log.d(TAG, "end assign values")
                return sb.toString()
            }
            return null
        }
    }
}
