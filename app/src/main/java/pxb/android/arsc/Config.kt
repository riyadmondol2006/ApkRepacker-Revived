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

class Config(
    @JvmField val id: ByteArray,
    @JvmField val entryCount: Int
) {
    @JvmField
    var resources: MutableMap<Int, ResEntry> = TreeMap()

    /* package */
    internal var wChunkSize = 0

    /* package */
    internal var wEntryStart = 0

    /* package */
    internal var wPosition = 0
}
