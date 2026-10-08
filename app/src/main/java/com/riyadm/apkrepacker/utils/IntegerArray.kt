package com.riyadm.apkrepacker.utils

/**
 * A simple collection that stores integers and grows automatically.
 */
class IntegerArray @JvmOverloads constructor(initialSize: Int = 2000) {

    var array: IntArray = IntArray(initialSize)
        private set

    /** number of stored elements (Java: getSize()/setSize()) */
    var size = 0


    fun add(num: Int) {
        if (size >= array.size) {
            val arrayN = IntArray(size * 2)
            System.arraycopy(array, 0, arrayN, 0, size)
            array = arrayN
        }

        array[size++] = num
    }


    fun get(index: Int): Int {
        return array[index]
    }


    fun clear() {
        size = 0
    }

}
