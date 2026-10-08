/*
 * Copyright (c) 2009-2013 Panxiaobo
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
package pxb.android.axml

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

object Util {
    @JvmStatic
    @Throws(IOException::class)
    fun readFile(file: File): ByteArray {
        val input: InputStream = FileInputStream(file)
        val xml = ByteArray(input.available())
        input.read(xml)
        input.close()
        return xml
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readIs(input: InputStream): ByteArray {
        val os = ByteArrayOutputStream()
        copy(input, os)
        return os.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeFile(data: ByteArray, out: File) {
        val fos = FileOutputStream(out)
        fos.write(data)
        fos.close()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readProguardConfig(config: File): Map<String, String> {
        val clzMap: MutableMap<String, String> = HashMap()
        val r = BufferedReader(InputStreamReader(FileInputStream(config), "utf8"))
        try {
            var ln = r.readLine()
            while (ln != null) {
                if (ln.startsWith("#") || ln.startsWith(" ")) {
                    ln = r.readLine()
                    continue
                }
                // format a.pt.Main -> a.a.a:
                val i = ln.indexOf("->")
                if (i > 0) {
                    clzMap[ln.substring(0, i).trim { it <= ' ' }] =
                        ln.substring(i + 2, ln.length - 1).trim { it <= ' ' }
                }
                ln = r.readLine()
            }
        } finally {
            r.close()
        }
        return clzMap
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(input: InputStream, os: OutputStream) {
        val xml = ByteArray(10 * 1024)
        var c = input.read(xml)
        while (c > 0) {
            os.write(xml, 0, c)
            c = input.read(xml)
        }
    }
}
