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

/**
 * dump axml to stdout
 *
 * @author [Panxiaobo](mailto:pxb1988@gmail.com)
 */
open class DumpAdapter(nv: NodeVisitor?, x: Int, nses: MutableMap<String?, String?>?) : AxmlVisitor(nv) {
    @JvmField
    protected var deep: Int = x

    @JvmField
    protected var nses: MutableMap<String?, String?>? = nses

    constructor() : this(null)

    constructor(nv: NodeVisitor?) : this(nv, 0, HashMap<String?, String?>())

    override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
        for (i in 0 until deep) {
            print("  ")
        }
        if (ns != null) {
            print(String.format("%s:", getPrefix(ns)))
        }
        print(name)
        if (resourceId != -1) {
            print(String.format("(%08x)", resourceId))
        }
        if (obj is String) {
            print(String.format("=[%08x]\"%s\"", type, obj))
        } else if (obj is Boolean) {
            print(String.format("=[%08x]\"%b\"", type, obj))
        } else if (obj is ValueWrapper) {
            print(String.format("=[%08x]@%08x, raw: \"%s\"", type, obj.ref, obj.raw))
        } else if (type == NodeVisitor.TYPE_REFERENCE) {
            print(String.format("=[%08x]@%08x", type, obj))
        } else {
            print(String.format("=[%08x]%08x", type, obj))
        }
        println()
        super.attr(ns, name, resourceId, type, obj)
    }

    override fun child(ns: String?, name: String?): NodeVisitor? {
        for (i in 0 until deep) {
            print("  ")
        }
        print("<")
        if (ns != null) {
            print(getPrefix(ns) + ":")
        }
        println(name)
        val nv = super.child(ns, name)
        if (nv != null) {
            return DumpAdapter(nv, deep + 1, nses)
        }
        return this
    }

    protected open fun getPrefix(uri: String?): String? {
        if (nses != null) {
            val prefix = nses!![uri]
            if (prefix != null) {
                return prefix
            }
        }
        return uri
    }

    override fun ns(prefix: String?, uri: String?, ln: Int) {
        println(prefix + "=" + uri)
        this.nses!![uri] = prefix
        super.ns(prefix, uri, ln)
    }

    override fun text(lineNumber: Int, value: String?) {
        for (i in 0 until deep + 1) {
            print("  ")
        }
        print("T: ")
        println(value)
        super.text(lineNumber, value)
    }
}
