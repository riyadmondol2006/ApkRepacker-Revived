package com.riyadm.patchengine.rules

import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import dalvik.system.DexClassLoader
import org.apache.commons.io.IOUtils
import org.apache.commons.io.IOUtils.closeQuietly
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.util.zip.ZipFile

class PatchRuleExecDex : PatchRule() {

    private var entranceFunc: String? = null
    private var ifVersion = 1
    private val keywords: MutableList<String> = ArrayList()
    private var mainClass: String? = null
    private var param: String? = null
    private var scriptName: String? = null
    private var smaliNeeded = false

    init {
        keywords.add(SCRIPT)
        keywords.add(INTERFACE_VERSION)
        keywords.add(SMALI_NEEDED)
        keywords.add(MAIN_CLASS)
        keywords.add(ENTRANCE)
        keywords.add(PARAM)
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
                        SCRIPT -> scriptName = br.readLine()!!.trim { it <= ' ' }
                        MAIN_CLASS -> mainClass = br.readLine()!!.trim { it <= ' ' }
                        ENTRANCE -> entranceFunc = br.readLine()!!.trim { it <= ' ' }
                        SMALI_NEEDED -> smaliNeeded = java.lang.Boolean.parseBoolean(br.readLine()!!.trim { it <= ' ' })
                        INTERFACE_VERSION -> ifVersion = Integer.parseInt(br.readLine()!!.trim { it <= ' ' })
                        else -> {
                            if (PARAM == line) {
                                val lines: MutableList<String> = ArrayList()
                                line = readMultiLines(br, lines, true, keywords)
                                val sb = StringBuilder()
                                for (i in lines.indices) {
                                    sb.append(lines[i])
                                    if (i != lines.size - 1) {
                                        // NOTE: kept from the Java original: appends the int 10 (text "10"), not '\n'
                                        sb.append(10)
                                    }
                                }
                                param = sb.toString()
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
        val activity = projectHelper
        val patchZip = zipFile
        val logger = iPatchContext
        if (ifVersion != 1) {
            logger.error(R.string.general_error, "Unsupported interface version: $ifVersion")
            return null
        }
        val optimizedDexOutputPath = activity.getCacheDir()

        val ze = patchZip.getEntry(scriptName)
        if (ze == null) {
            logger.error(R.string.general_error, "Cannot find '$scriptName' inside the patch.")
            return null
        }
        var input: InputStream? = null
        var os: OutputStream? = null
        try {
            val dexFile = File(optimizedDexOutputPath, "script.dex")
            // The previous run left a read-only copy behind: replace it.
            if (dexFile.exists() && !dexFile.delete()) {
                throw IOException("Cannot delete old $dexFile")
            }
            val dexPath = dexFile.absolutePath
            os = BufferedOutputStream(FileOutputStream(dexPath))
            input = BufferedInputStream(patchZip.getInputStream(ze))
            IOUtils.copy(input, os)
            closeQuietly(input)
            closeQuietly(os)
            // Android 14+ (targetSdk 34) refuses to load dex files that are still writable.
            if (!dexFile.setReadOnly()) {
                throw IOException("Cannot make $dexFile read-only")
            }
            try {
                logger.info("Executing dex..", true)
                val entryClass = DexClassLoader(
                    dexPath,
                    optimizedDexOutputPath!!.absolutePath,
                    null,
                    activity.getContext()!!.classLoader
                ).loadClass(mainClass)
                entryClass.getMethod(
                    entranceFunc,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java
                ).invoke(entryClass.newInstance(), activity.getApkPath(), patchZip.name, activity.getProjectPath(), param)
                return null
            } catch (th: Throwable) {
                if (th is InvocationTargetException) {
                    val t = th.targetException
                    if (t != null) {
                        logger.error(R.string.general_error, getStackTrace(t))
                        return null
                    }
                    logger.error(R.string.general_error, getStackTrace(th))
                    return null
                }
                logger.error(R.string.general_error, getStackTrace(th))
                return null
            }
        } catch (e: Exception) {
            logger.error(R.string.general_error, "Cannot extract '$scriptName' to SD card.")
            closeQuietly(input)
            closeQuietly(os)
            return null
        } catch (th2: Throwable) {
            closeQuietly(input)
            closeQuietly(os)
            throw th2
        }
    }

    private fun getStackTrace(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        val logger = iPatchContext
        if (scriptName == null) {
            logger.error(R.string.patch_error_no_script_name)
            return false
        } else if (mainClass == null) {
            logger.error(R.string.patch_error_no_main_class)
            return false
        } else if (entranceFunc != null) {
            return true
        } else {
            logger.error(R.string.patch_error_no_entrance_func)
            return false
        }
    }

    override fun isSmaliNeeded(): Boolean {
        return smaliNeeded
    }

    companion object {
        private const val ENTRANCE = "ENTRANCE:"
        private const val INTERFACE_VERSION = "INTERFACE_VERSION:"
        private const val MAIN_CLASS = "MAIN_CLASS:"
        private const val PARAM = "PARAM:"
        private const val SCRIPT = "SCRIPT:"
        private const val SMALI_NEEDED = "SMALI_NEEDED:"
        private const val strEnd = "[/EXECUTE_DEX]"
    }
}
