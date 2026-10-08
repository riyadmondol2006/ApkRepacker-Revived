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

class Type {
    @JvmField
    var configs: MutableList<Config> = ArrayList()

    @JvmField
    var id: Int = 0

    @JvmField
    var name: String? = null

    @JvmField
    var specs: Array<ResSpec?>? = null

    /* package */
    internal var wPosition = 0

    fun addConfig(config: Config) {
        if (config.entryCount != specs!!.size) {
            throw RuntimeException()
        }
        configs.add(config)
    }

    fun getSpec(resId: Int): ResSpec {
        val specs = specs!!
        var res = specs[resId]
        if (res == null) {
            res = ResSpec(resId)
            specs[resId] = res
        }
        return res
    }
}
