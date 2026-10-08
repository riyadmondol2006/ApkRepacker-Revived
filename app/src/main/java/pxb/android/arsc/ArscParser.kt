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
package pxb.android.arsc

import pxb.android.ResConst
import pxb.android.StringItems
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.AbstractMap

/**
 * Read the resources.arsc inside an Android apk.
 *
 * Usage:
 *
 * ```
 * byte[] oldArscFile= ... ; //
 * List<Pkg> pkgs = new ArscParser(oldArscFile).parse(); // read the file
 * modify(pkgs); // do what you want here
 * byte[] newArscFile = new ArscWriter(pkgs).toByteArray(); // build a new file
 * ```
 *
 * The format of arsc is described here (gingerbread)
 * - frameworks/base/libs/utils/ResourceTypes.cpp
 * - frameworks/base/include/utils/ResourceTypes.h
 *
 * and the cmd line `aapt d resources abc.apk` is also good for debug
 * (available in android sdk)
 *
 * Todos:
 * - TODO add support to read styled strings
 *
 * Thanks to the the following projects
 * - android4me https://code.google.com/p/android4me/
 * - Apktool https://code.google.com/p/android-apktool
 * - Android http://source.android.com/
 *
 * @author bob
 */
class ArscParser(b: ByteArray) : ResConst {
    /* pkg */
    internal inner class Chunk {

        @JvmField
        val headSize: Int

        @JvmField
        val location: Int

        @JvmField
        val size: Int

        @JvmField
        val type: Int

        init {
            location = input.position()
            type = input.short.toInt() and 0xFFFF
            headSize = input.short.toInt() and 0xFFFF
            size = input.int
            D("[%08x]type: %04x, headsize: %04x, size:%08x", location, type, headSize, size)
        }
    }

    private var fileSize = -1
    private val input: ByteBuffer = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
    private var keyNamesX: Array<String?>? = null
    private var pkg: Pkg? = null
    private val pkgs: MutableList<Pkg> = ArrayList()
    private var strings: Array<String?>? = null
    private var typeNamesX: Array<String?>? = null

    @Throws(IOException::class)
    fun parse(): MutableList<Pkg> {
        if (fileSize < 0) {
            val head = Chunk()
            if (head.type != ResConst.RES_TABLE_TYPE) {
                throw RuntimeException()
            }
            fileSize = head.size
            input.int // packagecount
        }
        while (input.hasRemaining()) {
            val chunk = Chunk()
            when (chunk.type) {
                ResConst.RES_STRING_POOL_TYPE -> {
                    val strings = StringItems.read(input)
                    this.strings = strings
                    if (DEBUG) {
                        for (i in strings.indices) {
                            D("STR [%08x] %s", i, strings[i])
                        }
                    }
                }
                ResConst.RES_TABLE_PACKAGE_TYPE -> readPackage(input)
            }
            input.position(chunk.location + chunk.size)
        }
        return pkgs
    }

    // private void readConfigFlags() {
    // int size = in.getInt();
    // if (size < 28) {
    // throw new RuntimeException();
    // }
    // short mcc = in.getShort();
    // short mnc = in.getShort();
    //
    // char[] language = new char[] { (char) in.get(), (char) in.get() };
    // char[] country = new char[] { (char) in.get(), (char) in.get() };
    //
    // byte orientation = in.get();
    // byte touchscreen = in.get();
    // short density = in.getShort();
    //
    // byte keyboard = in.get();
    // byte navigation = in.get();
    // byte inputFlags = in.get();
    // byte inputPad0 = in.get();
    //
    // short screenWidth = in.getShort();
    // short screenHeight = in.getShort();
    //
    // short sdkVersion = in.getShort();
    // short minorVersion = in.getShort();
    //
    // byte screenLayout = 0;
    // byte uiMode = 0;
    // short smallestScreenWidthDp = 0;
    // if (size >= 32) {
    // screenLayout = in.get();
    // uiMode = in.get();
    // smallestScreenWidthDp = in.getShort();
    // }
    //
    // short screenWidthDp = 0;
    // short screenHeightDp = 0;
    //
    // if (size >= 36) {
    // screenWidthDp = in.getShort();
    // screenHeightDp = in.getShort();
    // }
    //
    // short layoutDirection = 0;
    // if (size >= 38 && sdkVersion >= 17) {
    // layoutDirection = in.getShort();
    // }
    //
    // }

    private fun readEntry(config: Config, spec: ResSpec) {
        D("[%08x]read ResTable_entry", input.position())
        val size = input.short.toInt()
        D("ResTable_entry %d", size)

        val flags = input.short.toInt() // ENTRY_FLAG_PUBLIC
        val keyStr = input.int
        spec.updateName(keyNamesX!![keyStr])

        val resEntry = ResEntry(flags, spec)

        if (0 != (flags and ENTRY_FLAG_COMPLEX.toInt())) {

            val parent = input.int
            val count = input.int
            val bag = BagValue(parent)
            for (i in 0 until count) {
                val entry: Map.Entry<Int, Value> = AbstractMap.SimpleEntry(input.int, readValue())
                bag.map.add(entry)
            }
            resEntry.value = bag
        } else {
            resEntry.value = readValue()
        }
        config.resources[spec.id] = resEntry
    }

    @Throws(IOException::class)
    private fun readPackage(input: ByteBuffer) {
        val pid = input.int % 0xFF

        val name: String
        run {
            val nextPisition = input.position() + 128 * 2
            val sb = StringBuilder(32)
            for (i in 0 until 128) {
                val s = input.short.toInt()
                if (s == 0) {
                    break
                } else {
                    sb.append(s.toChar())
                }
            }
            name = sb.toString()
            input.position(nextPisition)
        }

        val pkg = Pkg(pid, name)
        this.pkg = pkg
        pkgs.add(pkg)

        val typeStringOff = input.int
        val typeNameCount = input.int
        val keyStringOff = input.int
        val specNameCount = input.int

        run {
            val chunk = Chunk()
            if (chunk.type != ResConst.RES_STRING_POOL_TYPE) {
                throw RuntimeException()
            }
            typeNamesX = StringItems.read(input)
            input.position(chunk.location + chunk.size)
        }
        run {
            val chunk = Chunk()
            if (chunk.type != ResConst.RES_STRING_POOL_TYPE) {
                throw RuntimeException()
            }
            val keyNamesX = StringItems.read(input)
            this.keyNamesX = keyNamesX
            if (DEBUG) {
                for (i in keyNamesX.indices) {
                    D("STR [%08x] %s", i, keyNamesX[i])
                }
            }
            input.position(chunk.location + chunk.size)
        }

        out@ while (input.hasRemaining()) {
            val chunk = Chunk()
            when (chunk.type) {
                ResConst.RES_TABLE_TYPE_SPEC_TYPE -> {
                    D("[%08x]read spec", input.position() - 8)
                    val tid = input.get().toInt() and 0xFF
                    input.get() // res0
                    input.short // res1
                    val entryCount = input.int

                    val t = pkg.getType(tid, typeNamesX!![tid - 1], entryCount)
                    for (i in 0 until entryCount) {
                        t.getSpec(i).flags = input.int
                    }
                }
                ResConst.RES_TABLE_TYPE_TYPE -> {
                    D("[%08x]read config", input.position() - 8)
                    val tid = input.get().toInt() and 0xFF
                    input.get() // res0
                    input.short // res1
                    val entryCount = input.int
                    val t = pkg.getType(tid, typeNamesX!![tid - 1], entryCount)
                    val entriesStart = input.int

                    D("[%08x]read config id", input.position())

                    val p = input.position()
                    val size = input.int
                    // readConfigFlags();
                    val data = ByteArray(size)
                    input.position(p)
                    input.get(data)
                    val config = Config(data, entryCount)

                    input.position(chunk.location + chunk.headSize)

                    D("[%08x]read config entry offset", input.position())

                    val entrys = IntArray(entryCount)
                    for (i in 0 until entryCount) {
                        entrys[i] = input.int
                    }
                    D("[%08x]read config entrys", input.position())
                    for (i in entrys.indices) {
                        if (entrys[i] != -1) {
                            input.position(chunk.location + entriesStart + entrys[i])
                            val spec = t.getSpec(i)
                            readEntry(config, spec)
                        }
                    }

                    t.addConfig(config)
                }
                else -> break@out
            }
            input.position(chunk.location + chunk.size)
        }
    }

    private fun readValue(): Value {
        val size1 = input.short.toInt() // 8
        val zero = input.get().toInt() // 0
        val type = input.get().toInt() and 0xFF // TypedValue.*
        val data = input.int
        var raw: String? = null
        if (type == TYPE_STRING) {
            raw = strings!![data]
        }
        return Value(type, data, raw)
    }

    companion object {
        private const val DEBUG = false

        private fun D(fmt: String, vararg args: Any?) {
            if (DEBUG) {
                println(String.format(fmt, *args))
            }
        }

        /**
         * If set, this resource has been declared public, so libraries are allowed
         * to reference it.
         */
        internal const val ENGRY_FLAG_PUBLIC = 0x0002

        /**
         * If set, this is a complex entry, holding a set of name/value mappings. It
         * is followed by an array of ResTable_map structures.
         */
        internal const val ENTRY_FLAG_COMPLEX: Short = 0x0001
        const val TYPE_STRING = 0x03
    }
}
