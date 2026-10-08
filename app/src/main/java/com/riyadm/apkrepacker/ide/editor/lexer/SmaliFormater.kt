package com.riyadm.apkrepacker.ide.editor.lexer

import com.riyadm.codeeditor.util.IndentStringBuilder

object SmaliFormater {

    @JvmStatic
    fun processStringOrChar(sb: IndentStringBuilder, text: String, isString: Boolean) {
        val chars = text.toCharArray()
        val max = chars.size
        var i = 0
        while (i < max) {
            val c = chars[i]
            if (c == '\\') {
                i += appendChar(sb, chars, i + 1, isString)
                i++
                continue
            }
            sb.append(c)
            i++
        }
    }

    private fun appendChar(sb: IndentStringBuilder, chars: CharArray, i: Int, isString: Boolean): Int {
        val c = chars[i]
        if (c == 'u') {
            val text = String(chars, i + 1, 4)
            val codepoint = Integer.parseInt(text)
            appendChar(sb, codepoint.toChar(), isString)
            return 5
        }
        sb.append('\\')
        sb.append(c)
        return 1
    }

    private fun appendChar(sb: IndentStringBuilder, ch: Char, isString: Boolean) {
        when (ch) {
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\'' -> if (isString)
                sb.append('\'')
            else
                sb.append("\\'")
            '"' -> if (isString)
                sb.append("\\\"")
            else
                sb.append('"')
            '\\' -> sb.append("\\\\")
            else -> sb.append(ch)
        }
    }

    @JvmStatic
    fun processWhiteSpace(sb: IndentStringBuilder, text: String) {
        if (text.contains("\n")) {
            for (c in text.toCharArray())
                if (c == '\n')
                    sb.append(c)
        } else {
            sb.append(" ")
        }
    }
}
