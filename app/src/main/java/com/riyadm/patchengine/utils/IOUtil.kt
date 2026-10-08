package com.riyadm.patchengine.utils

import android.os.Environment
import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

object IOUtil {

    @JvmStatic
    @Throws(IOException::class)
    fun copy(input: InputStream, out: OutputStream) {
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read != -1) {
                out.write(buffer, 0, read)
            } else {
                return
            }
        }
    }

    @JvmStatic
    fun copy(targetDir: File?, srcDir: File) {
        val files = srcDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isFile) {
                    var fis: FileInputStream? = null
                    var fos: FileOutputStream? = null
                    try {
                        fis = FileInputStream(file)
                        fos = FileOutputStream(File(targetDir, file.name))
                        copy(fis, fos)
                    } catch (ignored: Exception) {
                    } catch (th: Throwable) {
                        closeQuietly(fis)
                        closeQuietly(fos)
                        throw th
                    }
                    closeQuietly(fis)
                    closeQuietly(fos)
                } else if (file.isDirectory) {
                    val subDir = File(targetDir, file.name)
                    subDir.mkdir()
                    copy(subDir, file)
                }
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun toByteArray(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        copy(input, output)
        return output.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeZero(out: OutputStream, size: Int) {
        val blocks = size / 1024
        val remain = size % 1024
        val buffer = ByteArray(1024)
        for (i in 0 until 1024) {
            buffer[i] = 0
        }
        for (i2 in 0 until blocks) {
            out.write(buffer)
        }
        if (remain > 0) {
            out.write(buffer, 0, remain)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFully(input: InputStream, buf: ByteArray) {
        var read = 0
        while (read < buf.size) {
            val ret = input.read(buf, read, buf.size - read)
            if (ret == -1) {
                break
            }
            read += ret
        }
    }

    @JvmStatic
    fun writeObjectToFile(filePath: String, obj: Any?): Boolean {
        var objOut: ObjectOutputStream? = null
        try {
            objOut = ObjectOutputStream(FileOutputStream(File(filePath)))
            objOut.writeObject(obj)
            objOut.flush()
            closeQuietly(objOut)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            closeQuietly(objOut)
            return false
        } catch (th: Throwable) {
            closeQuietly(objOut)
            throw th
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readString(input: InputStream): String {
        val sb = StringBuilder()
        val br = BufferedReader(InputStreamReader(input))
        var line = br.readLine()
        while (line != null) {
            sb.append(line)
            sb.append("\n")
            line = br.readLine()
        }
        return sb.toString()
    }

    @JvmStatic
    fun closeQuietly(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun closeQuietly(zfile: ZipFile?) {
        if (zfile != null) {
            try {
                zfile.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeToFile(targetFile: String, content: String) {
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(targetFile)
            fos.write(content.toByteArray(Charset.defaultCharset()))
        } finally {
            closeQuietly(fos)
        }
    }

    @JvmStatic
    fun exist(): Boolean {
        if (Environment.getExternalStorageState().equals("mounted")) {
            return true
        }
        return false
    }

    @JvmStatic
    fun getRootDirectory(): String {
        return Environment.getExternalStorageDirectory().path
    }

    @JvmStatic
    @Throws(Exception::class)
    fun makeDir(dirName: String): String {
        if (exist()) {
            var subDir = ""
            //String packagePath = ctx.getPackageName();
            subDir = "/ApkRepacker/$dirName/"
            val targetDir = getRootDirectory() + subDir
            val f = File(targetDir)
            if (!f.exists()) {
                f.mkdirs()
            }
            return targetDir
        }
        throw Exception("Can not find sd card.")
    }

    @JvmStatic
    @Throws(Exception::class)
    fun makeDir(dirPath: String?, folderName: String) {
        val f = File(dirPath, folderName)
        if (f.exists()) {
            throwExistException(folderName)
        }
        f.mkdir()
    }

    @Throws(Exception::class)
    private fun throwExistException(filename: String) {
        throw Exception(Log.e("IOUtils", String.format("Folder %s exits", filename)).toString())
    }
}
