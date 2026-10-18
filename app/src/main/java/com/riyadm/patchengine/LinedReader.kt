package com.riyadm.patchengine

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader

class LinedReader(input: Reader) : BufferedReader(input) {

    private var mCurrentLine = 0

    /** A line given back with [unread], returned again by the next [readLine]. */
    private var mPending: String? = null

    /**
     * The end tag of the rule being parsed (set by [PatchParser]). When a line that starts another
     * rule comes before it, the rule was not closed: [readLine] returns this tag instead, so the
     * rule ends there and the next rule is parsed on its own (instead of being swallowed and its
     * TARGET / MATCH overwriting the open rule's), and [missingEnd] is told.
     */
    var ruleEnd: String? = null

    var missingEnd: ((line: Int, endTag: String) -> Unit)? = null

    @Throws(IOException::class)
    override fun readLine(): String? {
        mCurrentLine++
        val pending = mPending
        if (pending != null) {
            mPending = null
            return pending
        }
        var line = super.readLine() ?: return null
        // A UTF-8 BOM (Windows editors) would hide the first tag.
        if (mCurrentLine == 1 && line.startsWith('\uFEFF')) {
            line = line.substring(1)
        }
        val end = ruleEnd
        if (end != null && line.trim { it <= ' ' } in RULE_TAGS) {
            ruleEnd = null
            unread(line)
            missingEnd?.invoke(mCurrentLine + 1, end)
            return end
        }
        return line
    }

    /** Gives [line] (the one just read) back: the next [readLine] returns it again. */
    fun unread(line: String) {
        mPending = line
        mCurrentLine--
    }

    val currentLine: Int
        get() = mCurrentLine

    companion object {
        /** The tags that start a rule. */
        @JvmField
        val RULE_TAGS = setOf(
            "[ADD_FILES]", "[REMOVE_FILES]", "[MERGE]", "[MATCH_REPLACE]", "[MATCH_GOTO]",
            "[MATCH_ASSIGN]", "[FUNCTION_REPLACE]", "[SIGNATURE_REVISE]", "[GOTO]", "[DUMMY]",
            "[EXECUTE_DEX]",
        )
    }
}
