
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
 * Wraps an existing [Reader] and <em>buffers</em> the input. Expensive
 * interaction with the underlying reader is minimized, since most (smaller)
 * requests can be satisfied by accessing the buffer alone. The drawback is that
 * some extra space is required to hold the buffer and that copying takes place
 * when filling that buffer, but this is usually outweighed by the performance
 * benefits.
 *
 * A typical application pattern for the class looks like this:
 *
 * <pre>
 * BufferedReader buf = new BufferedReader(new FileReader(&quot;file.java&quot;));
 * </pre>
 *
 * @see java.io.BufferedWriter
 * @since 1.1
 */
open class BufferedReader @JvmOverloads constructor(private val `in`: Reader, size: Int = 8192) : Reader(`in`) {

    /**
     * The characters that can be read and refilled in bulk. We maintain three
     * indices into this buffer:<pre>
     *     { X X X X X X X X X X X X - - }
     *           ^     ^             ^
     *           |     |             |
     *         mark   pos           end</pre>
     * Pos points to the next readable character. End is one greater than the
     * last readable character. When `pos == end`, the buffer is empty and
     * must be [filled][fillBuf] before characters can be read.
     *
     * Mark is the value pos will be set to on calls to [reset]. Its
     * value is in the range `[0...pos]`. If the mark is `-1`, the
     * buffer cannot be reset.
     *
     * MarkLimit limits the distance between the mark and the pos. When this
     * limit is exceeded, [reset] is permitted (but not required) to
     * throw an exception. For shorter distances, [reset] shall not throw
     * (unless the reader is closed).
     */
    private var buf: CharArray?

    private var pos = 0

    private var end = 0

    private var mark = -1

    private var markLimit = -1

    /**
     * readLine returns a line as soon as it sees '\n' or '\r'. In the latter
     * case, there might be a following '\n' that should be treated as part of
     * the same line ending. Both readLine and all read methods are supposed
     * to skip the '\n' (and clear this field) but only readLine looks for '\r'
     * and sets it.
     */
    private var lastWasCR = false

    /**
     * We also need to keep the 'lastWasCR' state for the mark position, in case
     * we reset to there.
     */
    private var markedLastWasCR = false

    /**
     * Constructs a new `BufferedReader`, providing `in` with `size` characters
     * of buffer.
     *
     * @param in the `InputStream` the buffer reads from.
     * @param size the size of buffer in characters.
     * @throws IllegalArgumentException if `size <= 0`.
     */
    init {
        if (size <= 0) {
            throw IllegalArgumentException("size <= 0")
        }
        buf = CharArray(size)
    }

    /**
     * Closes this reader. This implementation closes the buffered source reader
     * and releases the buffer. Nothing is done if this reader has already been
     * closed.
     *
     * @throws IOException
     * if an error occurs while closing this reader.
     */
    @Throws(IOException::class)
    override fun close() {
        synchronized(lock) {
            if (!isClosed()) {
                `in`.close()
                buf = null
            }
        }
    }

    /**
     * Populates the buffer with data. It is an error to call this method when
     * the buffer still contains data; ie. if `pos < end`.
     *
     * @return the number of chars read into the buffer, or -1 if the end of the
     * source stream has been reached.
     */
    @Throws(IOException::class)
    private fun fillBuf(): Int {
        // assert(pos == end);

        if (mark == -1 || (pos - mark >= markLimit)) {
            /* mark isn't set or has exceeded its limit. use the whole buffer */
            val result = `in`.read(buf!!, 0, buf!!.size)
            if (result > 0) {
                mark = -1
                pos = 0
                end = result
            }
            return result
        }

        if (mark == 0 && markLimit > buf!!.size) {
            /* the only way to make room when mark=0 is by growing the buffer */
            var newLength = buf!!.size * 2
            if (newLength > markLimit) {
                newLength = markLimit
            }
            val newbuf = CharArray(newLength)
            System.arraycopy(buf!!, 0, newbuf, 0, buf!!.size)
            buf = newbuf
        } else if (mark > 0) {
            /* make room by shifting the buffered data to left mark positions */
            System.arraycopy(buf!!, mark, buf!!, 0, buf!!.size - mark)
            pos -= mark
            end -= mark
            mark = 0
        }

        /* Set the new position and mark position */
        val count = `in`.read(buf!!, pos, buf!!.size - pos)
        if (count != -1) {
            end += count
        }
        return count
    }

    /**
     * Indicates whether or not this reader is closed.
     *
     * @return `true` if this reader is closed, `false`
     * otherwise.
     */
    private fun isClosed(): Boolean {
        return buf == null
    }

    /**
     * Sets a mark position in this reader. The parameter `markLimit`
     * indicates how many characters can be read before the mark is invalidated.
     * Calling `reset()` will reposition the reader back to the marked
     * position if `markLimit` has not been surpassed.
     *
     * @param markLimit
     * the number of characters that can be read before the mark is
     * invalidated.
     * @throws IllegalArgumentException
     * if `markLimit < 0`.
     * @throws IOException
     * if an error occurs while setting a mark in this reader.
     * @see .markSupported
     * @see .reset
     */
    @Throws(IOException::class)
    override fun mark(markLimit: Int) {
        if (markLimit < 0) {
            throw IllegalArgumentException("markLimit < 0:$markLimit")
        }
        synchronized(lock) {
            checkNotClosed()
            this.markLimit = markLimit
            this.mark = pos
            this.markedLastWasCR = lastWasCR
        }
    }

    @Throws(IOException::class)
    private fun checkNotClosed() {
        if (isClosed()) {
            throw IOException("BufferedReader is closed")
        }
    }

    /**
     * Indicates whether this reader supports the `mark()` and
     * `reset()` methods. This implementation returns `true`.
     *
     * @return `true` for `BufferedReader`.
     * @see .mark
     * @see .reset
     */
    override fun markSupported(): Boolean {
        return true
    }

    /**
     * Reads a single character from this reader and returns it with the two
     * higher-order bytes set to 0. If possible, BufferedReader returns a
     * character from the buffer. If there are no characters available in the
     * buffer, it fills the buffer and then returns a character. It returns -1
     * if there are no more characters in the source reader.
     *
     * @return the character read or -1 if the end of the source reader has been
     * reached.
     * @throws IOException
     * if this reader is closed or some other I/O error occurs.
     */
    @Throws(IOException::class)
    override fun read(): Int {
        synchronized(lock) {
            checkNotClosed()
            var ch = readChar()
            if (lastWasCR && ch == '\n'.code) {
                ch = readChar()
            }
            lastWasCR = false
            return ch
        }
    }

    @Throws(IOException::class)
    private fun readChar(): Int {
        if (pos < end || fillBuf() != -1) {
            return buf!![pos++].code
        }
        return -1
    }

    /**
     * Reads up to `length` characters from this reader and stores them
     * at `offset` in the character array `buffer`. Returns the
     * number of characters actually read or -1 if the end of the source reader
     * has been reached. If all the buffered characters have been used, a mark
     * has not been set and the requested number of characters is larger than
     * this readers buffer size, BufferedReader bypasses the buffer and simply
     * places the results directly into `buffer`.
     *
     * @throws IndexOutOfBoundsException
     * if `offset < 0 || length < 0 || offset + length > buffer.length`.
     * @throws IOException
     * if this reader is closed or some other I/O error occurs.
     */
    @Throws(IOException::class)
    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        var offset = offset
        synchronized(lock) {
            checkNotClosed()
            checkOffsetAndCount(buffer.size, offset, length)
            if (length == 0) {
                return 0
            }

            maybeSwallowLF()

            var outstanding = length
            while (outstanding > 0) {
                // If there are chars in the buffer, grab those first.
                val available = end - pos
                if (available > 0) {
                    val count = if (available >= outstanding) outstanding else available
                    System.arraycopy(buf!!, pos, buffer, offset, count)
                    pos += count
                    offset += count
                    outstanding -= count
                }

                /*
                 * Before attempting to read from the underlying stream, make
                 * sure we really, really want to. We won't bother if we're
                 * done, or if we've already got some chars and reading from the
                 * underlying stream would block.
                 */
                if (outstanding == 0 || (outstanding < length && !`in`.ready())) {
                    break
                }

                // assert(pos == end);

                /*
                 * If we're unmarked and the requested size is greater than our
                 * buffer, read the chars directly into the caller's buffer. We
                 * don't read into smaller buffers because that could result in
                 * a many reads.
                 */
                if ((mark == -1 || (pos - mark >= markLimit)) && outstanding >= buf!!.size) {
                    val count = `in`.read(buffer, offset, outstanding)
                    if (count > 0) {
                        outstanding -= count
                        mark = -1
                    }
                    break // assume the source stream gave us all that it could
                }

                if (fillBuf() == -1) {
                    break // source is exhausted
                }
            }

            val count = length - outstanding
            if (count > 0) {
                return count
            }
            return -1
        }
    }

    /**
     * Peeks at the next input character, refilling the buffer if necessary. If
     * this character is a newline character ("\n"), it is discarded.
     */
    @Throws(IOException::class)
    protected fun chompNewline() {
        if ((pos != end || fillBuf() != -1) && buf!![pos] == '\n') {
            ++pos
        }
    }

    // If the last character was CR and the next character is LF, skip it.
    @Throws(IOException::class)
    private fun maybeSwallowLF() {
        if (lastWasCR) {
            chompNewline()
            lastWasCR = false
        }
    }

    fun isLastWasCR(): Boolean {
        return lastWasCR
    }

    /**
     * Returns the next line of text available from this reader. A line is
     * represented by zero or more characters followed by `'\n'`,
     * `'\r'`, `"\r\n"` or the end of the reader. The string does
     * not include the newline sequence.
     *
     * @return the contents of the line or `null` if no characters were
     * read before the end of the reader has been reached.
     * @throws IOException
     * if this reader is closed or some other I/O error occurs.
     */
    @Throws(IOException::class)
    open fun readLine(): String? {
        synchronized(lock) {
            checkNotClosed()

            maybeSwallowLF()

            // Do we have a whole line in the buffer?
            for (i in pos until end) {
                val ch = buf!![i]
                if (ch == '\n' || ch == '\r') {
                    val line = String(buf!!, pos, i - pos)
                    pos = i + 1
                    lastWasCR = (ch == '\r')
                    return line
                }
            }

            // Accumulate buffers in a StringBuilder until we've read a whole line.
            val result = StringBuilder(end - pos + 80)
            result.appendRange(buf!!, pos, end)
            while (true) {
                pos = end
                if (fillBuf() == -1) {
                    // If there's no more input, return what we've read so far, if anything.
                    return if (result.length > 0) result.toString() else null
                }

                // Do we have a whole line in the buffer now?
                for (i in pos until end) {
                    val ch = buf!![i]
                    if (ch == '\n' || ch == '\r') {
                        result.appendRange(buf!!, pos, i)
                        pos = i + 1
                        lastWasCR = (ch == '\r')
                        return result.toString()
                    }
                }

                // Add this whole buffer to the line-in-progress and try again...
                result.appendRange(buf!!, pos, end)
            }
        }
    }

    /**
     * Indicates whether this reader is ready to be read without blocking.
     *
     * @return `true` if this reader will not block when `read` is
     * called, `false` if unknown or blocking will occur.
     * @throws IOException
     * if this reader is closed or some other I/O error occurs.
     * @see .read
     * @see .read
     * @see .readLine
     */
    @Throws(IOException::class)
    override fun ready(): Boolean {
        synchronized(lock) {
            checkNotClosed()
            return ((end - pos) > 0) || `in`.ready()
        }
    }

    /**
     * Resets this reader's position to the last `mark()` location.
     * Invocations of `read()` and `skip()` will occur from this new
     * location.
     *
     * @throws IOException
     * if this reader is closed or no mark has been set.
     * @see .mark
     * @see .markSupported
     */
    @Throws(IOException::class)
    override fun reset() {
        synchronized(lock) {
            checkNotClosed()
            if (mark == -1) {
                throw IOException("Invalid mark")
            }
            this.pos = mark
            this.lastWasCR = this.markedLastWasCR
        }
    }

    /**
     * Skips at most `charCount` chars in this stream. Subsequent calls to
     * `read` will not return these chars unless `reset` is
     * used.
     *
     * Skipping characters may invalidate a mark if `markLimit`
     * is surpassed.
     *
     * @return the number of characters actually skipped.
     * @throws IllegalArgumentException if `charCount < 0`.
     * @throws IOException
     * if this reader is closed or some other I/O error occurs.
     */
    @Throws(IOException::class)
    override fun skip(charCount: Long): Long {
        if (charCount < 0) {
            throw IllegalArgumentException("charCount < 0: $charCount")
        }
        synchronized(lock) {
            checkNotClosed()
            if (end - pos >= charCount) {
                pos += charCount.toInt()
                return charCount
            }

            var read = (end - pos).toLong()
            pos = end
            while (read < charCount) {
                if (fillBuf() == -1) {
                    return read
                }
                if (end - pos >= charCount - read) {
                    pos += (charCount - read).toInt()
                    return charCount
                }
                // Couldn't get all the characters, skip what we read
                read += (end - pos).toLong()
                pos = end
            }
            return charCount
        }
    }

    companion object {
        @JvmStatic
        fun checkOffsetAndCount(arrayLength: Int, offset: Int, count: Int) {
            if ((offset or count) < 0 || offset > arrayLength || arrayLength - offset < count) {
                throw ArrayIndexOutOfBoundsException(offset)
            }
        }
    }
}
