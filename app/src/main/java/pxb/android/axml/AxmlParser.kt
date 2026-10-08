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
import pxb.android.StringItems
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * a class to read android axml
 *
 * @author [Panxiaobo](mailto:pxb1988@gmail.com)
 */
class AxmlParser(input: ByteBuffer) : ResConst {

    // private int attrName[];
    // private int attrNs[];
    // private int attrResId[];
    // private int attrType[];
    // private Object attrValue[];

    private var attributeCount = 0

    private var attrs: IntBuffer? = null

    private var classAttribute = 0
    private var fileSize = -1
    private var idAttribute = 0
    private val input: ByteBuffer = input.order(ByteOrder.LITTLE_ENDIAN)
    private var lineNumber = 0
    private var nameIdx = 0
    private var nsIdx = 0

    private var prefixIdx = 0

    private var resourceIds: IntArray? = null

    private var strings: Array<String?>? = null

    private var styleAttribute = 0

    private var textIdx = 0

    constructor(data: ByteArray) : this(ByteBuffer.wrap(data))

    fun getAttrCount(): Int {
        return attributeCount
    }

    fun getAttributeCount(): Int {
        return attributeCount
    }

    fun getAttrName(i: Int): String? {
        val idx = attrs!!.get(i * 5 + 1)
        return strings!![idx]
    }

    fun getAttrNs(i: Int): String? {
        val idx = attrs!!.get(i * 5 + 0)
        return if (idx >= 0) strings!![idx] else null
    }

    internal fun getAttrRawString(i: Int): String? {
        val idx = attrs!!.get(i * 5 + 2)
        if (idx >= 0) {
            return strings!![idx]
        }
        return null
    }

    fun getAttrResId(i: Int): Int {
        val resourceIds = resourceIds
        if (resourceIds != null) {
            val idx = attrs!!.get(i * 5 + 1)
            if (idx >= 0 && idx < resourceIds.size) {
                return resourceIds[idx]
            }
        }
        return -1
    }

    fun getAttrType(i: Int): Int {
        return attrs!!.get(i * 5 + 3) shr 24
    }

    fun getAttrValue(i: Int): Any? {
        val v = attrs!!.get(i * 5 + 4)

        if (i == idAttribute) {
            return ValueWrapper.wrapId(v, getAttrRawString(i))
        } else if (i == styleAttribute) {
            return ValueWrapper.wrapStyle(v, getAttrRawString(i))
        } else if (i == classAttribute) {
            return ValueWrapper.wrapClass(v, getAttrRawString(i))
        }

        return when (getAttrType(i)) {
            NodeVisitor.TYPE_STRING -> strings!![v]
            NodeVisitor.TYPE_INT_BOOLEAN -> v != 0
            else -> v
        }
    }

    fun getLineNumber(): Int {
        return lineNumber
    }

    fun getName(): String? {
        return strings!![nameIdx]
    }

    fun getNamespacePrefix(): String? {
        return strings!![prefixIdx]
    }

    fun getNamespaceUri(): String? {
        return if (nsIdx >= 0) strings!![nsIdx] else null
    }

    fun getText(): String? {
        return strings!![textIdx]
    }

    @Throws(IOException::class)
    fun next(): Int {
        if (fileSize < 0) {
            val type = input.int and 0xFFFF
            if (type != ResConst.RES_XML_TYPE) {
                throw RuntimeException()
            }
            fileSize = input.int
            return START_FILE
        }
        var event = -1
        while (true) {
            val p = input.position()
            if (p >= fileSize) {
                break
            }
            val type = input.int and 0xFFFF
            val size = input.int
            when (type) {
                ResConst.RES_XML_START_ELEMENT_TYPE -> {
                    run {
                        lineNumber = input.int
                        input.int /* skip, 0xFFFFFFFF */
                        nsIdx = input.int
                        nameIdx = input.int
                        val flag = input.int // 0x00140014 ?
                        if (flag != 0x00140014) {
                            throw RuntimeException()
                        }
                    }

                    attributeCount = input.short.toInt() and 0xFFFF
                    idAttribute = (input.short.toInt() and 0xFFFF) - 1
                    classAttribute = (input.short.toInt() and 0xFFFF) - 1
                    styleAttribute = (input.short.toInt() and 0xFFFF) - 1

                    attrs = input.asIntBuffer()

                    // attrResId = new int[attributeCount];
                    // attrName = new int[attributeCount];
                    // attrNs = new int[attributeCount];
                    // attrType = new int[attributeCount];
                    // attrValue = new Object[attributeCount];
                    // for (int i = 0; i < attributeCount; i++) {
                    // int attrNsIdx = in.getInt();
                    // int attrNameIdx = in.getInt();
                    // int raw = in.getInt();
                    // int aValueType = in.getInt() >>> 24;
                    // int aValue = in.getInt();
                    // Object value = null;
                    // switch (aValueType) {
                    // case TYPE_STRING:
                    // value = strings[aValue];
                    // break;
                    // case TYPE_INT_BOOLEAN:
                    // value = aValue != 0;
                    // break;
                    // default:
                    // value = aValue;
                    // }
                    // int resourceId = attrNameIdx < this.resourceIds.length ?
                    // resourceIds[attrNameIdx] : -1;
                    // attrNs[i] = attrNsIdx;
                    // attrName[i] = attrNameIdx;
                    // attrType[i] = aValueType;
                    // attrResId[i] = resourceId;
                    // attrValue[i] = value;
                    // }
                    event = START_TAG
                }
                ResConst.RES_XML_END_ELEMENT_TYPE -> {
                    input.position(p + size)
                    event = END_TAG
                }
                ResConst.RES_XML_START_NAMESPACE_TYPE -> {
                    lineNumber = input.int
                    input.int /* 0xFFFFFFFF */
                    prefixIdx = input.int
                    nsIdx = input.int
                    event = START_NS
                }
                ResConst.RES_XML_END_NAMESPACE_TYPE -> {
                    input.position(p + size)
                    event = END_NS
                }
                ResConst.RES_STRING_POOL_TYPE -> {
                    strings = StringItems.read(input)
                    input.position(p + size)
                    continue
                }
                ResConst.RES_XML_RESOURCE_MAP_TYPE -> {
                    val count = size / 4 - 2
                    val ids = IntArray(count)
                    resourceIds = ids
                    for (i in 0 until count) {
                        ids[i] = input.int
                    }
                    input.position(p + size)
                    continue
                }
                ResConst.RES_XML_CDATA_TYPE -> {
                    lineNumber = input.int
                    input.int /* 0xFFFFFFFF */
                    textIdx = input.int

                    input.int /* 00000008 00000000 */
                    input.int

                    event = TEXT
                }
                else -> throw RuntimeException()
            }
            input.position(p + size)
            return event
        }
        return END_FILE
    }

    companion object {
        const val END_FILE = 7
        const val END_NS = 5
        const val END_TAG = 3
        const val START_FILE = 1
        const val START_NS = 4
        const val START_TAG = 2
        const val TEXT = 6
    }
}
