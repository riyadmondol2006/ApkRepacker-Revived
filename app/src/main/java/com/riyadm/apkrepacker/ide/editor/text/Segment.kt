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

package com.riyadm.apkrepacker.ide.editor.text

import java.text.CharacterIterator

/**
 * A segment of a character array representing a fragment
 * of text.  It should be treated as immutable even though
 * the array is directly accessible.  This gives fast access
 * to fragments of text without the overhead of copying
 * around characters.  This is effectively an unprotected
 * String.
 *
 * The Segment implements the java.text.CharacterIterator
 * interface to support use with the i18n support without
 * copying text into a string.
 *
 * @author Timothy Prinzing
 */
open class Segment
/**
 * Creates a new segment referring to an existing array.
 *
 * @param array  the array to refer to
 * @param offset the offset into the array
 * @param count  the number of characters
 */(
    /**
     * This is the array containing the text of
     * interest.  This array should never be modified;
     * it is available only for efficiency.
     */
    @JvmField var array: CharArray?,
    /**
     * This is the offset into the array that
     * the desired text begins.
     */
    @JvmField var offset: Int,
    /**
     * This is the number of array elements that
     * make up the text of interest.
     */
    @JvmField var count: Int
) : Cloneable, CharacterIterator, CharSequence {

    /**
     * Flag to indicate that partial returns are valid.  If the flag is true,
     * an implementation of the interface method Document.getText(position,length,Segment)
     * should return as much text as possible without making a copy.  The default
     * state of the flag is false which will cause Document.getText(position,length,Segment)
     * to provide the same return behavior it always had, which may or may not
     * make a copy of the text depending upon the request.
     *
     * @since 1.4
     */
    var isPartialReturn: Boolean = false
    private var pos = 0

    /**
     * Creates a new segment.
     */
    constructor() : this(null, 0, 0)

    // --- CharacterIterator methods -------------------------------------

    /**
     * Converts a segment into a String.
     *
     * @return the string
     */
    override fun toString(): String {
        if (array != null) {
            return String(array!!, offset, count)
        }
        return ""
    }

    /**
     * Sets the position to getBeginIndex() and returns the character at that
     * position.
     *
     * @return the first character in the text, or DONE if the text is empty
     * @see .getBeginIndex
     * @since 1.3
     */
    override fun first(): Char {
        pos = offset
        if (count != 0) {
            return array!![pos]
        }
        return CharacterIterator.DONE
    }

    /**
     * Sets the position to getEndIndex()-1 (getEndIndex() if the text is empty)
     * and returns the character at that position.
     *
     * @return the last character in the text, or DONE if the text is empty
     * @see .getEndIndex
     * @since 1.3
     */
    override fun last(): Char {
        pos = offset + count
        if (count != 0) {
            pos -= 1
            return array!![pos]
        }
        return CharacterIterator.DONE
    }

    /**
     * Gets the character at the current position (as returned by getIndex()).
     *
     * @return the character at the current position or DONE if the current
     * position is off the end of the text.
     * @see .getIndex
     * @since 1.3
     */
    override fun current(): Char {
        if (count != 0 && pos < offset + count) {
            return array!![pos]
        }
        return CharacterIterator.DONE
    }

    /**
     * Increments the iterator's index by one and returns the character
     * at the new index.  If the resulting index is greater or equal
     * to getEndIndex(), the current index is reset to getEndIndex() and
     * a value of DONE is returned.
     *
     * @return the character at the new position or DONE if the new
     * position is off the end of the text range.
     * @since 1.3
     */
    override fun next(): Char {
        pos += 1
        val end = offset + count
        if (pos >= end) {
            pos = end
            return CharacterIterator.DONE
        }
        return current()
    }

    /**
     * Decrements the iterator's index by one and returns the character
     * at the new index. If the current index is getBeginIndex(), the index
     * remains at getBeginIndex() and a value of DONE is returned.
     *
     * @return the character at the new position or DONE if the current
     * position is equal to getBeginIndex().
     * @since 1.3
     */
    override fun previous(): Char {
        if (pos == offset) {
            return CharacterIterator.DONE
        }
        pos -= 1
        return current()
    }

    /**
     * Sets the position to the specified position in the text and returns that
     * character.
     *
     * @param position the position within the text.  Valid values range from
     * getBeginIndex() to getEndIndex().  An IllegalArgumentException is thrown
     * if an invalid value is supplied.
     * @return the character at the specified position or DONE if the specified position is equal to getEndIndex()
     * @since 1.3
     */
    override fun setIndex(position: Int): Char {
        val end = offset + count
        if ((position < offset) || (position > end)) {
            throw IllegalArgumentException("bad position: $position")
        }
        pos = position
        if ((pos != end) && (count != 0)) {
            return array!![pos]
        }
        return CharacterIterator.DONE
    }

    /**
     * Returns the start index of the text.
     *
     * @return the index at which the text begins.
     * @since 1.3
     */
    override fun getBeginIndex(): Int {
        return offset
    }

    /**
     * Returns the end index of the text.  This index is the index of the first
     * character following the end of the text.
     *
     * @return the index after the last character in the text
     * @since 1.3
     */
    override fun getEndIndex(): Int {
        return offset + count
    }

    // --- CharSequence methods -------------------------------------

    /**
     * Returns the current index.
     *
     * @return the current index.
     * @since 1.3
     */
    override fun getIndex(): Int {
        return pos
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.6
     */
    override fun get(index: Int): Char {
        if (index < 0
            || index >= count
        ) {
            throw StringIndexOutOfBoundsException(index)
        }
        return array!![offset + index]
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.6
     */
    override val length: Int
        get() = count

    /**
     * {@inheritDoc}
     *
     * @since 1.6
     */
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0) {
            throw StringIndexOutOfBoundsException(startIndex)
        }
        if (endIndex > count) {
            throw StringIndexOutOfBoundsException(endIndex)
        }
        if (startIndex > endIndex) {
            throw StringIndexOutOfBoundsException(endIndex - startIndex)
        }
        val segment = Segment()
        segment.array = this.array
        segment.offset = this.offset + startIndex
        segment.count = endIndex - startIndex
        return segment
    }

    /**
     * Creates a shallow copy.
     *
     * @return the copy
     */
    public override fun clone(): Any {
        // Segment implements Cloneable, so super.clone() never throws CloneNotSupportedException
        return super<Cloneable>.clone()
    }


}
