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
        val rnd = mRandom!!
        repeat(values) { sb.append(LETTERS[rnd.nextInt(26)]) }
        return sb.toString()
    }
}
