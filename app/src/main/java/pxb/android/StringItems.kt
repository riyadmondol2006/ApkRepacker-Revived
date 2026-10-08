/*
 * Copyright (c) 2009-2013 Panxiaobo
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pxb.android

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class StringItems : ArrayList<StringItem>() {

    internal var stringData: ByteArray? = null

    private var useUTF8 = true

    /** Size in bytes of the serialized string pool (named to avoid clashing with List.size). */
    fun byteSize(): Int {
        return 5 * 4 + this.size * 4 + stringData!!.size + 0 // TODO
    }

    @Throws(IOException::class)
    fun prepare() {
        for (s in this) {
            if (s.data!!.length > 0x7FFF) {
                useUTF8 = false
            }
        }
        val baos = ByteArrayOutputStream()
        var i = 0
        var offset = 0
        baos.reset()
        val map: MutableMap<String?, Int> = HashMap()
        for (item in this) {
            item.index = i++
            val stringData = item.data
            val of = map[stringData]
            if (of != null) {
                item.dataOffset = of
            } else {
                item.dataOffset = offset
                map[stringData] = offset
                if (useUTF8) {
                    val length = stringData!!.length
                    val data = stringData.toByteArray(Charsets.UTF_8)
                    val u8lenght = data.size

                    if (length > 0x7F) {
                        offset++
                        baos.write((length shr 8) or 0x80)
                    }
                    baos.write(length)

                    if (u8lenght > 0x7F) {
                        offset++
                        baos.write((u8lenght shr 8) or 0x80)
                    }
                    baos.write(u8lenght)
                    baos.write(data)
                    baos.write(0)
                    offset += 3 + u8lenght
                } else {
                    val length = stringData!!.length
                    val data = stringData.toByteArray(Charsets.UTF_16LE)
                    if (length > 0x7FFF) {
                        val x = (length shr 16) or 0x8000
                        baos.write(x)
                        baos.write(x shr 8)
                        offset += 2
                    }
                    baos.write(length)
                    baos.write(length shr 8)
                    baos.write(data)
                    baos.write(0)
                    baos.write(0)
                    offset += 4 + data.size
                }
            }
        }
        // TODO
        stringData = baos.toByteArray()
    }

    @Throws(IOException::class)
    fun write(out: ByteBuffer) {
        out.putInt(this.size)
        out.putInt(0) // TODO style count
        out.putInt(if (useUTF8) UTF8_FLAG else 0)
        out.putInt(7 * 4 + this.size * 4)
        out.putInt(0)
        for (item in this) {
            out.putInt(item.dataOffset)
        }
        out.put(stringData!!)
        // TODO
    }

    companion object {
        private const val UTF8_FLAG = 0x00000100

        @JvmStatic
        @Throws(IOException::class)
        fun read(input: ByteBuffer): Array<String?> {
            val trunkOffset = input.position() - 8
            val stringCount = input.int
            val styleOffsetCount = input.int
            val flags = input.int
            val stringDataOffset = input.int
            val stylesOffset = input.int
            val offsets = IntArray(stringCount)
            val strings = arrayOfNulls<String>(stringCount)
            for (i in 0 until stringCount) {
                offsets[i] = input.int
            }

            val base = trunkOffset + stringDataOffset
            for (i in offsets.indices) {
                input.position(base + offsets[i])
                val s: String

                if (0 != (flags and UTF8_FLAG)) {
                    u8length(input) // ignored
                    val u8len = u8length(input)
                    val start = input.position()
                    var blength = u8len
                    while (input.get(start + blength).toInt() != 0) {
                        blength++
                    }
                    s = String(input.array(), start, blength, Charsets.UTF_8)
                } else {
                    val length = u16length(input)
                    s = String(input.array(), input.position(), length * 2, Charsets.UTF_16LE)
                }
                strings[i] = s
            }
            return strings
        }

        @JvmStatic
        internal fun u16length(input: ByteBuffer): Int {
            var length = input.short.toInt() and 0xFFFF
            if (length > 0x7FFF) {
                length = ((length and 0x7FFF) shl 8) or (input.short.toInt() and 0xFFFF)
            }
            return length
        }

        @JvmStatic
        internal fun u8length(input: ByteBuffer): Int {
            var len = input.get().toInt() and 0xFF
            if ((len and 0x80) != 0) {
                len = ((len and 0x7F) shl 8) or (input.get().toInt() and 0xFF)
            }
            return len
        }
    }
}
