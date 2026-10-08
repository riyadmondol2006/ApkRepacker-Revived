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

interface ResConst {
    companion object {
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_TABLE_TYPE = 0x0002
        const val RES_TABLE_PACKAGE_TYPE = 0x0200
        const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
        const val RES_TABLE_TYPE_TYPE = 0x0201

        const val RES_XML_TYPE = 0x0003
        const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
        const val RES_XML_END_NAMESPACE_TYPE = 0x0101
        const val RES_XML_END_ELEMENT_TYPE = 0x0103
        const val RES_XML_START_NAMESPACE_TYPE = 0x0100
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val RES_XML_CDATA_TYPE = 0x0104
    }
}
