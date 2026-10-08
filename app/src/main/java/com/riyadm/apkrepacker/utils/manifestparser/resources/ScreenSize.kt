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
 * Screen size enum.
 * <p>This is used in the manifest in the uses-configuration node and in the resource folder names.
 */
enum class ScreenSize(
    private val mValue: String,
    private val mShortDisplayValue: String,
    private val mLongDisplayValue: String
) : ResourceEnum {
    SMALL("small", "Small", "Small Screen"), //$NON-NLS-1$
    NORMAL("normal", "Normal", "Normal Screen"), //$NON-NLS-1$
    LARGE("large", "Large", "Large Screen"), //$NON-NLS-1$
    XLARGE("xlarge", "X-Large", "Extra Large Screen"); //$NON-NLS-1$

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
        fun getEnum(value: String?): ScreenSize? {
            for (orient in entries) {
                if (orient.mValue == value) {
                    return orient
                }
            }

            return null
        }

        @JvmStatic
        fun getIndex(value: ScreenSize?): Int {
            return value?.ordinal ?: -1
        }

        @JvmStatic
        fun getByIndex(index: Int): ScreenSize? {
            val values = entries
            if (index >= 0 && index < values.size) {
                return values[index]
            }
            return null
        }

        /**
         * Get the resource bucket value that corresponds to the given size in inches.
         *
         * @param diagonalSize Diagonal Screen size in inches.
         *                     If null, a default diagonal size is used
         */
        @JvmStatic
        fun getScreenSize(diagonalSize: Double?): ScreenSize {
            if (diagonalSize == null) {
                return NORMAL
            }

            // Density-independent pixel (dp) : The density-independent pixel is
            // equivalent to one physical pixel on a 160 dpi screen,
            // which is the baseline density assumed by the system for a
            // "medium" density screen.
            // Android 8.1 Compatibility Definition, section 7.1
            val diagonalDp = 160.0 * diagonalSize

            // Set the Screen Size
            if (diagonalDp >= 1200) return XLARGE
            if (diagonalDp >= 800) return LARGE
            if (diagonalDp >= 568) return NORMAL

            return SMALL
        }
    }
}
