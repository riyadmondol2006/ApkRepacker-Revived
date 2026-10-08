package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.JavaScriptLexer
import com.a4455jkjh.lexer.JavaScriptParser
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

class JavascriptLexTask : Antlr4LexTask<JavaScriptLexer>(JavaScriptLexer.VOCABULARY) {
    private val parser: JavaScriptParser
    private var program: JavaScriptParser.ProgramContext? = null

    init {
        parser = JavaScriptParser(null)
    }

    override fun getLanguageType(): String {
        return "Js"
    }

    override fun generateLexer(): JavaScriptLexer {
        return JavaScriptLexer(null)
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: JavaScriptLexer) {
        program = null
        while (!abort) {
            val token = lexer.nextToken()
            val tokenType = token.type
            if (tokenType == -1)
                break
            when (tokenType) {
                JavaScriptLexer.MultiLineComment,
                JavaScriptLexer.SingleLineComment,
                JavaScriptLexer.HtmlComment,
                JavaScriptLexer.CDataComment -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.COMMENT))
                }
                JavaScriptLexer.OpenBracket,
                JavaScriptLexer.OpenParen,
                JavaScriptLexer.OpenBrace,
                JavaScriptLexer.CloseBracket,
                JavaScriptLexer.CloseParen,
                JavaScriptLexer.CloseBrace,
                JavaScriptLexer.SemiColon,
                JavaScriptLexer.Comma -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                JavaScriptLexer.Assign,
                JavaScriptLexer.QuestionMark,
                JavaScriptLexer.Colon,
                JavaScriptLexer.Ellipsis,
                JavaScriptLexer.Dot,
                JavaScriptLexer.PlusPlus,
                JavaScriptLexer.MinusMinus,
                JavaScriptLexer.Plus,
                JavaScriptLexer.Minus,
                JavaScriptLexer.BitNot,
                JavaScriptLexer.Not,
                JavaScriptLexer.Multiply,
                JavaScriptLexer.Divide,
                JavaScriptLexer.Modulus,
                JavaScriptLexer.RightShiftArithmetic,
                JavaScriptLexer.LeftShiftArithmetic,
                JavaScriptLexer.RightShiftLogical,
                JavaScriptLexer.LessThan,
                JavaScriptLexer.MoreThan,
                JavaScriptLexer.LessThanEquals,
                JavaScriptLexer.GreaterThanEquals,
                JavaScriptLexer.Equals_,
                JavaScriptLexer.NotEquals,
                JavaScriptLexer.IdentityEquals,
                JavaScriptLexer.IdentityNotEquals,
                JavaScriptLexer.BitAnd,
                JavaScriptLexer.BitXOr,
                JavaScriptLexer.BitOr,
                JavaScriptLexer.And,
                JavaScriptLexer.Or,
                JavaScriptLexer.MultiplyAssign,
                JavaScriptLexer.DivideAssign,
                JavaScriptLexer.ModulusAssign,
                JavaScriptLexer.PlusAssign,
                JavaScriptLexer.MinusAssign,
                JavaScriptLexer.LeftShiftArithmeticAssign,
                JavaScriptLexer.RightShiftArithmeticAssign,
                JavaScriptLexer.RightShiftLogicalAssign,
                JavaScriptLexer.BitAndAssign,
                JavaScriptLexer.BitXorAssign,
                JavaScriptLexer.BitOrAssign,
                JavaScriptLexer.ARROW -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                JavaScriptLexer.NullLiteral,
                JavaScriptLexer.BooleanLiteral,
                JavaScriptLexer.DecimalLiteral,
                JavaScriptLexer.HexIntegerLiteral,
                JavaScriptLexer.OctalIntegerLiteral,
                JavaScriptLexer.OctalIntegerLiteral2,
                JavaScriptLexer.BinaryIntegerLiteral,
                JavaScriptLexer.StringLiteral,
                JavaScriptLexer.TemplateStringLiteral -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                JavaScriptLexer.Break,
                JavaScriptLexer.Do,
                JavaScriptLexer.Instanceof,
                JavaScriptLexer.Typeof,
                JavaScriptLexer.Case,
                JavaScriptLexer.Else,
                JavaScriptLexer.New,
                JavaScriptLexer.Var,
                JavaScriptLexer.Catch,
                JavaScriptLexer.Finally,
                JavaScriptLexer.Return,
                JavaScriptLexer.Void,
                JavaScriptLexer.Continue,
                JavaScriptLexer.For,
                JavaScriptLexer.Switch,
                JavaScriptLexer.While,
                JavaScriptLexer.Debugger,
                JavaScriptLexer.Function,
                JavaScriptLexer.This,
                JavaScriptLexer.With,
                JavaScriptLexer.Default,
                JavaScriptLexer.If,
                JavaScriptLexer.Throw,
                JavaScriptLexer.Delete,
                JavaScriptLexer.In,
                JavaScriptLexer.Try,
                JavaScriptLexer.Class,
                JavaScriptLexer.Enum,
                JavaScriptLexer.Extends,
                JavaScriptLexer.Super,
                JavaScriptLexer.Const,
                JavaScriptLexer.Export,
                JavaScriptLexer.Import,
                JavaScriptLexer.Implements,
                JavaScriptLexer.Let,
                JavaScriptLexer.Private,
                JavaScriptLexer.Public,
                JavaScriptLexer.Interface,
                JavaScriptLexer.Package,
                JavaScriptLexer.Protected,
                JavaScriptLexer.Static,
                JavaScriptLexer.Yield -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                }
                JavaScriptLexer.UnexpectedCharacter -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.ERROR))
                }
                else -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                }
            }
            // Pair pair = new Pair(token.getStopIndex(), ColorScheme.getColor(type));
            // _tokens.add(pair);
        }
    }

    override fun parse(lexer: JavaScriptLexer) {
        val tks = CommonTokenStream(lexer)
        parser.tokenStream = tks
        program = parser.program()
    }

    override fun getTree(): ParseTree? {
        return program
    }

}
