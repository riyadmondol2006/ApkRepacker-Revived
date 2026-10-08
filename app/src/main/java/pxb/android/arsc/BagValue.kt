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

class BagValue(@JvmField val parent: Int) {
    @JvmField
    var map: MutableList<Map.Entry<Int, Value>> = ArrayList()

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other == null)
            return false
        if (other !is BagValue)
            return false
        if (map != other.map)
            return false
        if (parent != other.parent)
            return false
        return true
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + map.hashCode()
        result = prime * result + parent
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(String.format("{bag%08x", parent))
        for (e in map) {
            sb.append(",").append(String.format("0x%08x", e.key))
            sb.append("=")
            sb.append(e.value)
        }

        return sb.append("}").toString()
    }
}
