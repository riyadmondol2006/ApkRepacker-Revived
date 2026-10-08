/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.ide.editor.util

import java.util.Random

/**
 * A class that contains utility methods related to numbers.
 *
 * @hide Pending API council approval
 */
object MathUtils {
    private val sRandom = Random()
    private const val DEG_TO_RAD = 3.1415926f / 180.0f
    private const val RAD_TO_DEG = 180.0f / 3.1415926f

    @JvmStatic
    fun abs(v: Float): Float {
        return if (v > 0) v else -v
    }

    @JvmStatic
    fun constrain(amount: Int, low: Int, high: Int): Int {
        return if (amount < low) low else (if (amount > high) high else amount)
    }

    @JvmStatic
    fun constrain(amount: Long, low: Long, high: Long): Long {
        return if (amount < low) low else (if (amount > high) high else amount)
    }

    @JvmStatic
    fun constrain(amount: Float, low: Float, high: Float): Float {
        return if (amount < low) low else (if (amount > high) high else amount)
    }

    @JvmStatic
    fun log(a: Float): Float {
        return Math.log(a.toDouble()).toFloat()
    }

    @JvmStatic
    fun exp(a: Float): Float {
        return Math.exp(a.toDouble()).toFloat()
    }

    @JvmStatic
    fun pow(a: Float, b: Float): Float {
        return Math.pow(a.toDouble(), b.toDouble()).toFloat()
    }

    @JvmStatic
    fun max(a: Float, b: Float): Float {
        return if (a > b) a else b
    }

    @JvmStatic
    fun max(a: Int, b: Int): Float {
        return (if (a > b) a else b).toFloat()
    }

    @JvmStatic
    fun max(a: Float, b: Float, c: Float): Float {
        return if (a > b) (if (a > c) a else c) else (if (b > c) b else c)
    }

    @JvmStatic
    fun max(a: Int, b: Int, c: Int): Float {
        return (if (a > b) (if (a > c) a else c) else (if (b > c) b else c)).toFloat()
    }

    @JvmStatic
    fun min(a: Float, b: Float): Float {
        return if (a < b) a else b
    }

    @JvmStatic
    fun min(a: Int, b: Int): Float {
        return (if (a < b) a else b).toFloat()
    }

    @JvmStatic
    fun min(a: Float, b: Float, c: Float): Float {
        return if (a < b) (if (a < c) a else c) else (if (b < c) b else c)
    }

    @JvmStatic
    fun min(a: Int, b: Int, c: Int): Float {
        return (if (a < b) (if (a < c) a else c) else (if (b < c) b else c)).toFloat()
    }

    @JvmStatic
    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = (x2 - x1)
        val y = (y2 - y1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    @JvmStatic
    fun dist(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
        val x = (x2 - x1)
        val y = (y2 - y1)
        val z = (z2 - z1)
        return Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }

    @JvmStatic
    fun mag(a: Float, b: Float): Float {
        return Math.sqrt((a * a + b * b).toDouble()).toFloat()
    }

    @JvmStatic
    fun mag(a: Float, b: Float, c: Float): Float {
        return Math.sqrt((a * a + b * b + c * c).toDouble()).toFloat()
    }

    @JvmStatic
    fun sq(v: Float): Float {
        return v * v
    }

    @JvmStatic
    fun radians(degrees: Float): Float {
        return degrees * DEG_TO_RAD
    }

    @JvmStatic
    fun degrees(radians: Float): Float {
        return radians * RAD_TO_DEG
    }

    @JvmStatic
    fun acos(value: Float): Float {
        return Math.acos(value.toDouble()).toFloat()
    }

    @JvmStatic
    fun asin(value: Float): Float {
        return Math.asin(value.toDouble()).toFloat()
    }

    @JvmStatic
    fun atan(value: Float): Float {
        return Math.atan(value.toDouble()).toFloat()
    }

    @JvmStatic
    fun atan2(a: Float, b: Float): Float {
        return Math.atan2(a.toDouble(), b.toDouble()).toFloat()
    }

    @JvmStatic
    fun tan(angle: Float): Float {
        return Math.tan(angle.toDouble()).toFloat()
    }

    @JvmStatic
    fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    @JvmStatic
    fun norm(start: Float, stop: Float, value: Float): Float {
        return (value - start) / (stop - start)
    }

    @JvmStatic
    fun map(minStart: Float, minStop: Float, maxStart: Float, maxStop: Float, value: Float): Float {
        return maxStart + (maxStart - maxStop) * ((value - minStart) / (minStop - minStart))
    }

    @JvmStatic
    fun random(howbig: Int): Int {
        return (sRandom.nextFloat() * howbig).toInt()
    }

    @JvmStatic
    fun random(howsmall: Int, howbig: Int): Int {
        if (howsmall >= howbig) return howsmall
        return (sRandom.nextFloat() * (howbig - howsmall) + howsmall).toInt()
    }

    @JvmStatic
    fun random(howbig: Float): Float {
        return sRandom.nextFloat() * howbig
    }

    @JvmStatic
    fun random(howsmall: Float, howbig: Float): Float {
        if (howsmall >= howbig) return howsmall
        return sRandom.nextFloat() * (howbig - howsmall) + howsmall
    }

    @JvmStatic
    fun randomSeed(seed: Long) {
        sRandom.setSeed(seed)
    }
}
