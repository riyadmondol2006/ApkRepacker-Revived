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

import java.io.IOException
import java.util.Stack

/**
 * a class to read android axml
 *
 * @author [Panxiaobo](mailto:pxb1988@gmail.com)
 */
class AxmlReader(data: ByteArray) {
    internal val parser: AxmlParser = AxmlParser(data)

    @Throws(IOException::class)
    fun accept(av: AxmlVisitor) {
        val nvs = Stack<NodeVisitor>()
        var tos: NodeVisitor = av
        while (true) {
            val type = parser.next()
            when (type) {
                AxmlParser.START_TAG -> {
                    nvs.push(tos)
                    val child = tos.child(parser.getNamespaceUri(), parser.getName())
                    if (child != null) {
                        tos = child
                        if (tos !== EMPTY_VISITOR) {
                            tos.line(parser.getLineNumber())
                            for (i in 0 until parser.getAttrCount()) {
                                tos.attr(
                                    parser.getAttrNs(i), parser.getAttrName(i), parser.getAttrResId(i),
                                    parser.getAttrType(i), parser.getAttrValue(i)
                                )
                            }
                        }
                    } else {
                        tos = EMPTY_VISITOR
                    }
                }
                AxmlParser.END_TAG -> {
                    tos.end()
                    tos = nvs.pop()
                }
                AxmlParser.START_NS -> av.ns(parser.getNamespacePrefix(), parser.getNamespaceUri(), parser.getLineNumber())
                AxmlParser.END_NS -> {
                }
                AxmlParser.TEXT -> tos.text(parser.getLineNumber(), parser.getText())
                AxmlParser.END_FILE -> return
            }
        }
    }

    companion object {
        @JvmField
        val EMPTY_VISITOR: NodeVisitor = object : NodeVisitor() {
            override fun child(ns: String?, name: String?): NodeVisitor? {
                return this
            }
        }
    }
}
