package com.riyadm.apkrepacker.ide.editor.lexer


import com.riyadm.codeeditor.util.LexTask
import com.riyadm.codeeditor.util.NonProgLexTask

import org.antlr.v4.runtime.Vocabulary

import java.io.File
import java.util.ArrayList

object LexerUtil {

    @JvmStatic
    fun isText(type: String): Boolean {
        return type.endsWith("json") ||
                type.endsWith("smali") ||
                type.endsWith("m") ||
                type.endsWith("mm") ||
                type.endsWith("xml") ||
                type.endsWith("html") ||
                type.endsWith("htm") ||
                type.endsWith("txt") ||
                type.endsWith("ini") ||
                type.endsWith("cfg") ||
                type.endsWith("prop") ||
                type.endsWith("js")
    }

    @JvmStatic
    fun getKeywords(tokens: Vocabulary): Array<String> {
        val keywordPattern = "'[a-z_]+'"
        val len = tokens.maxTokenType
        val keywords: MutableList<String> = ArrayList(len)
        for (i in 0 until len) {
            val name = tokens.getLiteralName(i)
            if (name != null && name.matches(Regex(keywordPattern)))
                keywords.add(name.substring(1, name.length - 1))
        }
        return keywords.toTypedArray()
    }

    @JvmStatic
    fun createLexer(name: String, item: File?): LexTask {
        if (name.endsWith(".java"))
            return JavaLexTask()
        if (name.endsWith(".smali"))
            return SmaliLexTask()
        if (name.endsWith(".c") ||
            name.endsWith(".h") ||
            name.endsWith(".cc") ||
            name.endsWith(".cpp") ||
            name.endsWith(".cxx"))
            return CppLexTask()
        if (name.endsWith(".xml"))
            return XmlLexTask()
        if (name.endsWith(".html") ||
            name.endsWith(".htm"))
            return HtmlLexTask()
        if (name.endsWith(".json"))
            return JsonLexTask()
        if (name.endsWith(".css"))
            return CssLexTask()
        if (name.endsWith(".js"))
            return JavascriptLexTask()
        return NonProgLexTask.instance
    }
}
