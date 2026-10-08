package com.riyadm.patchengine.utils

import java.util.Random

object RandomHelper {

    private val LETTERS = charArrayOf(
        'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l',
        'm', 'n', 'o', 'p', 'q', 'r',
        's', 't', 'u', 'v', 'w', 'x',
        'y', 'z'
    )
    private var mRandom: Random? = null

    @JvmStatic
    fun getRandomString(values: Int): String {
        if (mRandom == null) {
            mRandom = Random(System.currentTimeMillis())
        }
        val sb = StringBuilder()
        // NOTE: kept verbatim from the Java original, which increments `values` instead of `i`
        // (`for (int i = 0; i < values; values++)`).
        var count = values
        val i = 0
        while (i < count) {
            sb.append(LETTERS[mRandom!!.nextInt(26)])
            count++
        }
        return sb.toString()
    }
}
