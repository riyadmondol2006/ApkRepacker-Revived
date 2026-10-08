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

import com.google.common.collect.Maps
import java.util.EnumSet

/**
 * Allowed values of screen density.
 *
 * <p>This enum is used in the manifest in the uses-configuration node and in the resource folder
 * names as well as in other places that need to know the density values.
 */
enum class Density(
    private val mValue: String,
    private val mDisplayValue: String,
    private val mDpi: Int,
    private val mSince: Int
) : ResourceEnum {
    XXXHIGH("xxxhdpi", "XXX-High Density", 640, 18), //$NON-NLS-1$
    DPI_560("560dpi",  "560 DPI Density",  560,  1), //$NON-NLS-1$
    XXHIGH( "xxhdpi",  "XX-High Density",  480, 16), //$NON-NLS-1$
    DPI_440("440dpi",  "440 DPI Density",  440, 28),
    DPI_420("420dpi",  "420 DPI Density",  420, 23), //$NON-NLS-1$
    DPI_400("400dpi",  "400 DPI Density",  400,  1), //$NON-NLS-1$
    DPI_360("360dpi",  "360 DPI Density",  360, 23), //$NON-NLS-1$
    XHIGH(  "xhdpi",   "X-High Density",   320,  8), //$NON-NLS-1$
    DPI_260("260dpi",  "260 DPI Density",  260, 25), //$NON-NLS-1$
    DPI_280("280dpi",  "280 DPI Density",  280, 22), //$NON-NLS-1$
    DPI_300("300dpi",  "300 DPI Density",  300, 25), //$NON-NLS-1$
    DPI_340("340dpi",  "340 DPI Density",  340, 25), //$NON-NLS-1$
    HIGH(   "hdpi",    "High Density",     240,  4), //$NON-NLS-1$
    DPI_220("220dpi",  "220 DPI Density",  220, 29),
    TV(     "tvdpi",   "TV Density",       213, 13), //$NON-NLS-1$
    DPI_200("200dpi",  "200 DPI Density",  200, 29),
    DPI_180("180dpi",  "180 DPI Density",  180, 29),
    MEDIUM( "mdpi",    "Medium Density",   160,  4), //$NON-NLS-1$
    DPI_140("140dpi",  "140 DPI Density",  140, 29),
    LOW(    "ldpi",    "Low Density",      120,  4), //$NON-NLS-1$
    ANYDPI( "anydpi",  "Any Density",   0xFFFE, 21), // 0xFFFE is the value used by the framework.
    NODPI(  "nodpi",   "No Density",    0xFFFF,  4); // 0xFFFF is the value used by the framework.

    override fun getResourceValue(): String {
        return mValue
    }

    fun getDpiValue(): Int {
        return mDpi
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

    /**
     * Returns true if this density is relevant for app developers (e.g.
     * a density you should consider providing resources for)
     */
    fun isRecommended(): Boolean {
        return when (this) {
            TV,
            DPI_140,
            DPI_180,
            DPI_200,
            DPI_220,
            DPI_260,
            DPI_280,
            DPI_300,
            DPI_340,
            DPI_360,
            DPI_400,
            DPI_420,
            DPI_440,
            DPI_560 -> false
            else -> true
        }
    }

    override fun isFakeValue(): Boolean {
        return false
    }

    override fun isValidValueForDevice(): Boolean {
        return this != NODPI && this != ANYDPI // nodpi/anydpi is not a valid config for devices.
    }

    companion object {
        @JvmField
        val DEFAULT_DENSITY: Int = MEDIUM.getDpiValue()
        private val densityByValue: MutableMap<String, Density> =
            Maps.newHashMapWithExpectedSize(entries.size)

        init {
            for (density in entries) {
                densityByValue[density.mValue] = density
            }
        }

        /**
         * Returns the enum matching the provided qualifier value.
         *
         * @param value The qualifier value.
         * @return the enum for the qualifier value or null if no match was found.
         */
        @JvmStatic
        fun getEnum(value: String?): Density? {
            return if (value == null) null else densityByValue[value]
        }

        /**
         * Returns the enum matching the given DPI value.
         *
         * @param dpiValue The density value.
         * @return the enum for the density value or null if no match was found.
         */
        @JvmStatic
        fun getEnum(dpiValue: Int): Density? {
            val densities = entries
            for (density in densities) {
                if (density.mDpi == dpiValue) {
                    return density
                }
            }
            return null
        }

        @JvmStatic
        fun getIndex(value: Density?): Int {
            return value?.ordinal ?: -1
        }

        @JvmStatic
        fun getByIndex(index: Int): Density? {
            return try {
                values()[index]
            } catch (e: ArrayIndexOutOfBoundsException) {
                null
            }
        }

        /**
         * Returns all densities which are recommended and valid for a device.
         *
         * @see isRecommended
         * @see isValidValueForDevice
         */
        @JvmStatic
        fun getRecommendedValuesForDevice(): Set<Density> {
            val result = EnumSet.noneOf(Density::class.java)
            for (value in entries) {
                if (value.isRecommended() && value.isValidValueForDevice()) {
                    result.add(value)
                }
            }

            return result
        }
    }
}
