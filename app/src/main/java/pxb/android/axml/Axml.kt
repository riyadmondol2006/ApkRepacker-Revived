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

class Axml : AxmlVisitor() {

    @JvmField
    var firsts: MutableList<Node> = ArrayList()

    @JvmField
    var nses: MutableList<Ns> = ArrayList()

    fun accept(visitor: AxmlVisitor) {
        for (ns in nses) {
            ns.accept(visitor)
        }
        for (first in firsts) {
            first.accept(visitor)
        }
    }

    override fun child(ns: String?, name: String?): NodeVisitor? {
        val node = Node()
        node.name = name
        node.ns = ns
        firsts.add(node)
        return node
    }

    override fun ns(prefix: String?, uri: String?, ln: Int) {
        val ns = Ns()
        ns.prefix = prefix
        ns.uri = uri
        ns.ln = ln
        nses.add(ns)
    }

    class Node : NodeVisitor() {
        @JvmField
        var attrs: MutableList<Attr> = ArrayList()

        @JvmField
        var children: MutableList<Node> = ArrayList()

        @JvmField
        var ln: Int? = null

        @JvmField
        var ns: String? = null

        @JvmField
        var name: String? = null

        @JvmField
        var text: Text? = null

        fun accept(nodeVisitor: NodeVisitor) {
            val nodeVisitor2 = nodeVisitor.child(ns, name)
            acceptB(nodeVisitor2!!)
            nodeVisitor2.end()
        }

        fun acceptB(nodeVisitor: NodeVisitor) {
            if (text != null) {
                text!!.accept(nodeVisitor)
            }
            for (a in attrs) {
                a.accept(nodeVisitor)
            }
            val ln = ln
            if (ln != null) {
                nodeVisitor.line(ln)
            }
            for (c in children) {
                c.accept(nodeVisitor)
            }
        }

        override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
            val attr = Attr()
            attr.name = name
            attr.ns = ns
            attr.resourceId = resourceId
            attr.type = type
            attr.value = obj
            attrs.add(attr)
        }

        override fun child(ns: String?, name: String?): NodeVisitor? {
            val node = Node()
            node.name = name
            node.ns = ns
            children.add(node)
            return node
        }

        override fun line(ln: Int) {
            this.ln = ln
        }

        override fun text(lineNumber: Int, value: String?) {
            val text = Text()
            text.ln = lineNumber
            text.text = value
            this.text = text
        }

        class Attr {
            @JvmField
            var ns: String? = null

            @JvmField
            var name: String? = null

            @JvmField
            var resourceId: Int = 0

            @JvmField
            var type: Int = 0

            @JvmField
            var value: Any? = null

            fun accept(nodeVisitor: NodeVisitor) {
                nodeVisitor.attr(ns, name, resourceId, type, value)
            }
        }

        class Text {
            @JvmField
            var ln: Int = 0

            @JvmField
            var text: String? = null

            fun accept(nodeVisitor: NodeVisitor) {
                nodeVisitor.text(ln, text)
            }
        }
    }

    class Ns {
        @JvmField
        var ln: Int = 0

        @JvmField
        var prefix: String? = null

        @JvmField
        var uri: String? = null

        fun accept(visitor: AxmlVisitor) {
            visitor.ns(prefix, uri, ln)
        }
    }
}
