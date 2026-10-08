package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.JavaLexer
import com.a4455jkjh.lexer.JavaParser
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

class JavaLexTask : Antlr4LexTask<JavaLexer>(JavaLexer.VOCABULARY) {
    private val parser: JavaParser
    private var unit: JavaParser.CompilationUnitContext? = null

    init {
        parser = JavaParser(null)
    }

    override fun getLanguageType(): String {
        return "Java"
    }

    override fun generateLexer(): JavaLexer {
        return JavaLexer(null)
    }

    override fun tokenize(_tokens: MutableList<Pair>, lexer: JavaLexer) {
        var imp = false
        unit = null
        while (!abort) {
            val token = lexer.nextToken()
            val tokenType = token.type
            if (tokenType == -1)
                break
            when (tokenType) {
                JavaLexer.PUBLIC,
                JavaLexer.ABSTRACT,
                JavaLexer.ASSERT,
                JavaLexer.BREAK,
                JavaLexer.CASE,
                JavaLexer.CATCH,
                JavaLexer.CONTINUE,
                JavaLexer.CLASS,
                JavaLexer.CONST,
                JavaLexer.DEFAULT,
                JavaLexer.DO,
                JavaLexer.ELSE,
                JavaLexer.ENUM,
                JavaLexer.EXTENDS,
                JavaLexer.FINAL,
                JavaLexer.FINALLY,
                JavaLexer.FOR,
                JavaLexer.GOTO,
                JavaLexer.IF,
                JavaLexer.IMPLEMENTS,
                JavaLexer.INSTANCEOF,
                JavaLexer.INTERFACE,
                JavaLexer.NATIVE,
                JavaLexer.NEW,
                JavaLexer.PRIVATE,
                JavaLexer.PROTECTED,
                JavaLexer.RETURN,
                JavaLexer.STATIC,
                JavaLexer.STRICTFP,
                JavaLexer.SUPER,
                JavaLexer.SWITCH,
                JavaLexer.SYNCHRONIZED,
                JavaLexer.THIS,
                JavaLexer.THROW,
                JavaLexer.THROWS,
                JavaLexer.TRANSIENT,
                JavaLexer.TRY,
                JavaLexer.VOLATILE,
                JavaLexer.WHILE -> {
                    // type = ColorScheme.Colorable.KEYWORD;
                    _tokens.add(Pair(token.stopIndex, Lexer.TYPE))
                }
                JavaLexer.IMPORT,
                JavaLexer.PACKAGE -> {
                    //type = ColorScheme.Colorable.KEYWORD;
                    _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                    imp = true
                }
                JavaLexer.BINARY_LITERAL,
                JavaLexer.DECIMAL_LITERAL,
                JavaLexer.HEX_FLOAT_LITERAL,
                JavaLexer.HEX_LITERAL,
                JavaLexer.FLOAT_LITERAL,
                JavaLexer.OCT_LITERAL,
                JavaLexer.STRING_LITERAL,
                JavaLexer.CHAR_LITERAL,
                JavaLexer.BOOL_LITERAL,
                JavaLexer.NULL_LITERAL -> {
                    //  type = ColorScheme.Colorable.LITERAL;
                    _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                JavaLexer.BOOLEAN,
                JavaLexer.BYTE,
                JavaLexer.CHAR,
                JavaLexer.FLOAT,
                JavaLexer.DOUBLE,
                JavaLexer.INT,
                JavaLexer.LONG,
                JavaLexer.SHORT,
                JavaLexer.VOID -> {
                    //type = ColorScheme.Colorable.TYPE;
                    _tokens.add(Pair(token.stopIndex, Lexer.TYPE))
                }
                JavaLexer.SEMI -> {
                    // type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                    imp = false
                }
                JavaLexer.ARROW,
                JavaLexer.COLONCOLON,
                JavaLexer.ELLIPSIS,
                JavaLexer.ASSIGN,
                JavaLexer.GT,
                JavaLexer.LT,
                JavaLexer.BANG,
                JavaLexer.TILDE,
                JavaLexer.QUESTION,
                JavaLexer.COLON,
                JavaLexer.EQUAL,
                JavaLexer.LE,
                JavaLexer.GE,
                JavaLexer.NOTEQUAL,
                JavaLexer.AND,
                JavaLexer.OR,
                JavaLexer.INC,
                JavaLexer.DEC,
                JavaLexer.ADD,
                JavaLexer.SUB,
                JavaLexer.DIV,
                JavaLexer.BITOR,
                JavaLexer.BITAND,
                JavaLexer.CARET,
                JavaLexer.MOD,
                JavaLexer.ADD_ASSIGN,
                JavaLexer.SUB_ASSIGN,
                JavaLexer.MUL_ASSIGN,
                JavaLexer.DIV_ASSIGN,
                JavaLexer.AND_ASSIGN,
                JavaLexer.OR_ASSIGN,
                JavaLexer.XOR_ASSIGN,
                JavaLexer.MOD_ASSIGN,
                JavaLexer.LSHIFT_ASSIGN,
                JavaLexer.RSHIFT_ASSIGN,
                JavaLexer.URSHIFT_ASSIGN,
                JavaLexer.AT -> {
                    //type = ColorScheme.Colorable.OPERATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                JavaLexer.LPAREN,
                JavaLexer.RPAREN,
                JavaLexer.LBRACE,
                JavaLexer.RBRACE,
                JavaLexer.LBRACK,
                JavaLexer.RBRACK,
                JavaLexer.COMMA,
                JavaLexer.DOT -> {
                    // type = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                JavaLexer.MUL -> {
                    if (imp)
                        //type = ColorScheme.Colorable.PACKAGE;
                        _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                    else
                        //type = ColorScheme.Colorable.OPERATOR;
                        _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                JavaLexer.IDENTIFIER -> {
                    if (imp)
                        // type = ColorScheme.Colorable.PACKAGE;
                        _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                    else
                        //type = ColorScheme.Colorable.NAME;
                        _tokens.add(Pair(token.stopIndex, Lexer.NAME))
                }
                JavaLexer.LINE_COMMENT,
                JavaLexer.COMMENT -> {
                    //type = ColorScheme.Colorable.COMMENT;
                    _tokens.add(Pair(token.stopIndex, Lexer.COMMENT))
                }
                0 -> {
                    //type = ColorScheme.Colorable.ERROR;
                    _tokens.add(Pair(token.stopIndex, Lexer.ERROR))
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

    override fun parse(lexer: JavaLexer) {
        val tks = CommonTokenStream(lexer)
        parser.tokenStream = tks
        unit = parser.compilationUnit()
    }

    override fun getTree(): ParseTree? {
        return unit
    }

}
