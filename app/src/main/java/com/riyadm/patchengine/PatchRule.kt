package com.riyadm.patchengine

import android.util.Log
import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.interfaces.IBeforeAddFile
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.utils.IOUtil
import org.apache.commons.io.IOUtils
import org.apache.commons.io.IOUtils.closeQuietly
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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
        var string: String?
        var line = br.readLine()
        while (true) {
            string = line
            if (line != null) {
                var s: String = line
                if (bTrim) {
                    s = line.trim { it <= ' ' }
                }
                string = s
                if (endKeywords.contains(s)) {
                    break
                }
                lines.add(s)
                line = br.readLine()
                continue
            }
            break
        }
        return string
    }

    protected fun preProcessing(ctx: IPatchContext, values: MutableList<String>) {
        for (i in values.indices) {
            val assignedVal = assignValues(ctx, values[i])
            if (assignedVal != null) {
                Log.d("PatchRule", assignedVal)
                values[i] = assignedVal
            } else
                Log.d("PatchRule", "assignedVal null")
        }
    }

    private fun getParentFolder(path: String): String? {
        val pos = path.lastIndexOf("/")
        if (pos > 0) {
            return path.substring(0, pos)
        }
        return null
    }

    private fun addFileEntry(
        projectHelper: ProjectHelper,
        zfile: ZipFile,
        entry: ZipEntry,
        targetDir: String?,
        logger: IPatchContext
    ) {
        val path = targetDir + "/" + entry.name
        var parent = getParentFolder(path)
        // assert parent != null;
        while (!File(parent!!).exists()) {
            parent = getParentFolder(parent)
        }
        var parentDir: String = parent
        val paths = Pattern.compile("/").split(path.substring(parentDir.length + 1))
        if (paths.size > 1) {
            var i = 0
            while (i < paths.size - 1) {
                try {
                    IOUtil.makeDir(parentDir, paths[i])
                    parentDir = parentDir + "/" + paths[i]
                    i++
                } catch (e: Exception) {
                    logger.error(R.string.failed_create_dir, e.message)
                    return
                }
            }
        }
        var input: InputStream? = null
        try {
            input = zfile.getInputStream(entry)
            val out = FileOutputStream(path)
            IOUtils.copy(input, out)
        } catch (e2: Exception) {
            logger.error(R.string.general_error, e2.message)
        } finally {
            closeQuietly(input)
        }
    }

    @Throws(Exception::class)
    fun addFilesInZip(projectHelper: ProjectHelper, zipFile: String, hook: IBeforeAddFile?, logger: IPatchContext) {
        var consumed: Boolean
        val targetDir = projectHelper.getProjectPath()
        val zfile2 = ZipFile(zipFile)
        val entries = zfile2.entries()
        while (entries.hasMoreElements()) {
            val ze = entries.nextElement()
            // Never write outside the project for names like "../x" (see SafeZip).
            if (targetDir == null || SafeZip.resolve(File(targetDir), ze.name) == null) {
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
                    addFileEntry(projectHelper, zfile2, ze, targetDir, logger)
                }
            }
        }
        zfile2.close()
    }

    @Throws(IOException::class)
    fun parseAsKeyword(line: String?, br: LinedReader): Boolean {
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
            val pos = targetFile.lastIndexOf('/')
            if (pos != -1) {
                val firstDir = targetFile.substring(0, pos)
                return "smali" == firstDir || firstDir.startsWith("smali_")
            }
        }
        return false
    }

    companion object {

        private const val TAG = "PatchRule"

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
