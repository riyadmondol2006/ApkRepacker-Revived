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
import pxb.android.StringItem
import pxb.android.StringItems
import pxb.android.axml.Util
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TreeMap

/**
 * Write pkgs to an arsc file
 *
 * @see ArscParser
 * @author bob
 */
class ArscWriter(private val pkgs: List<Pkg>) : ResConst {
    private class PkgCtx {
        val keyNames: MutableMap<String?, StringItem> = HashMap()
        val keyNames0 = StringItems()
        var keyStringOff = 0
        var offset = 0
        var pkg: Pkg? = null
        var pkgSize = 0
        val typeNames: MutableList<StringItem?> = ArrayList()

        val typeNames0 = StringItems()
        var typeStringOff = 0

        fun addKeyName(name: String?) {
            if (keyNames.containsKey(name)) {
                return
            }
            val stringItem = StringItem(name)
            keyNames[name] = stringItem
            keyNames0.add(stringItem)
        }

        fun addTypeName(id: Int, name: String?) {
            while (typeNames.size <= id) {
                typeNames.add(null)
            }

            val item = typeNames[id]
            if (item == null) {
                typeNames[id] = StringItem(name)
            } else {
                throw RuntimeException()
            }
        }
    }

    private val ctxs: MutableList<PkgCtx> = ArrayList(5)
    private val strTable: MutableMap<String, StringItem> = TreeMap()
    private val strTable0 = StringItems()

    private fun addString(str: String) {
        if (strTable.containsKey(str)) {
            return
        }
        val stringItem = StringItem(str)
        strTable[str] = stringItem
        strTable0.add(stringItem)
    }

    private fun count(): Int {

        var size = 0

        size += 8 + 4 // chunk, pkgcount
        run {
            var stringSize = strTable0.byteSize()
            if (stringSize % 4 != 0) {
                stringSize += 4 - stringSize % 4
            }
            size += 8 + stringSize // global strings
        }
        for (ctx in ctxs) {
            ctx.offset = size
            var pkgSize = 0
            pkgSize += 8 + 4 + 256 // chunk,pid+name
            pkgSize += 4 * 4

            ctx.typeStringOff = pkgSize
            run {
                var stringSize = ctx.typeNames0.byteSize()
                if (stringSize % 4 != 0) {
                    stringSize += 4 - stringSize % 4
                }
                pkgSize += 8 + stringSize // type names
            }

            ctx.keyStringOff = pkgSize

            run {
                var stringSize = ctx.keyNames0.byteSize()
                if (stringSize % 4 != 0) {
                    stringSize += 4 - stringSize % 4
                }
                pkgSize += 8 + stringSize // key names
            }

            for (type in ctx.pkg!!.types.values) {
                type.wPosition = size + pkgSize
                pkgSize += 8 + 4 + 4 + 4 * type.specs!!.size // trunk,id,entryCount,
                // configs

                for (config in type.configs) {
                    config.wPosition = pkgSize + size
                    val configBasePostion = pkgSize
                    pkgSize += 8 + 4 + 4 + 4 // trunk,id,entryCount,entriesStart
                    var size0 = config.id.size
                    if (size0 % 4 != 0) {
                        size0 += 4 - size0 % 4
                    }
                    pkgSize += size0 // config

                    if (pkgSize - configBasePostion > 0x0038) {
                        throw RuntimeException("config id  too big")
                    } else {
                        pkgSize = configBasePostion + 0x0038
                    }

                    pkgSize += 4 * config.entryCount // offset
                    config.wEntryStart = pkgSize - configBasePostion
                    val entryBase = pkgSize
                    for (e in config.resources.values) {
                        e.wOffset = pkgSize - entryBase
                        pkgSize += 8 // size,flag,keyString
                        val value = e.value
                        if (value is BagValue) {
                            pkgSize += 8 + value.map.size * 12
                        } else {
                            pkgSize += 8
                        }
                    }
                    config.wChunkSize = pkgSize - configBasePostion
                }
            }
            ctx.pkgSize = pkgSize
            size += pkgSize
        }

        return size
    }

    @Throws(IOException::class)
    private fun prepare(): List<PkgCtx> {
        for (pkg in pkgs) {
            val ctx = PkgCtx()
            ctx.pkg = pkg
            ctxs.add(ctx)

            for (type in pkg.types.values) {
                ctx.addTypeName(type.id - 1, type.name)
                for (spec in type.specs!!) {
                    ctx.addKeyName(spec!!.name)
                }
                for (config in type.configs) {
                    for (e in config.resources.values) {
                        val obj = e.value
                        if (obj is BagValue) {
                            travelBagValue(obj)
                        } else {
                            travelValue(obj as Value)
                        }
                    }
                }
            }
            ctx.keyNames0.prepare()
            // typeNames may still hold null gaps, exactly as the Java code added them
            @Suppress("UNCHECKED_CAST")
            val typeNames = ctx.typeNames as List<StringItem>
            ctx.typeNames0.addAll(typeNames)
            ctx.typeNames0.prepare()
        }
        strTable0.prepare()
        return ctxs
    }

    @Throws(IOException::class)
    fun toByteArray(): ByteArray {
        prepare()
        val size = count()
        val out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        write(out, size)
        return out.array()
    }

    private fun travelBagValue(bag: BagValue) {
        for (e in bag.map) {
            travelValue(e.value)
        }
    }

    private fun travelValue(v: Value) {
        val raw = v.raw
        if (raw != null) {
            addString(raw)
        }
    }

    @Throws(IOException::class)
    private fun write(out: ByteBuffer, size: Int) {
        out.putInt(ResConst.RES_TABLE_TYPE or (0x000c shl 16))
        out.putInt(size)
        out.putInt(ctxs.size)

        run {
            val stringSize = strTable0.byteSize()
            var padding = 0
            if (stringSize % 4 != 0) {
                padding = 4 - stringSize % 4
            }
            out.putInt(ResConst.RES_STRING_POOL_TYPE or (0x001C shl 16))
            out.putInt(stringSize + padding + 8)
            strTable0.write(out)
            out.put(ByteArray(padding))
        }

        for (pctx in ctxs) {
            if (out.position() != pctx.offset) {
                throw RuntimeException()
            }
            val basePosition = out.position()
            val pkg = pctx.pkg!!
            out.putInt(ResConst.RES_TABLE_PACKAGE_TYPE or (0x011c shl 16))
            out.putInt(pctx.pkgSize)
            out.putInt(pkg.id)
            val p = out.position()
            out.put(pkg.name!!.toByteArray(Charsets.UTF_16LE))
            out.position(p + 256)

            out.putInt(pctx.typeStringOff)
            out.putInt(pctx.typeNames0.size)

            out.putInt(pctx.keyStringOff)
            out.putInt(pctx.keyNames0.size)

            run {
                if (out.position() - basePosition != pctx.typeStringOff) {
                    throw RuntimeException()
                }
                val stringSize = pctx.typeNames0.byteSize()
                var padding = 0
                if (stringSize % 4 != 0) {
                    padding = 4 - stringSize % 4
                }
                out.putInt(ResConst.RES_STRING_POOL_TYPE or (0x001C shl 16))
                out.putInt(stringSize + padding + 8)
                pctx.typeNames0.write(out)
                out.put(ByteArray(padding))
            }

            run {
                if (out.position() - basePosition != pctx.keyStringOff) {
                    throw RuntimeException()
                }
                val stringSize = pctx.keyNames0.byteSize()
                var padding = 0
                if (stringSize % 4 != 0) {
                    padding = 4 - stringSize % 4
                }
                out.putInt(ResConst.RES_STRING_POOL_TYPE or (0x001C shl 16))
                out.putInt(stringSize + padding + 8)
                pctx.keyNames0.write(out)
                out.put(ByteArray(padding))
            }

            for (t in pkg.types.values) {
                D("[%08x]write spec", out.position(), t.name)
                if (t.wPosition != out.position()) {
                    throw RuntimeException()
                }
                val specs = t.specs!!
                out.putInt(ResConst.RES_TABLE_TYPE_SPEC_TYPE or (0x0010 shl 16))
                out.putInt(4 * 4 + 4 * specs.size) // size

                out.putInt(t.id)
                out.putInt(specs.size)
                for (spec in specs) {
                    out.putInt(spec!!.flags)
                }

                for (config in t.configs) {
                    D("[%08x]write config", out.position())
                    val typeConfigPosition = out.position()
                    if (config.wPosition != typeConfigPosition) {
                        throw RuntimeException()
                    }
                    out.putInt(ResConst.RES_TABLE_TYPE_TYPE or (0x0038 shl 16))
                    out.putInt(config.wChunkSize) // size

                    out.putInt(t.id)
                    out.putInt(specs.size)
                    out.putInt(config.wEntryStart)

                    D("[%08x]write config ids", out.position())
                    out.put(config.id)

                    val size0 = config.id.size
                    var padding = 0
                    if (size0 % 4 != 0) {
                        padding = 4 - size0 % 4
                    }
                    out.put(ByteArray(padding))

                    out.position(typeConfigPosition + 0x0038)

                    D("[%08x]write config entry offsets", out.position())
                    for (i in 0 until config.entryCount) {
                        val entry = config.resources[i]
                        if (entry == null) {
                            out.putInt(-1)
                        } else {
                            out.putInt(entry.wOffset)
                        }
                    }

                    if (out.position() - typeConfigPosition != config.wEntryStart) {
                        throw RuntimeException()
                    }
                    D("[%08x]write config entrys", out.position())
                    for (e in config.resources.values) {
                        D("[%08x]ResTable_entry", out.position())
                        val value = e.value
                        val isBag = value is BagValue
                        out.putShort((if (isBag) 16 else 8).toShort())
                        var flag = e.flag
                        if (isBag) { // add complex flag
                            flag = flag or ArscParser.ENTRY_FLAG_COMPLEX.toInt()
                        } else { // remove
                            flag = flag and ArscParser.ENTRY_FLAG_COMPLEX.toInt().inv()
                        }
                        out.putShort(flag.toShort())
                        out.putInt(pctx.keyNames[e.spec.name]!!.index)
                        if (value is BagValue) {
                            out.putInt(value.parent)
                            out.putInt(value.map.size)
                            for (entry in value.map) {
                                out.putInt(entry.key)
                                writeValue(entry.value, out)
                            }
                        } else {
                            writeValue(value as Value, out)
                        }
                    }
                }
            }
        }
    }

    private fun writeValue(value: Value, out: ByteBuffer) {
        out.putShort(8.toShort())
        out.put(0.toByte())
        out.put(value.type.toByte())
        if (value.type == ArscParser.TYPE_STRING) {
            out.putInt(strTable[value.raw!!]!!.index)
        } else {
            out.putInt(value.data)
        }
    }

    companion object {
        private fun D(fmt: String, vararg args: Any?) {
        }

        @JvmStatic
        @Throws(IOException::class)
        fun main(vararg args: String) {
            if (args.size < 2) {
                System.err.println("asrc-write-test in.arsc out.arsc")
                return
            }
            val data = Util.readFile(File(args[0]))
            val pkgs = ArscParser(data).parse()
            // ArscDumper.dump(pkgs);
            val data2 = ArscWriter(pkgs).toByteArray()
            // ArscDumper.dump(new ArscParser(data2).parse());
            Util.writeFile(data2, File(args[1]))
        }
    }
}
