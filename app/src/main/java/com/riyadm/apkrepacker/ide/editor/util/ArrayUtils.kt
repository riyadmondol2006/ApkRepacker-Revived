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

import java.lang.reflect.Array as ReflectArray
import java.util.ArrayList

/**
 * ArrayUtils contains some methods that you can call to find out
 * the most efficient increments by which to grow arrays.
 */
object ArrayUtils {
    private const val CACHE_SIZE = 73
    private val sCache = arrayOfNulls<Any>(CACHE_SIZE)

    @JvmStatic
    fun newUnpaddedByteArray(minLen: Int): ByteArray {
        return ByteArray(minLen)
    }

    @JvmStatic
    fun newUnpaddedCharArray(minLen: Int): CharArray {
        return CharArray(minLen)
    }

    @JvmStatic
    fun newUnpaddedIntArray(minLen: Int): IntArray {
        return IntArray(minLen)
    }

    @JvmStatic
    fun newUnpaddedBooleanArray(minLen: Int): BooleanArray {
        return BooleanArray(minLen)
    }

    @JvmStatic
    fun newUnpaddedLongArray(minLen: Int): LongArray {
        return LongArray(minLen)
    }

    @JvmStatic
    fun newUnpaddedFloatArray(minLen: Int): FloatArray {
        return FloatArray(minLen)
    }

    @JvmStatic
    fun newUnpaddedObjectArray(minLen: Int): Array<Any?> {
        return arrayOfNulls(minLen)
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> newUnpaddedArray(clazz: Class<T>, minLen: Int): Array<T> {
        return ReflectArray.newInstance(clazz, minLen) as Array<T>
    }

    /**
     * Checks if the beginnings of two byte arrays are equal.
     *
     * @param array1 the first byte array
     * @param array2 the second byte array
     * @param length the number of bytes to check
     * @return true if they're equal, false otherwise
     */
    @JvmStatic
    fun equals(array1: ByteArray?, array2: ByteArray?, length: Int): Boolean {
        if (length < 0) {
            throw IllegalArgumentException()
        }

        if (array1 === array2) {
            return true
        }
        if (array1 == null || array2 == null || array1.size < length || array2.size < length) {
            return false
        }
        for (i in 0 until length) {
            if (array1[i] != array2[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Returns an empty array of the specified type.  The intent is that
     * it will return the same empty array every time to avoid reallocation,
     * although this is not guaranteed.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> emptyArray(kind: Class<T>): Array<T> {
        if (kind == Any::class.java) {
            return arrayOfNulls<Any>(0) as Array<T>
        }

        val bucket = (kind.hashCode() and 0x7FFFFFFF) % CACHE_SIZE
        var cache = sCache[bucket]

        if (cache == null || cache.javaClass.componentType != kind) {
            cache = ReflectArray.newInstance(kind, 0)
            sCache[bucket] = cache

            // Log.e("cache", "new empty " + kind.getName() + " at " + bucket);
        }

        return cache as Array<T>
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun <T> isEmpty(array: Array<T>?): Boolean {
        return array == null || array.size == 0
    }

    /**
     * Checks that value is present as at least one of the elements of the array.
     *
     * @param array the array to check in
     * @param value the value to check for
     * @return true if the value is present in the array
     */
    @JvmStatic
    fun <T> contains(array: Array<T>?, value: T): Boolean {
        return indexOf(array, value) != -1
    }

    /**
     * Return first index of `value` in `array`, or `-1` if
     * not found.
     */
    @JvmStatic
    fun <T> indexOf(array: Array<T>?, value: T): Int {
        if (array == null) return -1
        for (i in array.indices) {
            val item = array[i]
            if (item == null) {
                if (value == null) return i
            } else {
                if (value != null && item == value) return i
            }
        }
        return -1
    }

    /**
     * Test if all `check` items are contained in `array`.
     */
    @JvmStatic
    fun <T> containsAll(array: Array<T>?, check: Array<T>): Boolean {
        for (checkItem in check) {
            if (!contains(array, checkItem)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun contains(array: IntArray?, value: Int): Boolean {
        if (array == null) return false
        for (element in array) {
            if (element == value) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun contains(array: LongArray?, value: Long): Boolean {
        if (array == null) return false
        for (element in array) {
            if (element == value) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun total(array: LongArray): Long {
        var total: Long = 0
        for (value in array) {
            total += value
        }
        return total
    }

    /**
     * Appends an element to a copy of the array and returns the copy.
     *
     * @param array   The original array, or null to represent an empty array.
     * @param element The element to add.
     * @return A new array that contains all of the elements of the original array
     * with the specified element added at the end.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> appendElement(kind: Class<T>, array: Array<T>?, element: T): Array<T> {
        val result: Array<T>
        val end: Int
        if (array != null) {
            end = array.size
            result = ReflectArray.newInstance(kind, end + 1) as Array<T>
            System.arraycopy(array, 0, result, 0, end)
        } else {
            end = 0
            result = ReflectArray.newInstance(kind, 1) as Array<T>
        }
        result[end] = element
        return result
    }

    /**
     * Removes an element from a copy of the array and returns the copy.
     * If the element is not present, then the original array is returned unmodified.
     *
     * @param array   The original array, or null to represent an empty array.
     * @param element The element to remove.
     * @return A new array that contains all of the elements of the original array
     * except the first copy of the specified element removed.  If the specified element
     * was not present, then returns the original array.  Returns null if the result
     * would be an empty array.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> removeElement(kind: Class<T>, array: Array<T>?, element: T): Array<T>? {
        if (array != null) {
            val length = array.size
            for (i in 0 until length) {
                if (array[i] === element) {
                    if (length == 1) {
                        return null
                    }
                    val result = ReflectArray.newInstance(kind, length - 1) as Array<T>
                    System.arraycopy(array, 0, result, 0, i)
                    System.arraycopy(array, i + 1, result, i, length - i - 1)
                    return result
                }
            }
        }
        return array
    }

    /**
     * Appends a new value to a copy of the array and returns the copy.  If
     * the value is already present, the original array is returned
     *
     * @param cur The original array, or null to represent an empty array.
     * @param val The value to add.
     * @return A new array that contains all of the values of the original array
     * with the new value added, or the original array.
     */
    @JvmStatic
    fun appendInt(cur: IntArray?, `val`: Int): IntArray {
        if (cur == null) {
            return intArrayOf(`val`)
        }
        val N = cur.size
        for (i in 0 until N) {
            if (cur[i] == `val`) {
                return cur
            }
        }
        val ret = IntArray(N + 1)
        System.arraycopy(cur, 0, ret, 0, N)
        ret[N] = `val`
        return ret
    }

    @JvmStatic
    fun removeInt(cur: IntArray?, `val`: Int): IntArray? {
        if (cur == null) {
            return null
        }
        val N = cur.size
        for (i in 0 until N) {
            if (cur[i] == `val`) {
                val ret = IntArray(N - 1)
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i)
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1)
                }
                return ret
            }
        }
        return cur
    }

    /**
     * Appends a new value to a copy of the array and returns the copy.  If
     * the value is already present, the original array is returned
     *
     * @param cur The original array, or null to represent an empty array.
     * @param val The value to add.
     * @return A new array that contains all of the values of the original array
     * with the new value added, or the original array.
     */
    @JvmStatic
    fun appendLong(cur: LongArray?, `val`: Long): LongArray {
        if (cur == null) {
            return longArrayOf(`val`)
        }
        val N = cur.size
        for (i in 0 until N) {
            if (cur[i] == `val`) {
                return cur
            }
        }
        val ret = LongArray(N + 1)
        System.arraycopy(cur, 0, ret, 0, N)
        ret[N] = `val`
        return ret
    }

    @JvmStatic
    fun removeLong(cur: LongArray?, `val`: Long): LongArray? {
        if (cur == null) {
            return null
        }
        val N = cur.size
        for (i in 0 until N) {
            if (cur[i] == `val`) {
                val ret = LongArray(N - 1)
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i)
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1)
                }
                return ret
            }
        }
        return cur
    }

    @JvmStatic
    fun cloneOrNull(array: LongArray?): LongArray? {
        return array?.clone()
    }

    @JvmStatic
    fun <T> add(cur: ArrayList<T>?, `val`: T): ArrayList<T> {
        var cur = cur
        if (cur == null) {
            cur = ArrayList()
        }
        cur.add(`val`)
        return cur
    }

    @JvmStatic
    fun <T> remove(cur: ArrayList<T>?, `val`: T): ArrayList<T>? {
        if (cur == null) {
            return null
        }
        cur.remove(`val`)
        return if (cur.isEmpty()) {
            null
        } else {
            cur
        }
    }

    @JvmStatic
    fun <T> contains(cur: ArrayList<T>?, `val`: T): Boolean {
        return cur?.contains(`val`) ?: false
    }
}
