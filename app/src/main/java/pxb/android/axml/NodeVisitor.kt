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

abstract class NodeVisitor {

    @JvmField
    protected var nv: NodeVisitor? = null

    constructor() : super()

    constructor(nv: NodeVisitor?) : super() {
        this.nv = nv
    }

    /**
     * add attribute to the node
     *
     * @param ns
     * @param name
     * @param resourceId
     * @param type       [TYPE_STRING] or others
     * @param obj        a string for [TYPE_STRING] ,and Integer for others
     */
    open fun attr(ns: String?, name: String?, resourceId: Int, type: Int, obj: Any?) {
        if (nv != null) {
            nv!!.attr(ns, name, resourceId, type, obj)
        }
    }

    /**
     * create a child node
     *
     * @param ns
     * @param name
     * @return
     */
    open fun child(ns: String?, name: String?): NodeVisitor? {
        if (nv != null) {
            return nv!!.child(ns, name)
        }
        return null
    }

    /**
     * end the visit
     */
    open fun end() {
        if (nv != null) {
            nv!!.end()
        }
    }

    /**
     * line number in the .xml
     *
     * @param ln
     */
    open fun line(ln: Int) {
        if (nv != null) {
            nv!!.line(ln)
        }
    }

    /**
     * the node text
     *
     * @param value
     */
    open fun text(lineNumber: Int, value: String?) {
        if (nv != null) {
            nv!!.text(lineNumber, value)
        }
    }

    companion object {
        const val TYPE_FIRST_INT = 0x10
        const val TYPE_INT_BOOLEAN = 0x12
        const val TYPE_INT_HEX = 0x11
        const val TYPE_REFERENCE = 0x01
        const val TYPE_STRING = 0x03
    }
}
