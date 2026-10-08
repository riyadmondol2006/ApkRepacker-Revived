/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.utils

import android.content.Context
import com.jecelyin.common.utils.DLog
import com.jecelyin.common.utils.SysUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
object BundlesUtils {
    @JvmStatic
    fun getBundlesDir(context: Context): File {
        return File(SysUtils.getCacheDir(context), "Bundles")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun unzipBundles(context: Context) {
        val cacheDir = SysUtils.getCacheDir(context)
        val okFile = File(cacheDir, ".bundles_unzip_ok")
        if (DLog.DEBUG && okFile.isFile)
            return
        val assetManager = context.assets
        var reader: BufferedReader? = null
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            reader = BufferedReader(InputStreamReader(assetManager.open("assets.index")))

            // do reading, usually loop until end of file reading
            var mLine: String?
            while (reader.readLine().also { mLine = it } != null) {
                //process line
                if (!mLine!!.startsWith("Bundles/"))
                    continue

                try {
                    `in` = assetManager.open(mLine!!)
                    val outFile = File(cacheDir, mLine!!)
                    val path = outFile.parentFile
                    if (!path!!.isDirectory && !path.mkdirs()) {
                        DLog.e("can't create dir: " + path.path)
                        continue
                    }
                    out = FileOutputStream(outFile)
                    copyFile(`in`, out)
                } catch (e: IOException) {
                    throw e
                } finally {
                    if (`in` != null) {
                        try {
                            `in`.close()
                        } catch (e: IOException) {
                            // NOOP
                        }
                    }
                    if (out != null) {
                        try {
                            out.close()
                        } catch (e: IOException) {
                            // NOOP
                        }
                    }
                }
            }
            okFile.createNewFile()
        } catch (e: IOException) {
            throw e
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    //log the exception
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(12024)
        var read: Int
        while (`in`.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
    }
}
