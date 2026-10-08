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
 * visitor to visit an axml
 *
 * @author [Panxiaobo](mailto:pxb1988@gmail.com)
 */
open class AxmlVisitor : NodeVisitor {

    constructor() : super()

    constructor(av: NodeVisitor?) : super(av)

    /**
     * create a ns
     *
     * @param prefix
     * @param uri
     * @param ln
     */
    open fun ns(prefix: String?, uri: String?, ln: Int) {
        val nv = nv
        if (nv != null && nv is AxmlVisitor) {
            nv.ns(prefix, uri, ln)
        }
    }
}
