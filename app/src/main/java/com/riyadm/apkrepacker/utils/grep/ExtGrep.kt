package com.riyadm.apkrepacker.utils.grep

import android.annotation.SuppressLint
import android.os.Parcel
import android.os.Parcelable
import com.jecelyin.common.task.JecAsyncTask
import com.jecelyin.common.task.TaskListener
import com.jecelyin.common.task.TaskResult
import com.jecelyin.common.utils.DLog
import com.jecelyin.editor.v2.io.FileEncodingDetector
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * @author https://github.com/drippel/JavaGrep
 */
@Suppress("DEPRECATION")
class ExtGrep : Parcelable {

    @JvmField
    val includeFilePatterns: MutableList<String> = ArrayList()
    @JvmField
    val excludeDirPatterns: MutableList<String> = ArrayList()
    @JvmField
    var filesToProcess: MutableList<File>? = ArrayList()
    @JvmField
    var invertMatch = false
    @JvmField
    var ignoreCase = false
    @JvmField
    var maxCount = 0
    @JvmField
    var printFileNameOnly = false
    @JvmField
    var printByteOffset = false
    @JvmField
    var quiet = false
    @JvmField
    var printCountOnly = false
    @JvmField
    var printFilesWithoutMatch = false
    @JvmField
    var wordRegex = false
    @JvmField
    var lineRegex = false
    @JvmField
    var noMessages = false
    @JvmField
    var printFileName = false
    @JvmField
    var printMatchOnly = false
    @JvmField
    var printLineNumber = false
    @JvmField
    var recurseDirectories = false
    @JvmField
    var skipDirectories = false
    @JvmField
    var excludeFilePatterns: MutableList<String> = ArrayList()
    @JvmField
    var useInclude = false
    @JvmField
    var useExclude = false
    @JvmField
    var beforeContext = 0
    @JvmField
    var afterContext = 0
    private var grepPattern: Pattern? = null
    var regex: String? = null
        private set
    var isUseRegex = false
        private set
    @JvmField
    var mExtensions: ArrayList<String> = ArrayList()

    constructor()

    private constructor(`in`: Parcel) {
        this.invertMatch = `in`.readByte().toInt() != 0
        this.ignoreCase = `in`.readByte().toInt() != 0
        this.maxCount = `in`.readInt()
        this.printFileNameOnly = `in`.readByte().toInt() != 0
        this.printByteOffset = `in`.readByte().toInt() != 0
        this.quiet = `in`.readByte().toInt() != 0
        this.printCountOnly = `in`.readByte().toInt() != 0
        this.printFilesWithoutMatch = `in`.readByte().toInt() != 0
        this.wordRegex = `in`.readByte().toInt() != 0
        this.lineRegex = `in`.readByte().toInt() != 0
        this.noMessages = `in`.readByte().toInt() != 0
        this.printFileName = `in`.readByte().toInt() != 0
        this.printMatchOnly = `in`.readByte().toInt() != 0
        this.printLineNumber = `in`.readByte().toInt() != 0
        this.recurseDirectories = `in`.readByte().toInt() != 0
        this.skipDirectories = `in`.readByte().toInt() != 0
        this.excludeFilePatterns = `in`.createStringArrayList()!!
        this.useInclude = `in`.readByte().toInt() != 0
        this.useExclude = `in`.readByte().toInt() != 0
        this.beforeContext = `in`.readInt()
        this.afterContext = `in`.readInt()
        this.regex = `in`.readString()
        this.filesToProcess = ArrayList()
        `in`.readList(this.filesToProcess!!, List::class.java.classLoader)
    }

    private fun printMessage(msg: String) {

//        if (!quiet) {
//            System.out.println(msg);
//        }
    }

    private fun grepFiles(): List<Result> {
        val results = ArrayList<Result>()
        // at this point the list was expanded into file names
        for (f in filesToProcess!!) {
            if (!f.exists() || !f.canRead())
                continue
            results.addAll(grepFile(f)!!)
        }
        return results
    }

    fun readExcludeFrom(vals: Array<String>) {

        for (`val` in vals) {
            try {
                val br = BufferedReader(FileReader(File(`val`)))
                var line = br.readLine()
                while (line != null) {
                    excludeFilePatterns.add(line)
                    line = br.readLine()
                }

            } catch (e: IOException) {
                DLog.e(e)
            }
        }

    }

    fun readIncludesFrom(vals: Array<String>) {

        for (`val` in vals) {
            try {
                val br = BufferedReader(FileReader(File(`val`)))
                var line = br.readLine()
                while (line != null) {
                    includeFilePatterns.add(line)
                    line = br.readLine()
                }

            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }

    }

    fun printErrorMessage(msg: String?) {

        if (!noMessages) {
            println(msg)
        }
    }

    private fun printMatch(file: File, line: String, lineNumber: Int,
                           startOffset: Int, endOffset: Int, lineStartOffset: Int, matchStart: Int, matchEnd: Int, beforeContextLines: List<String>,
                           afterContextLines: List<String>): Result {
        //int maxText = 20;
        //int start = Math.max(lineStartOffset - maxText, 0);
        // int end = Math.min(lineStartOffset + maxText, line.length());
        val result = Result()
        result.file = file
        //не обзезать найденую строку
        result.line = line//.substring((int) start, (int) end);
        //  result.line = line.substring((int) start, (int) end);//строка будет обрезаться
        result.lineNumber = lineNumber
        result.startOffset = startOffset
        result.endOffset = endOffset
        result.lineStartOffset = lineStartOffset
        result.matchStart = matchStart
        result.matchEnd = matchEnd
        // if (result.matchEnd > end - start) {
        //    result.matchEnd = end - start;
        //  }
        return result
    }

    fun verifyFileList() {

        val list: MutableList<File> = ArrayList()

        for (f in filesToProcess!!) {

            if (f.exists()) {
                if (f.isFile) {
                    if (includeFile(f)) {
                        if (excludeFile(f)) {
                            list.add(f)
                        }
                    }
                } else if (f.isDirectory) {
                    val suffixs = mExtensions.toTypedArray()
                    val children = FileUtils.listFiles(f, suffixs, recurseDirectories)
                    list.addAll(children)
//                    if (recurseDirectories) {
//                        list.addAll(recurseDir(f));
//                    }
                }
            }
        }

        filesToProcess = list
    }

    private fun recurseDir(dir: File): List<File> {

        val files: MutableList<File> = ArrayList()

        for (f in dir.listFiles()!!) {

            if (f.isFile) {
                if (includeFile(f)) {
                    if (excludeFile(f)) {
                        files.add(f)
                    }
                }
            } else if (f.isDirectory) {
                if (!excludeDir(f)) {
                    files.addAll(recurseDir(f))
                }
            }

        }

        return files
    }

    fun readRegexFromFile(vararg fnames: String) {

    }

    fun readLongRegexFromFile(fnames: Array<String>) {


    }

    private fun reset() {

        grepPattern = null
        regex = null
        filesToProcess = null
        excludeFilePatterns.clear()

    }

    fun includeFile(f: File): Boolean {

        if (!useInclude) {
            return true
        }

//        if( CollectionUtils.exists( includeFilePatterns, wildcardMatcher( f ) ) ) {
//            return true;
//        }

        return false
    }

    fun excludeFile(f: File): Boolean {

        /*
        if( !useExclude ) {
           return false;
       }


         */
        val d = FileFilterCriteria()
        return d.accept(f)
        //if( ///CollectionUtils.exists( excludeFilePatterns, wildcardMatcher( f ) ) ) {
        //   return true;
        //   }

        // return IOUtils.isBinaryFile(f); //对中文有误杀
        // return false;
    }

    private fun excludeDir(f: File): Boolean {

        //  String dir = "original";
        // return DirectoryFileFilter.DIRECTORY.accept(f,dir );
        //   if( CollectionUtils.exists( excludeDirPatterns, wildcardMatcher( f ) ) ) {
        // return true;
        //  }
        return false
    }

    private fun printFileNamesOnly(): Boolean {

        return printFilesWithoutMatch || printFileNameOnly
    }

    private fun matchAny(line: CharSequence): Matcher? {
        val m = grepPattern!!.matcher(line)
        if (m.find()) {
            return m
        }

        return null
    }

    /**
     * Replaces every match. In plain-text mode the replacement is literal ('$' and '\\' are
     * common in smali). In regex mode an invalid group reference, which is an
     * IllegalArgumentException since targetSdk 34 (OpenJDK 17 regex), becomes an IOException.
     */
    @Throws(IOException::class)
    fun replaceAll(text: String?, replaceText: String?): String {
        compilePattern()
        val m = grepPattern!!.matcher(text!!)
        if (!isUseRegex) {
            return m.replaceAll(Matcher.quoteReplacement(replaceText!!))
        }
        try {
            return m.replaceAll(replaceText!!)
        } catch (e: IllegalArgumentException) {
            throw IOException(e.message, e)
        } catch (e: IndexOutOfBoundsException) {
            throw IOException(e.message, e)
        }
        /*ArrayList<Integer> array = new ArrayList<>();
        // 从头开始搜索获取所有位置
        while (m.find()) {
            array.add(m.start());
            array.add(m.end());
        }
        int size = array.size();
        if (size == 0) {
           // UIUtils.toast(text.getContext(), text.getContext().getResources().getQuantityString(R.plurals.x_text_replaced, 0));
            return;
        }
        int count = 0;
        for (int i = size - 2; i >= 0; i -= 2) {
            count++;
            text.(array.get(i), array.get(i + 1), replaceText);
        }*/
        //  UIUtils.toast(text.getContext(), text.getContext().getResources().getQuantityString(R.plurals.x_text_replaced, count, count));
    }

    private fun grepFile(file: File): List<Result>? {
        var lineNumber = 0
        var count = 0
        var byteOffset = 0
        val beforeContextLines: MutableList<String> = LinkedList()
        val afterContextLines: MutableList<String> = LinkedList()
        val results = ArrayList<Result>()
        var bfr: BufferedReader? = null
        try {
            val encoding = FileEncodingDetector.detectEncoding(file)
            bfr = BufferedReader(InputStreamReader(FileInputStream(file), encoding), 16000)
            var line = bfr.readLine()
            while (line != null) {

                lineNumber++
                val m = matchAny(line)

                if (m != null && !invertMatch) {
                    count++
                    if (afterContext > 0) {
                        afterContextLines.clear()
                        bfr.mark(4000)
                        for (i in 0 until afterContext) {
                            val s = bfr.readLine()
                            if (s != null) {
                                afterContextLines.add(s)
                            } else {
                                break
                            }
                        }
                        bfr.reset()
                    }
                    var match = line
                    if (printMatchOnly) {
                        match = m.group()
                    }
//                    printMatch(file, match, lineNumber, count, (byteOffset + m.start()), beforeContextLines,
                    results.add(printMatch(file, match!!, lineNumber, byteOffset + m.start(), byteOffset + m.end(), m.start(), m.start(), m.end(), beforeContextLines,
                            afterContextLines))
                    if (printFileNameOnly) {
                        return null
                    }
                    //
                } else if (m == null && invertMatch) {
                    count++
                    results.add(printMatch(file, line, lineNumber, byteOffset, byteOffset, byteOffset, byteOffset, byteOffset, beforeContextLines,
                            afterContextLines))
                    // TODO: this has a slightly different meaning
                    if (printFileNameOnly) {
                        return null
                    }
                }

                if (maxCount != 0 && count >= maxCount) {
                    break
                }

                byteOffset += line.length
                byteOffset += 1 // TODO: end of line char - what about DOS/Win32?

                beforeContextLines.add(line)
                if (beforeContextLines.size > beforeContext) {
                    beforeContextLines.removeAt(0)
                }
                line = bfr.readLine()
            }
        } catch (ioe: Exception) {
            DLog.e(ioe)
        } finally {
            if (bfr != null)
                try {
                    bfr.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }

        if (count == 0) {
            // no matches in file
            if (printFilesWithoutMatch) {
                printMessage(file.name)
            }
        }

        if (printCountOnly) {
            printMessage(file.name + ":" + count)
        }

        return results
    }

    @SuppressLint("StaticFieldLeak")
    fun grepText(direct: GrepDirect, line: CharSequence, start: Int, listener: TaskListener<MatcherResult>?) {
        object : JecAsyncTask<Int, Void, MatcherResult>() {

            @Throws(Exception::class)
            override fun onRun(taskResult: TaskResult<MatcherResult>, vararg params: Int?) {
                compilePattern()
                var results: MatcherResult? = null
                var index = params[0]!!
                val m = grepPattern!!.matcher(line)
                if (direct == GrepDirect.NEXT) {
                    while (true) {
                        if (m.find(index)) {
                            results = MatcherResult(m)
                            break
                        } else if (index > 0) {
                            index = 0
                        } else {
                            break
                        }
                    }
                } else {
                    if (index <= 0)
                        index = line.length

                    // 从头开始搜索获取所有位置
                    while (m.find()) {
                        if (m.end() >= index) {
                            break
                        }
                        results = MatcherResult(m)
                    }
                }
                taskResult.setResult(results)
            }
        }.setTaskListener(listener).execute(start)
    }

    fun setRegex(r: String?, useRegex: Boolean) {
        regex = if (!useRegex) escapeRegexChar(r!!) else r
        this.isUseRegex = useRegex
    }

    private fun compilePattern() {

//        int flags = Pattern.COMMENTS;
        var flags = 0

        var pattern = regex

        if (ignoreCase) {
            flags = flags or Pattern.CASE_INSENSITIVE
        }

        // we are ignoring line-regex if both are supplied
        if (wordRegex) {
            // poor mans way to do it
            pattern = "\\b" + pattern + "\\b"
        } else {
            if (lineRegex) {

                // poor mans way to do it
                pattern = "^" + pattern + "$"
            }
        }

        grepPattern = Pattern.compile(pattern, flags)

    }

    fun addFile(name: String?) {

        filesToProcess!!.add(File(name))
    }

    @SuppressLint("StaticFieldLeak")
    fun execute(listener: TaskListener<List<Result>>?) {
        object : JecAsyncTask<Void, Void, List<Result>>() {

            @Throws(Exception::class)
            override fun onRun(taskResult: TaskResult<List<Result>>, vararg params: Void?) {
                compilePattern()
                verifyFileList()
                val results = grepFiles()
                taskResult.setResult(results)
            }
        }.setTaskListener(listener).execute()
    }

    fun execute(): List<Result> {
        compilePattern()
        verifyFileList()
        return grepFiles()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(if (invertMatch) 1.toByte() else 0.toByte())
        dest.writeByte(if (ignoreCase) 1.toByte() else 0.toByte())
        dest.writeInt(this.maxCount)
        dest.writeByte(if (printFileNameOnly) 1.toByte() else 0.toByte())
        dest.writeByte(if (printByteOffset) 1.toByte() else 0.toByte())
        dest.writeByte(if (quiet) 1.toByte() else 0.toByte())
        dest.writeByte(if (printCountOnly) 1.toByte() else 0.toByte())
        dest.writeByte(if (printFilesWithoutMatch) 1.toByte() else 0.toByte())
        dest.writeByte(if (wordRegex) 1.toByte() else 0.toByte())
        dest.writeByte(if (lineRegex) 1.toByte() else 0.toByte())
        dest.writeByte(if (noMessages) 1.toByte() else 0.toByte())
        dest.writeByte(if (printFileName) 1.toByte() else 0.toByte())
        dest.writeByte(if (printMatchOnly) 1.toByte() else 0.toByte())
        dest.writeByte(if (printLineNumber) 1.toByte() else 0.toByte())
        dest.writeByte(if (recurseDirectories) 1.toByte() else 0.toByte())
        dest.writeByte(if (skipDirectories) 1.toByte() else 0.toByte())
        dest.writeStringList(this.excludeFilePatterns)
        dest.writeByte(if (useInclude) 1.toByte() else 0.toByte())
        dest.writeByte(if (useExclude) 1.toByte() else 0.toByte())
        dest.writeInt(this.beforeContext)
        dest.writeInt(this.afterContext)
        dest.writeString(this.regex)
        dest.writeList(this.filesToProcess)
    }

    enum class GrepDirect {
        PREV,
        NEXT,
    }

    fun interface OnSearchFinishListener {
        fun onFinish(results: List<Result>?)
    }

    class Result internal constructor() {
        @JvmField
        var file: File? = null
        @JvmField
        var line: String? = null
        @JvmField
        var lineNumber = 0
        @JvmField
        var lineStartOffset = 0
        @JvmField
        var startOffset = 0
        @JvmField
        var endOffset = 0
        @JvmField
        var matchStart = 0
        @JvmField
        var matchEnd = 0
    }

    internal inner class FileFilterCriteria : FileFilter {

        //     private final String[] fileExtension = new String[]{".xml", ".txt", ".json", ".smali"};

        override fun accept(file: File): Boolean {
            // System.out.println("File from directory:- " + file.getName());
            for (extension in mExtensions) {
                if (file.name.lowercase().endsWith(extension)) {
                    return true
                }
            }
            return false
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ExtGrep> = object : Parcelable.Creator<ExtGrep> {
            override fun createFromParcel(source: Parcel): ExtGrep {
                return ExtGrep(source)
            }

            override fun newArray(size: Int): Array<ExtGrep?> {
                return arrayOfNulls(size)
            }
        }

        @JvmStatic
        fun parseReplacement(m: MatcherResult, replaceText: String): String {
            var escape = false
            var dollar = false

            val buffer = StringBuilder()
            val length = replaceText.length
            for (i in 0 until length) {
                val c = replaceText[i]
                if (c == '\\' && !escape) {
                    escape = true
                } else if (c == '$' && !escape) {
                    dollar = true
                } else if (c >= '0' && c <= '9' && dollar) {
                    val group = c - '0'
                    if (group < m.groupCount())
                        buffer.append(m.group(group))
                    dollar = false
                } else if (c == 'r' && escape) {
                    buffer.append('\r')
                    escape = false
                } else if (c == 'n' && escape) {
                    buffer.append('\n')
                    escape = false
                } else if (c == 't' && escape) {
                    buffer.append('\t')
                    escape = false
                } else {
                    buffer.append(c)
                    dollar = false
                    escape = false
                }
            }

            // This seemingly stupid piece of code reproduces a JDK bug.
            if (escape) {
                throw ArrayIndexOutOfBoundsException(replaceText.length)
            }
            return buffer.toString()
        }

        private fun escapeRegexChar(pattern: String): String {
            val metachar = ".^$[]*+?|()\\{}"

            val newpat = StringBuilder()

            val len = pattern.length

            for (i in 0 until len) {
                val c = pattern[i]
                if (metachar.indexOf(c) >= 0) {
                    newpat.append('\\')
                }
                newpat.append(c)
            }
            return newpat.toString()
        }
    }
}
