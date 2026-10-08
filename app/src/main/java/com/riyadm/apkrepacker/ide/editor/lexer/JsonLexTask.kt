package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.JSONLexer
import com.a4455jkjh.lexer.JSONParser
import com.riyadm.codeeditor.util.IndentStringBuilder
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair


import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

class JsonLexTask : Antlr4LexTask<JSONLexer>(NormalLanguage()) {
    private val parser: JSONParser
    private var json: JSONParser.JsonContext? = null

    init {
        parser = JSONParser(null)
        json = null
    }

    override fun getLanguageType(): String {
        return "Json"
    }

    override fun generateLexer(): JSONLexer {
        return JSONLexer(null)
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: JSONLexer) {
        var key = false
        var isArray = false
        json = null
        while (!abort) {
            val token = lexer.nextToken()
            val tokenType = token.type
            if (tokenType == -1)
                break
            when (tokenType) {
                JSONLexer.LBRACE,
                JSONLexer.COMMA -> {
                    key = true
                    // type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                JSONLexer.LBRACK -> {
                    isArray = true
                    //type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                JSONLexer.RBRACE,
                JSONLexer.RBRACK,
                JSONLexer.COLON -> {
                    isArray = false
                    key = false
                    // type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                JSONLexer.TRUE,
                JSONLexer.FALSE,
                JSONLexer.NULL,
                JSONLexer.NUMBER -> {
                    //type = ColorScheme.Colorable.LITERAL;
                    _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                JSONLexer.STRING -> {
                    if (key && !isArray)
                        //  type = ColorScheme.Colorable.KEYWORD;
                        _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                    else
                        //  type = ColorScheme.Colorable.LITERAL;
                        _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                else -> {
                    // type = ColorScheme.Colorable.NAME;
                    _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                }
            }
            //int end = token.getStopIndex();
            // Pair pair = new Pair(end, ColorScheme.getColor(type));
            //  _tokens.add(pair);
        }
    }

    override fun parse(lexer: JSONLexer) {
        val tks = CommonTokenStream(lexer)
        parser.tokenStream = tks
        json = parser.json()
    }


    override fun canFormat(): Boolean {
        return true
    }

    override fun format(sb: IndentStringBuilder, lexer: JSONLexer, width: Int, curPos: Int): Int {
        var newPos = -1
        var start = -1
        while (true) {
            val token = lexer.nextToken() as CommonToken
            val tokenType = token.type
            if (tokenType == -1)
                break
            when (tokenType) {
                JSONLexer.LBRACE -> {
                    sb.indent(width)
                    sb.append("{\n")
                }
                JSONLexer.COMMA -> {
                    sb.append(",\n")
                }
                JSONLexer.LBRACK -> {
                    sb.indent(width)
                    sb.append("[\n")
                }
                JSONLexer.RBRACE -> {
                    sb.deindent(width)
                    sb.append("\n}")
                }
                JSONLexer.RBRACK -> {
                    sb.deindent(width)
                    sb.append("]")
                }
                JSONLexer.COLON -> {
                    sb.append(" : ")
                }
                JSONLexer.WS -> {
                }
                else -> {
                    sb.append(token.text)
                }
            }
            val end = token.stopIndex
            newPos = Antlr4LexTask.compute(sb.length, start, end, curPos, newPos)
            start = end
        }

        return newPos
    }

    override fun getTree(): ParseTree? {
        return json
    }

}
