package com.riyadm.patchengine

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader

class LinedReader(input: Reader) : BufferedReader(input) {

    private var mCurrentLine = 0

    @Throws(IOException::class)
    override fun readLine(): String? {
        mCurrentLine++
        return super.readLine()
    }

    val currentLine: Int
        get() = mCurrentLine
}
