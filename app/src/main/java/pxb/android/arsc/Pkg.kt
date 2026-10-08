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

import java.util.TreeMap

class Pkg(
    @JvmField val id: Int,
    @JvmField var name: String?
) {
    @JvmField
    var types: TreeMap<Int, Type> = TreeMap()

    fun getType(tid: Int, name: String?, entrySize: Int): Type {
        var type = types[tid]
        if (type != null) {
            if (name != null) {
                if (type.name == null) {
                    type.name = name
                } else if (!name.endsWith(type.name!!)) {
                    throw RuntimeException()
                }
                if (type.specs!!.size != entrySize) {
                    throw RuntimeException()
                }
            }
        } else {
            type = Type()
            type.id = tid
            type.name = name
            type.specs = arrayOfNulls(entrySize)
            types[tid] = type
        }
        return type
    }
}
