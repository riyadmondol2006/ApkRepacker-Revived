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

/**
 * A helper class that aims to provide comparable growth performance to ArrayList, but on primitive
 * arrays. Common array operations are implemented for efficient use in dynamic containers.
 *
 * All methods in this class assume that the length of an array is equivalent to its capacity and
 * NOT the number of elements in the array. The current size of the array is always passed in as a
 * parameter.
 *
 * @hide
 */
object GrowingArrayUtils {

    /**
     * Appends an element to the end of the array, growing the array if there is no more room.
     *
     * @param array       The array to which to append the element. This must NOT be null.
     * @param currentSize The number of elements in the array. Must be less than or equal to
     *                    array.length.
     * @param element     The element to append.
     * @return the array to which the element was appended. This may be different than the given
     * array.
     */
    @JvmStatic
    fun <T> append(array: Array<T>, currentSize: Int, element: T): Array<T> {
        var array = array
        assert(currentSize <= array.size)

        if (currentSize + 1 > array.size) {
            @Suppress("UNCHECKED_CAST")
            val newArray = ArrayUtils.newUnpaddedArray(
                array.javaClass.componentType as Class<T>, growSize(currentSize)
            )
            System.arraycopy(array, 0, newArray, 0, currentSize)
            array = newArray
        }
        array[currentSize] = element
        return array
    }

    /**
     * Primitive int version of [append].
     */
    @JvmStatic
    fun append(array: IntArray, currentSize: Int, element: Int): IntArray {
        var array = array
        assert(currentSize <= array.size)

        if (currentSize + 1 > array.size) {
            val newArray = ArrayUtils.newUnpaddedIntArray(growSize(currentSize))
            System.arraycopy(array, 0, newArray, 0, currentSize)
            array = newArray
        }
        array[currentSize] = element
        return array
    }

    /**
     * Primitive long version of [append].
     */
    @JvmStatic
    fun append(array: LongArray, currentSize: Int, element: Long): LongArray {
        var array = array
        assert(currentSize <= array.size)

        if (currentSize + 1 > array.size) {
            val newArray = ArrayUtils.newUnpaddedLongArray(growSize(currentSize))
            System.arraycopy(array, 0, newArray, 0, currentSize)
            array = newArray
        }
        array[currentSize] = element
        return array
    }

    /**
     * Primitive boolean version of [append].
     */
    @JvmStatic
    fun append(array: BooleanArray, currentSize: Int, element: Boolean): BooleanArray {
        var array = array
        assert(currentSize <= array.size)

        if (currentSize + 1 > array.size) {
            val newArray = ArrayUtils.newUnpaddedBooleanArray(growSize(currentSize))
            System.arraycopy(array, 0, newArray, 0, currentSize)
            array = newArray
        }
        array[currentSize] = element
        return array
    }

    /**
     * Inserts an element into the array at the specified index, growing the array if there is no
     * more room.
     *
     * @param array       The array to which to append the element. Must NOT be null.
     * @param currentSize The number of elements in the array. Must be less than or equal to
     *                    array.length.
     * @param element     The element to insert.
     * @return the array to which the element was appended. This may be different than the given
     * array.
     */
    @JvmStatic
    fun <T> insert(array: Array<T>, currentSize: Int, index: Int, element: T): Array<T> {
        assert(currentSize <= array.size)

        if (currentSize + 1 <= array.size) {
            System.arraycopy(array, index, array, index + 1, currentSize - index)
            array[index] = element
            return array
        }

        @Suppress("UNCHECKED_CAST")
        val newArray = ArrayUtils.newUnpaddedArray(
            array.javaClass.componentType as Class<T>,
            growSize(currentSize)
        )
        System.arraycopy(array, 0, newArray, 0, index)
        newArray[index] = element
        System.arraycopy(array, index, newArray, index + 1, array.size - index)
        return newArray
    }

    /**
     * Primitive int version of [insert].
     */
    @JvmStatic
    fun insert(array: IntArray, currentSize: Int, index: Int, element: Int): IntArray {
        assert(currentSize <= array.size)

        if (currentSize + 1 <= array.size) {
            System.arraycopy(array, index, array, index + 1, currentSize - index)
            array[index] = element
            return array
        }

        val newArray = ArrayUtils.newUnpaddedIntArray(growSize(currentSize))
        System.arraycopy(array, 0, newArray, 0, index)
        newArray[index] = element
        System.arraycopy(array, index, newArray, index + 1, array.size - index)
        return newArray
    }

    /**
     * Primitive long version of [insert].
     */
    @JvmStatic
    fun insert(array: LongArray, currentSize: Int, index: Int, element: Long): LongArray {
        assert(currentSize <= array.size)

        if (currentSize + 1 <= array.size) {
            System.arraycopy(array, index, array, index + 1, currentSize - index)
            array[index] = element
            return array
        }

        val newArray = ArrayUtils.newUnpaddedLongArray(growSize(currentSize))
        System.arraycopy(array, 0, newArray, 0, index)
        newArray[index] = element
        System.arraycopy(array, index, newArray, index + 1, array.size - index)
        return newArray
    }

    /**
     * Primitive boolean version of [insert].
     */
    @JvmStatic
    fun insert(array: BooleanArray, currentSize: Int, index: Int, element: Boolean): BooleanArray {
        assert(currentSize <= array.size)

        if (currentSize + 1 <= array.size) {
            System.arraycopy(array, index, array, index + 1, currentSize - index)
            array[index] = element
            return array
        }

        val newArray = ArrayUtils.newUnpaddedBooleanArray(growSize(currentSize))
        System.arraycopy(array, 0, newArray, 0, index)
        newArray[index] = element
        System.arraycopy(array, index, newArray, index + 1, array.size - index)
        return newArray
    }

    /**
     * Given the current size of an array, returns an ideal size to which the array should grow.
     * This is typically double the given size, but should not be relied upon to do so in the
     * future.
     */
    @JvmStatic
    fun growSize(currentSize: Int): Int {
        return if (currentSize <= 4) 8 else currentSize * 2
    }
}
