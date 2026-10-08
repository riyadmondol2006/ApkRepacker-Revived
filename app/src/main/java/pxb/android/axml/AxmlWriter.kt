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

import pxb.android.ResConst
import pxb.android.StringItem
import pxb.android.StringItems
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Stack
import java.util.TreeSet

/**
 * a class to write android axml
 *
 * @author [Panxiaobo](mailto:pxb1988@gmail.com)
 */
open class AxmlWriter : AxmlVisitor() {
    @JvmField
    protected var firsts: MutableList<NodeImpl> = ArrayList(3)
    private val nses: MutableMap<String?, Ns?> = HashMap()
    private var otherString: MutableList<StringItem>? = ArrayList()
    private val resourceId2Str: MutableMap<String, StringItem> = HashMap()
    private val resourceIds: MutableList<Int> = ArrayList()
    private var resourceString: MutableList<StringItem>? = ArrayList()
    private val stringItems = StringItems()

    override fun child(ns: String?, name: String?): NodeVisitor? {
        val first = NodeImpl(ns, name)
        this.firsts.add(first)
        return first
    }

    override fun end() {
    }

    override fun ns(prefix: String?, uri: String?, ln: Int) {
        nses[uri] = Ns(if (prefix == null) null else StringItem(prefix), StringItem(uri), ln)
    }

    // TODO add style support
    // private List<StringItem> styleItems = new ArrayList();

    @Throws(IOException::class)
    private fun prepare(): Int {
        var size = 0

        for (first in firsts) {
            size += first.prepare(this)
        }
        run {
            var a = 0
            for (e in nses.entries) {
                var ns = e.value
                if (ns == null) {
                    ns = Ns(null, StringItem(e.key), 0)
                    e.setValue(ns)
                }
                if (ns.prefix == null) {
                    ns.prefix = StringItem(String.format("axml_auto_%02d", a++))
                }
                ns.prefix = update(ns.prefix)
                ns.uri = update(ns.uri)
            }
        }

        size += nses.size * 24 * 2

        this.stringItems.addAll(resourceString!!)
        resourceString = null
        this.stringItems.addAll(otherString!!)
        otherString = null
        this.stringItems.prepare()
        var stringSize = this.stringItems.byteSize()
        if (stringSize % 4 != 0) {
            stringSize += 4 - stringSize % 4
        }
        size += 8 + stringSize
        size += 8 + resourceIds.size * 4
        return size
    }

    @Throws(IOException::class)
    fun toByteArray(): ByteArray {

        val size = 8 + prepare()
        val out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        out.putInt(ResConst.RES_XML_TYPE or (0x0008 shl 16))
        out.putInt(size)

        val stringSize = this.stringItems.byteSize()
        var padding = 0
        if (stringSize % 4 != 0) {
            padding = 4 - stringSize % 4
        }
        out.putInt(ResConst.RES_STRING_POOL_TYPE or (0x001C shl 16))
        out.putInt(stringSize + padding + 8)
        this.stringItems.write(out)
        out.put(ByteArray(padding))

        out.putInt(ResConst.RES_XML_RESOURCE_MAP_TYPE or (0x0008 shl 16))
        out.putInt(8 + this.resourceIds.size * 4)
        for (i in resourceIds) {
            out.putInt(i)
        }

        val stack = Stack<Ns>()
        for (e in this.nses.entries) {
            val ns = e.value!!
            stack.push(ns)
            out.putInt(ResConst.RES_XML_START_NAMESPACE_TYPE or (0x0010 shl 16))
            out.putInt(24)
            out.putInt(-1)
            out.putInt(-1) // 0xFFFFFFFF
            out.putInt(ns.prefix!!.index)
            out.putInt(ns.uri!!.index)
        }

        for (first in firsts) {
            first.write(out)
        }

        while (stack.size > 0) {
            val ns = stack.pop()
            out.putInt(ResConst.RES_XML_END_NAMESPACE_TYPE or (0x0010 shl 16))
            out.putInt(24)
            out.putInt(ns.ln)
            out.putInt(-1) // 0xFFFFFFFF
            out.putInt(ns.prefix!!.index)
            out.putInt(ns.uri!!.index)
        }
        return out.array()
    }

    internal fun update(item: StringItem?): StringItem? {
        if (item == null)
            return null
        val otherString = this.otherString!!
        val i = otherString.indexOf(item)
        if (i < 0) {
            val copy = StringItem(item.data)
            otherString.add(copy)
            return copy
        } else {
            return otherString[i]
        }
    }

    internal fun updateNs(item: StringItem?): StringItem? {
        if (item == null) {
            return null
        }
        val ns = item.data
        if (!this.nses.containsKey(ns)) {
            this.nses[ns] = null
        }
        return update(item)
    }

    internal fun updateWithResourceId(name: StringItem, resourceId: Int): StringItem {
        val key = name.data + resourceId
        val item = this.resourceId2Str[key]
        if (item != null) {
            return item
        } else {
            val copy = StringItem(name.data)
            resourceIds.add(resourceId)
            resourceString!!.add(copy)
            resourceId2Str[key] = copy
            return copy
        }
    }

    class Attr(
        @JvmField var ns: StringItem?,
        @JvmField var name: StringItem?,
        @JvmField var resourceId: Int
    ) {

        @JvmField
        var index: Int = 0

        @JvmField
        var type: Int = 0

        @JvmField
        var value: Any? = null

        @JvmField
        var raw: StringItem? = null

        fun prepare(axmlWriter: AxmlWriter) {
            ns = axmlWriter.updateNs(ns)
            if (this.name != null) {
                if (resourceId != -1) {
                    this.name = axmlWriter.updateWithResourceId(this.name!!, this.resourceId)
                } else {
                    this.name = axmlWriter.update(this.name)
                }
            }
            val value = value
            if (value is StringItem) {
                this.value = axmlWriter.update(value)
            }
            if (raw != null) {
                raw = axmlWriter.update(raw)
            }
        }
    }

    protected open class NodeImpl(ns: String?, name: String?) : NodeVisitor(null) {
        internal var id: Attr? = null
        internal var style: Attr? = null
        private val attrs: MutableSet<Attr> = TreeSet(ATTR_CMP)
        internal var clz: Attr? = null

        @JvmField
        protected var children: MutableList<NodeImpl> = ArrayList()
        private var line = 0
        private var name: StringItem? = if (name == null) null else StringItem(name)
        private var ns: StringItem? = if (ns == null) null else StringItem(ns)
        private var text: StringItem? = null
        private var textLineNumber = 0

        override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
            if (name == null) {
                throw RuntimeException("name can't be null")
            }
            val a = Attr(if (ns == null) null else StringItem(ns), StringItem(name), resourceId)
            a.type = type

            if (obj is ValueWrapper) {
                if (obj.raw != null) {
                    a.raw = StringItem(obj.raw)
                }
                a.value = obj.ref
                when (obj.type) {
                    ValueWrapper.CLASS -> clz = a
                    ValueWrapper.ID -> id = a
                    ValueWrapper.STYLE -> style = a
                }
            } else if (type == NodeVisitor.TYPE_STRING) {
                val raw = StringItem(obj as String?)
                a.raw = raw
                a.value = raw
            } else {
                a.raw = null
                a.value = obj
            }

            onAttr(a)
        }

        protected open fun onAttr(a: Attr) {
            attrs.add(a)
        }

        override fun child(ns: String?, name: String?): NodeVisitor? {
            val child = NodeImpl(ns, name)
            this.children.add(child)
            return child
        }

        override fun end() {
        }

        override fun line(ln: Int) {
            this.line = ln
        }

        open fun prepare(axmlWriter: AxmlWriter): Int {
            ns = axmlWriter.updateNs(ns)
            name = axmlWriter.update(name)

            var attrIndex = 0
            for (attr in attrs) {
                attr.index = attrIndex++
                attr.prepare(axmlWriter)
            }

            text = axmlWriter.update(text)
            var size = 24 + 36 + attrs.size * 20 // 24 for end tag,36+x*20 for
            // start tag
            for (child in children) {
                size += child.prepare(axmlWriter)
            }
            if (text != null) {
                size += 28
            }
            return size
        }

        override fun text(lineNumber: Int, value: String?) {
            this.text = StringItem(value)
            this.textLineNumber = lineNumber
        }

        @Throws(IOException::class)
        internal open fun write(out: ByteBuffer) {
            // start tag
            out.putInt(ResConst.RES_XML_START_ELEMENT_TYPE or (0x0010 shl 16))
            out.putInt(36 + attrs.size * 20)
            out.putInt(line)
            out.putInt(-1) // 0xFFFFFFFF
            out.putInt(if (ns != null) this.ns!!.index else -1)
            out.putInt(name!!.index)
            out.putInt(0x00140014) // TODO
            out.putShort(this.attrs.size.toShort())
            out.putShort((id?.let { it.index + 1 } ?: 0).toShort())
            out.putShort((clz?.let { it.index + 1 } ?: 0).toShort())
            out.putShort((style?.let { it.index + 1 } ?: 0).toShort())
            for (attr in attrs) {
                out.putInt(if (attr.ns == null) -1 else attr.ns!!.index)
                out.putInt(attr.name!!.index)
                out.putInt(if (attr.raw != null) attr.raw!!.index else -1)
                out.putInt((attr.type shl 24) or 0x000008)
                val v = attr.value
                if (v is StringItem) {
                    out.putInt(v.index)
                } else if (v is Boolean) {
                    out.putInt(if (v) -1 else 0)
                } else if (v is String) {
                    out.putInt(Integer.parseInt(v))
                } else {
                    out.putInt(attr.value as Int)
                }
            }

            if (this.text != null) {
                out.putInt(ResConst.RES_XML_CDATA_TYPE or (0x0010 shl 16))
                out.putInt(28)
                out.putInt(textLineNumber)
                out.putInt(-1) // 0xFFFFFFFF
                out.putInt(text!!.index)
                out.putInt(0x00000008)
                out.putInt(0x00000000)
            }

            // children
            for (child in children) {
                child.write(out)
            }

            // end tag
            out.putInt(ResConst.RES_XML_END_ELEMENT_TYPE or (0x0010 shl 16))
            out.putInt(24)
            out.putInt(-1)
            out.putInt(-1) // 0xFFFFFFFF
            out.putInt(if (ns != null) this.ns!!.index else -1)
            out.putInt(name!!.index)
        }
    }

    protected class Ns(
        internal var prefix: StringItem?,
        internal var uri: StringItem?,
        internal var ln: Int
    )

    companion object {
        @JvmField
        internal val ATTR_CMP: Comparator<Attr> = Comparator { a, b ->
            var x = a.resourceId - b.resourceId
            if (x == 0) {
                x = a.name!!.data!!.compareTo(b.name!!.data!!)
                if (x == 0) {
                    val aNsIsnull = a.ns == null
                    val bNsIsnull = b.ns == null
                    if (aNsIsnull) {
                        if (bNsIsnull) {
                            x = 0
                        } else {
                            x = -1
                        }
                    } else {
                        if (bNsIsnull) {
                            x = 1
                        } else {
                            x = a.ns!!.data!!.compareTo(b.ns!!.data!!)
                        }
                    }
                }
            }
            x
        }
    }
}
