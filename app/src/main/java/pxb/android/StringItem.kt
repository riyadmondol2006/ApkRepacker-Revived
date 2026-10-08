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
package pxb.android

class StringItem {
    @JvmField
    var data: String? = null

    @JvmField
    var dataOffset: Int = 0

    @JvmField
    var index: Int = 0

    constructor() : super()

    constructor(data: String?) : super() {
        this.data = data
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other == null)
            return false
        if (javaClass != other.javaClass)
            return false
        other as StringItem
        if (data == null) {
            if (other.data != null)
                return false
        } else if (data != other.data)
            return false
        return true
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (data?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return String.format("S%04d %s", index, data)
    }
}
