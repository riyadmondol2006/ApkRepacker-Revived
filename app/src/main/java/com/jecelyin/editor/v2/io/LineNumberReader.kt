/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
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
package com.jecelyin.editor.v2.io

import java.io.IOException
import java.io.Reader

/**
 * Wraps an existing [Reader] and counts the line terminators encountered
 * while reading the data. The line number starts at 0 and is incremented any
 * time `'\r'`, `'\n'` or `"\r\n"` is read. The class has an
 * internal buffer for its data. The size of the buffer defaults to 8 KB.
 */
open class LineNumberReader : BufferedReader {

    private var lineNumber = 0

    private var markedLineNumber = -1

    private var lastWasCR = false

    private var markedLastWasCR = false

    /**
     * Constructs a new LineNumberReader on the Reader `in`. The internal
     * buffer gets the default size (8 KB).
     *
     * @param in the Reader that is buffered.
     */
    constructor(`in`: Reader) : super(`in`)

    /**
     * Constructs a new LineNumberReader on the Reader `in`. The size of
     * the internal buffer is specified by the parameter `size`.
     *
     * @param in   the Reader that is buffered.
     * @param size the size of the buffer to allocate.
     * @throws IllegalArgumentException if `size <= 0`.
     */
    constructor(`in`: Reader, size: Int) : super(`in`, size)

    /**
     * Returns the current line number for this reader. Numbering starts at 0.
     *
     * @return the current line number.
     */
    open fun getLineNumber(): Int {
        synchronized(lock) {
            return lineNumber
        }
    }

    /**
     * Sets the line number of this reader to the specified `lineNumber`.
     * Note that this may have side effects on the line number associated with
     * the last marked position.
     *
     * @param lineNumber the new line number value.
     * @see .mark
     * @see .reset
     */
    open fun setLineNumber(lineNumber: Int) {
        synchronized(lock) {
            this.lineNumber = lineNumber
        }
    }

    /**
     * Sets a mark position in this reader. The parameter `readlimit`
     * indicates how many characters can be read before the mark is invalidated.
     * Sending `reset()` will reposition this reader back to the marked
     * position, provided that `readlimit` has not been surpassed. The
     * line number associated with this marked position is also stored so that
     * it can be restored when `reset()` is called.
     *
     * @param readlimit the number of characters that can be read from this stream
     * before the mark is invalidated.
     * @throws IOException if an error occurs while setting the mark in this reader.
     * @see .markSupported
     * @see .reset
     */
    @Throws(IOException::class)
    override fun mark(readlimit: Int) {
        synchronized(lock) {
            super.mark(readlimit)
            markedLineNumber = lineNumber
            markedLastWasCR = lastWasCR
        }
    }

    /**
     * Reads a single character from the source reader and returns it as an
     * integer with the two higher-order bytes set to 0. Returns -1 if the end
     * of the source reader has been reached.
     *
     * The line number count is incremented if a line terminator is encountered.
     * Recognized line terminator sequences are `'\r'`, `'\n'` and
     * `"\r\n"`. Line terminator sequences are always translated into
     * `'\n'`.
     *
     * @return the character read or -1 if the end of the source reader has been
     * reached.
     * @throws IOException if the reader is closed or another IOException occurs.
     */
    @Throws(IOException::class)
    override fun read(): Int {
        synchronized(lock) {
            var ch = super.read()
            if (ch == '\n'.code && lastWasCR) {
                ch = super.read()
            }
            lastWasCR = false
            when (ch) {
                '\r'.code -> {
                    ch = '\n'.code
                    lastWasCR = true
                    // fall through
                    lineNumber++
                }
                '\n'.code -> lineNumber++
            }
            return ch
        }
    }

    /**
     * Reads up to `count` characters from the source reader and stores
     * them in the character array `buffer` starting at `offset`.
     * Returns the number of characters actually read or -1 if no characters
     * have been read and the end of this reader has been reached.
     *
     * The line number count is incremented if a line terminator is encountered.
     * Recognized line terminator sequences are `'\r'`, `'\n'` and
     * `"\r\n"`.
     *
     * @throws IOException if this reader is closed or another IOException occurs.
     */
    @Throws(IOException::class)
    override fun read(buffer: CharArray, offset: Int, count: Int): Int {
        synchronized(lock) {
            val read = super.read(buffer, offset, count)
            if (read == -1) {
                return -1
            }
            for (i in 0 until read) {
                val ch = buffer[offset + i]
                if (ch == '\r') {
                    lineNumber++
                    lastWasCR = true
                } else if (ch == '\n') {
                    if (!lastWasCR) {
                        lineNumber++
                    }
                    lastWasCR = false
                } else {
                    lastWasCR = false
                }
            }
            return read
        }
    }

    /**
     * Returns the next line of text available from this reader. A line is
     * represented by 0 or more characters followed by `'\r'`,
     * `'\n'`, `"\r\n"` or the end of the stream. The returned
     * string does not include the newline sequence.
     *
     * @return the contents of the line or `null` if no characters have
     * been read before the end of the stream has been reached.
     * @throws IOException if this reader is closed or another IOException occurs.
     */
    @Throws(IOException::class)
    override fun readLine(): String? {
        synchronized(lock) {
            if (lastWasCR) {
                chompNewline()
                lastWasCR = false
            }
            val result = super.readLine()
            if (result != null) {
                lineNumber++
            }
            return result
        }
    }

    /**
     * Resets this reader to the last marked location. It also resets the line
     * count to what is was when this reader was marked. This implementation
     * resets the source reader.
     *
     * @throws IOException if this reader is already closed, no mark has been set or the
     * mark is no longer valid because more than `readlimit`
     * bytes have been read since setting the mark.
     * @see .mark
     * @see .markSupported
     */
    @Throws(IOException::class)
    override fun reset() {
        synchronized(lock) {
            super.reset()
            lineNumber = markedLineNumber
            lastWasCR = markedLastWasCR
        }
    }

    /**
     * Skips `charCount` characters in this reader. Subsequent calls to
     * `read` will not return these characters unless `reset`
     * is used. This implementation skips `charCount` number of characters in
     * the source reader and increments the line number count whenever line
     * terminator sequences are skipped.
     *
     * @return the number of characters actually skipped.
     * @throws IllegalArgumentException if `charCount < 0`.
     * @throws IOException              if this reader is closed or another IOException occurs.
     * @see .mark
     * @see .read
     * @see .reset
     */
    @Throws(IOException::class)
    override fun skip(charCount: Long): Long {
        if (charCount < 0) {
            throw IllegalArgumentException("charCount < 0: $charCount")
        }
        synchronized(lock) {
            var i = 0
            while (i < charCount) {
                if (read() == -1) {
                    return i.toLong()
                }
                i++
            }
            return charCount
        }
    }
}
