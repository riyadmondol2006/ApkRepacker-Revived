package com.riyadm.apkrepacker.ide.editor.lexer

import android.util.Log

import com.a4455jkjh.lexer.XMLLexer
import com.a4455jkjh.lexer.XMLParser
import com.a4455jkjh.lexer.XMLParserBaseVisitor
import com.riyadm.codeeditor.lang.Language
import com.riyadm.codeeditor.util.IndentStringBuilder
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

import java.io.File

open class XmlLexTask : Antlr4LexTask<XMLLexer>(XMLLexer.VOCABULARY) {
    @JvmField
    protected val parser: XMLParser
    private val lexer: XMLLexer
    @JvmField
    protected var document: XMLParser.DocumentContext? = null

    private val item: File? = null

    init {
        // this.item = item;
        parser = XMLParser(null)
        lexer = XMLLexer(null)
        if (item != null) {
            // parser.removeErrorListeners();
            // parser.addErrorListener(item);
        }
        document = null
    }

    override fun getLanguageType(): String {
        return "Xml"
    }

    override fun getTree(): ParseTree? {
        return document
    }

    override fun generateLexer(): XMLLexer {
        val xMLLexer = XMLLexer(null)
        if (item != null) {
            //  xMLLexer.removeErrorListeners();
            //   xMLLexer.addErrorListener(item);
        }
        return xMLLexer
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: XMLLexer) {
        var lastType = 0
        document = null
        while (!abort) {
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
                    if (lastType == XMLLexer.OPEN || lastType == XMLLexer.SLASH)
                        _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                    else
                        _tokens.add(Pair(token.stopIndex, Lexer.TYPE))
                }
                XMLLexer.STRING -> _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                XMLLexer.EQUALS -> _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                else -> _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
            }
            lastType = tokenType
            //  Pair pair = new Pair(token.getStopIndex(), ColorScheme.getColor(type));
            //   _tokens.add(pair);
        }
    }

    override fun parse(lexer: XMLLexer) {
        Log.i("APKTOOL PARSE", "parse xml")
        //item.reset();
        val tokens = CommonTokenStream(lexer)
        parser.tokenStream = tokens
        document = parser.document()
    }

    override fun canAnalysis(): Boolean {
        return item != null
    }


    override fun canFormat(): Boolean {
        return true
    }

    override fun parse(i: ANTLRInputStream) {
        lexer.setInputStream(i)
        parse(lexer)
    }

    protected fun parse(lexer: XMLLexer, endType: Int) {
        while (true) {
            if (lexer.nextToken().type == endType)
                break
        }
    }

    override fun format(sb: IndentStringBuilder, lexer: XMLLexer, width: Int, curPos: Int): Int {
        val tokens = CommonTokenStream(lexer)
        parser.tokenStream = tokens
        val f = XMLFormatter(sb, width, curPos)
        parser.document().accept(f)
        return f.newPos
    }

    private class LanguageXml : Language() {
        init {
            keywords = arrayOf<String>()
        }
    }

    private inner class XMLFormatter(
        private val sb: IndentStringBuilder,
        private val width: Int,
        private val curPos: Int
    ) : XMLParserBaseVisitor<Void?>() {
        var newPos: Int = curPos
        private var shouldNewLine = false

        override fun visitElement(ctx: XMLParser.ElementContext): Void? {
            sb.append('<')
            sb.indent(width)
            ctx.Name(0).accept(this)
            val attrs = ctx.attribute()
            shouldNewLine = attrs.size > 1
            for (attr in attrs)
                attr.accept(this)
            val content = ctx.content()
            if (content == null) {
                sb.append("/>")
                sb.deindent(width)
            } else {
                sb.append('>')
                content.accept(this)
                sb.deindent(width)
                sb.append("</")
                visitTerminal(ctx.Name(1))
                //ctx.Name(1).accept(this);
                sb.append('>')
            }
            return null
        }

        override fun visitAttribute(ctx: XMLParser.AttributeContext): Void? {
            sb.append(if (shouldNewLine) '\n' else ' ')
            ctx.Name().accept(this)
            sb.append('=')
            ctx.STRING().accept(this)
            return null
        }

        override fun visitTerminal(node: TerminalNode?): Void? {
            if (node == null)
                return null
            val token = node.symbol
            val text = token.text
            val type = token.type
            when (type) {
                XMLLexer.SEA_WS -> SmaliFormater.processWhiteSpace(sb, text)
                XMLLexer.SPECIAL_CLOSE -> {
                    sb.append(' ')
                    sb.append(text)
                }
                else -> sb.append(text)
            }
            val start = token.startIndex
            val end = token.stopIndex
            newPos = Antlr4LexTask.compute(sb.length, start, end, curPos, newPos)
            return null
        }
    }

}
