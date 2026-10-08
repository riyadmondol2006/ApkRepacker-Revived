/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.riyadm.apkrepacker.utils.manifestparser.resources

/**
 * Represents Android quantities.
 *
 * @see <a href="https://developer.android.com/guide/topics/resources/string-resource#Plurals">Arity
 *     strings (plurals)</a>
 */
enum class Arity(private val mName: String) {
    ZERO("zero"),
    ONE("one"),
    TWO("two"),
    FEW("few"),
    MANY("many"),
    OTHER("other");

    fun getName(): String {
        return mName
    }

    companion object {
        @JvmField
        val EMPTY_ARRAY: Array<Arity> = arrayOf()

        @JvmStatic
        fun getEnum(name: String): Arity? {
            for (value in entries) {
                if (value.mName == name) {
                    return value
                }
            }

            return null
        }
    }
}
