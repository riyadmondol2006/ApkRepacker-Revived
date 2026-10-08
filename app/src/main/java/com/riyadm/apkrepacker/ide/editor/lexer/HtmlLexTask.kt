package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.XMLLexer
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Recognizer

class HtmlLexTask : XmlLexTask() {
    init {
        // super(item);
    }

    override fun getLanguageType(): String {
        return "Html"
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: XMLLexer) {
        var lastType = 0
        var lastTag: String? = null
        while (!abort) {
            val start = lexer.charIndex
            val token = lexer.nextToken()
            val tokenType = token.type
            if (tokenType == Recognizer.EOF)
                break
            when (tokenType) {
                XMLLexer.XMLDeclOpen -> {
                    parse(lexer, XMLLexer.SPECIAL_CLOSE)
                    _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                }
                XMLLexer.DTD -> _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                XMLLexer.COMMENT -> _tokens.add(Pair(token.stopIndex, Lexer.COMMENT))
                XMLLexer.OPEN,
                XMLLexer.CLOSE,
                XMLLexer.SLASH_CLOSE,
                XMLLexer.SLASH -> _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                XMLLexer.Name -> {
                    if (lastType == XMLLexer.OPEN) {
                        _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                        lastTag = token.text
                    } else if (lastType == XMLLexer.SLASH) {
                        _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                        lastTag = null
                    } else
                        _tokens.add(Pair(token.stopIndex, Lexer.TYPE))
                }
                XMLLexer.STRING -> _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                XMLLexer.EQUALS -> _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                XMLLexer.TEXT -> _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                else -> _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
            }
            lastType = tokenType
            //Pair pair = new Pair(lexer.getCharIndex() - start, ColorScheme.getColor(type));
            // _tokens.add(pair);
        }
        lexer.reset()
        val tokens = CommonTokenStream(lexer)
        parser.tokenStream = tokens
        document = parser.document()
    }
}
