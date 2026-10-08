package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.CPP14Lexer
import com.a4455jkjh.lexer.CPP14Parser
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

class CppLexTask : Antlr4LexTask<CPP14Lexer>(CPP14Lexer.VOCABULARY) {
    private val parser: CPP14Parser
    private var unit: CPP14Parser.TranslationunitContext? = null

    init {
        parser = CPP14Parser(null)
    }

    override fun getLanguageType(): String {
        return "C++"
    }

    override fun generateLexer(): CPP14Lexer {
        return CPP14Lexer(null)
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: CPP14Lexer) {
        unit = null
        while (!abort) {
            val token = lexer.nextToken()
            val tokenType = token.type
            if (tokenType == -1)
                break
            when (tokenType) {
                CPP14Lexer.Alignas,
                CPP14Lexer.Alignof,
                CPP14Lexer.Asm,
                CPP14Lexer.Auto,
                CPP14Lexer.Break,
                CPP14Lexer.Class,
                CPP14Lexer.Const,
                CPP14Lexer.Catch,
                CPP14Lexer.Case,
                CPP14Lexer.Constexpr,
                CPP14Lexer.Const_cast,
                CPP14Lexer.Continue,
                CPP14Lexer.Decltype,
                CPP14Lexer.Default,
                CPP14Lexer.Delete,
                CPP14Lexer.Do,
                CPP14Lexer.Dynamic_cast,
                CPP14Lexer.Else,
                CPP14Lexer.Enum,
                CPP14Lexer.Explicit,
                CPP14Lexer.Export,
                CPP14Lexer.Extern,
                CPP14Lexer.Final,
                CPP14Lexer.For,
                CPP14Lexer.Friend,
                CPP14Lexer.Goto,
                CPP14Lexer.If,
                CPP14Lexer.Inline,
                CPP14Lexer.Mutable,
                CPP14Lexer.Namespace,
                CPP14Lexer.New,
                CPP14Lexer.Noexcept,
                CPP14Lexer.Operator,
                CPP14Lexer.Override,
                CPP14Lexer.Private,
                CPP14Lexer.Protected,
                CPP14Lexer.Public,
                CPP14Lexer.Register,
                CPP14Lexer.Reinterpret_cast,
                CPP14Lexer.Return,
                CPP14Lexer.Sizeof,
                CPP14Lexer.Signed,
                CPP14Lexer.Static,
                CPP14Lexer.Static_cast,
                CPP14Lexer.Static_assert,
                CPP14Lexer.Struct,
                CPP14Lexer.Switch,
                CPP14Lexer.Template,
                CPP14Lexer.This,
                CPP14Lexer.Throw,
                CPP14Lexer.Thread_local,
                CPP14Lexer.Try,
                CPP14Lexer.Typedef,
                CPP14Lexer.Typeid,
                CPP14Lexer.Typename,
                CPP14Lexer.Unsigned,
                CPP14Lexer.Union,
                CPP14Lexer.Using,
                CPP14Lexer.Virtual,
                CPP14Lexer.Volatile,
                CPP14Lexer.While -> {
                    //type = ColorScheme.Colorable.KEYWORD;
                    _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                }
                CPP14Lexer.Bool,
                CPP14Lexer.Char,
                CPP14Lexer.Char16,
                CPP14Lexer.Char32,
                CPP14Lexer.Double,
                CPP14Lexer.Float,
                CPP14Lexer.Int,
                CPP14Lexer.Long,
                CPP14Lexer.Short,
                CPP14Lexer.Wchar -> {
                    // type = ColorScheme.Colorable.TYPE;
                    _tokens.add(Pair(token.stopIndex, Lexer.TYPE))
                }
                CPP14Lexer.LeftParen,
                CPP14Lexer.LeftBracket,
                CPP14Lexer.LeftBrace,
                CPP14Lexer.Dot,
                CPP14Lexer.RightParen,
                CPP14Lexer.RightBracket,
                CPP14Lexer.RightBrace,
                CPP14Lexer.Semi,
                CPP14Lexer.Comma -> {
                    //type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                CPP14Lexer.Plus,
                CPP14Lexer.Minus,
                CPP14Lexer.Star,
                CPP14Lexer.Div,
                CPP14Lexer.Mod,
                CPP14Lexer.Caret,
                CPP14Lexer.And,
                CPP14Lexer.Or,
                CPP14Lexer.Tilde,
                CPP14Lexer.Not,
                CPP14Lexer.Assign,
                CPP14Lexer.Less,
                CPP14Lexer.Greater,
                CPP14Lexer.PlusAssign,
                CPP14Lexer.MinusAssign,
                CPP14Lexer.StarAssign,
                CPP14Lexer.DivAssign,
                CPP14Lexer.ModAssign,
                CPP14Lexer.XorAssign,
                CPP14Lexer.AndAssign,
                CPP14Lexer.OrAssign,
                CPP14Lexer.LeftShift,
                CPP14Lexer.LeftShiftAssign,
                CPP14Lexer.Equal,
                CPP14Lexer.NotEqual,
                CPP14Lexer.LessEqual,
                CPP14Lexer.GreaterEqual,
                CPP14Lexer.AndAnd,
                CPP14Lexer.OrOr,
                CPP14Lexer.PlusPlus,
                CPP14Lexer.MinusMinus,
                CPP14Lexer.Arrow,
                CPP14Lexer.ArrowStar,
                CPP14Lexer.Question,
                CPP14Lexer.Colon,
                CPP14Lexer.Doublecolon,
                CPP14Lexer.DotStar,
                CPP14Lexer.Ellipsis -> {
                    // type = ColorScheme.Colorable.OPERATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                CPP14Lexer.Integerliteral,
                CPP14Lexer.Decimalliteral,
                CPP14Lexer.Octalliteral,
                CPP14Lexer.Hexadecimalliteral,
                CPP14Lexer.Binaryliteral,
                CPP14Lexer.Integersuffix,
                CPP14Lexer.Characterliteral,
                CPP14Lexer.Floatingliteral,
                CPP14Lexer.Stringliteral,
                CPP14Lexer.True,
                CPP14Lexer.False,
                CPP14Lexer.Nullptr -> {
                    //type = ColorScheme.Colorable.LITERAL;
                    _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                CPP14Lexer.BlockComment,
                CPP14Lexer.LineComment -> {
                    //type = ColorScheme.Colorable.COMMENT;
                    _tokens.add(Pair(token.stopIndex, Lexer.COMMENT))
                }
                CPP14Lexer.MultiLineMacro,
                CPP14Lexer.Directive -> {
                    //type = ColorScheme.Colorable.PACKAGE;
                    _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                }
                else -> {
                    //type = ColorScheme.Colorable.NAME;
                    _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                }
            }
            // Pair pair = new Pair(token.getStopIndex(), ColorScheme.getColor(type));
            // _tokens.add(pair);
        }
    }

    override fun parse(lexer: CPP14Lexer) {
        val tks = CommonTokenStream(lexer)
        parser.tokenStream = tks
        unit = parser.translationunit()
    }

    override fun getTree(): ParseTree? {
        return unit
    }

}
