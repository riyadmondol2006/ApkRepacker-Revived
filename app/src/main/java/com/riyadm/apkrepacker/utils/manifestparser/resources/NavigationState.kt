/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * Navigation state enum.
 * <p>This is used in the resource folder names.
 */
enum class NavigationState(
    private val mValue: String,
    private val mShortDisplayValue: String,
    private val mLongDisplayValue: String
) : ResourceEnum {
    EXPOSED("navexposed", "Exposed", "Exposed navigation"), //$NON-NLS-1$
    HIDDEN("navhidden", "Hidden", "Hidden navigation"); //$NON-NLS-1$

    override fun getResourceValue(): String {
        return mValue
    }

    override fun getShortDisplayValue(): String {
        return mShortDisplayValue
    }

    override fun getLongDisplayValue(): String {
        return mLongDisplayValue
    }

    override fun isFakeValue(): Boolean {
        return false
    }

    override fun isValidValueForDevice(): Boolean {
        return true
    }

    companion object {
        /**
         * Returns the enum for matching the provided qualifier value.
         * @param value The qualifier value.
         * @return the enum for the qualifier value or null if no matching was found.
         */
        @JvmStatic
        fun getEnum(value: String?): NavigationState? {
            for (v in entries) {
                if (v.mValue == value) {
                    return v
                }
            }

            return null
        }

        @JvmStatic
        fun getIndex(value: NavigationState?): Int {
            return value?.ordinal ?: -1
        }

        @JvmStatic
        fun getByIndex(index: Int): NavigationState? {
            val values = entries
            if (index >= 0 && index < values.size) {
                return values[index]
            }
            return null
        }
    }
}
