package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.css3Lexer
import com.a4455jkjh.lexer.css3Parser
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

class CssLexTask : Antlr4LexTask<css3Lexer>(css3Lexer.VOCABULARY) {
    private val parser: css3Parser
    private var unit: css3Parser.StylesheetContext? = null

    init {
        parser = css3Parser(null)
    }

    override fun getLanguageType(): String {
        return "Css"
    }

    override fun generateLexer(): css3Lexer {
        return css3Lexer(null)
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: css3Lexer) {
        unit = null
        while (!abort) {
            val token = lexer.nextToken()
            val tokenType = token.type
            if (tokenType == -1)
                break
            when (tokenType) {
                css3Lexer.Comment -> {
                    // type = ColorScheme.Colorable.COMMENT;
                    _tokens.add(Pair(token.stopIndex, Lexer.COMMENT))
                }
                css3Lexer.LParen,
                css3Lexer.LBrace,
                css3Lexer.LBrack,
                css3Lexer.RParen,
                css3Lexer.RBrace,
                css3Lexer.Colon,
                css3Lexer.Comma,
                css3Lexer.Semi,
                css3Lexer.Dot,
                css3Lexer.UnderScroll -> {
                    // type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                css3Lexer.Number,
                css3Lexer.String,
                css3Lexer.Uri,
                css3Lexer.Dimension,
                css3Lexer.Percentage,
                css3Lexer.Hash -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                css3Lexer.MediaOnly,
                css3Lexer.Not,
                css3Lexer.And,
                css3Lexer.PseudoNot,
                css3Lexer.Or,
                css3Lexer.FontFace,
                css3Lexer.Supports,
                css3Lexer.Keyframes,
                css3Lexer.From,
                css3Lexer.To,
                css3Lexer.Viewport,
                css3Lexer.CounterStyle,
                css3Lexer.FontFeatureValues,
                css3Lexer.Media,
                css3Lexer.Import,
                css3Lexer.Page,
                css3Lexer.Namespace,
                css3Lexer.Charset -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                }
                css3Lexer.Calc,
                css3Lexer.DxImageTransform,
                css3Lexer.Var,
                css3Lexer.Function -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                }
                css3Lexer.Plus,
                css3Lexer.Minus,
                css3Lexer.Greater,
                css3Lexer.Tilde,
                css3Lexer.PrefixMatch,
                css3Lexer.SuffixMatch,
                css3Lexer.SubstringMatch,
                css3Lexer.Star,
                css3Lexer.BitOr,
                css3Lexer.Cdo,
                css3Lexer.Cdc,
                css3Lexer.Includes,
                css3Lexer.DashMatch -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                else -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                }
            }
            // Pair pair = new Pair(token.getStopIndex(), ColorScheme.getColor(type));
            // _tokens.add(pair);
        }
    }

    override fun parse(lexer: css3Lexer) {
        val tks = CommonTokenStream(lexer)
        parser.tokenStream = tks
        unit = parser.stylesheet()
    }

    override fun getTree(): ParseTree? {
        return unit
    }

}
