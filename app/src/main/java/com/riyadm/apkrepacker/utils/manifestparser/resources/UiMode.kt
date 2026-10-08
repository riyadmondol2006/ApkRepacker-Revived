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
 * UI Mode enum.
 * <p>This is used in the resource folder names.
 */
enum class UiMode(
    private val mValue: String,
    private val mDisplayValue: String,
    private val mSince: Int
) : ResourceEnum {
    NORMAL("", "Normal", 1),
    CAR("car", "Car Dock", 8),
    DESK("desk", "Desk Dock", 8),
    TELEVISION("television", "Television", 13),
    APPLIANCE("appliance", "Appliance", 16),
    WATCH("watch", "Watch", 20),
    VR_HEADSET("vrheadset", "VR Headset", 26);

    override fun getResourceValue(): String {
        return mValue
    }

    fun since(): Int {
        return mSince
    }

    override fun getShortDisplayValue(): String {
        return mDisplayValue
    }

    override fun getLongDisplayValue(): String {
        return mDisplayValue
    }

    override fun isFakeValue(): Boolean {
        return this == NORMAL // NORMAL is not a real enum. it's used for internal state only.
    }

    override fun isValidValueForDevice(): Boolean {
        return this != NORMAL
    }

    companion object {
        /**
         * Returns the enum for matching the provided qualifier value.
         * @param value The qualifier value.
         * @return the enum for the qualifier value or null if no matching was found.
         */
        @JvmStatic
        fun getEnum(value: String?): UiMode? {
            for (mode in entries) {
                if (mode.mValue == value) {
                    return mode
                }
            }

            return null
        }

        @JvmStatic
        fun getIndex(value: UiMode?): Int {
            return value?.ordinal ?: -1
        }

        @JvmStatic
        fun getByIndex(index: Int): UiMode? {
            val values = entries
            if (index >= 0 && index < values.size) {
                return values[index]
            }
            return null
        }
    }
}
