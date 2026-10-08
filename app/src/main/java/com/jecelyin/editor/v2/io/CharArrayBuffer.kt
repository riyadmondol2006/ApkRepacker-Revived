
/*
 * Copyright 2018 Mr Duy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.io

/**
 * Created by jecelyin on 16/3/13.
 */
class CharArrayBuffer(capacity: Int) {
    private var buffer: CharArray
    private var len = 0

    init {
        if (capacity < 0) {
            throw IllegalArgumentException("Buffer capacity may not be negative")
        } else {
            this.buffer = CharArray(capacity)
        }
    }

    private fun expand(newlen: Int) {
        val newbuffer = CharArray(Math.max(this.buffer.size shl 1, newlen))
        System.arraycopy(this.buffer, 0, newbuffer, 0, this.len)
        this.buffer = newbuffer
    }

    fun append(b: CharArray?, off: Int, len: Int) {
        if (b != null) {
            if (off >= 0 && off <= b.size && len >= 0 && off + len >= 0 && off + len <= b.size) {
                if (len != 0) {
                    val newlen = this.len + len
                    if (newlen > this.buffer.size) {
                        this.expand(newlen)
                    }

                    System.arraycopy(b, off, this.buffer, this.len, len)
                    this.len = newlen
                }
            } else {
                throw IndexOutOfBoundsException()
            }
        }
    }

    fun clear() {
        this.len = 0
    }

    fun length(): Int {
        return this.len
    }

    fun charAt(i: Int): Char {
        return this.buffer[i]
    }

    fun buffer(): CharArray {
        return this.buffer
    }

    override fun toString(): String {
        return String(this.buffer, 0, this.len)
    }
}
