package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.SmaliLexer
import com.a4455jkjh.lexer.SmaliParser
import com.riyadm.codeeditor.lang.Language
import com.riyadm.codeeditor.util.IndentStringBuilder
import com.riyadm.codeeditor.util.Lexer
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes

import java.util.ArrayList
import java.util.Locale

class SmaliLexTask : Antlr4LexTask<SmaliLexer>(LanguageSmali()) {
    private val parser: SmaliParser
    private val tks: CommonTokenStream
    private val lexer: SmaliLexer
    //private final File item;
    private var smali: SmaliParser.SmaliContext? = null
    private var hasMethodHandle = false
    // private ArrayList<Pair> _tokens;

    init {
        //this.item = item;
        parser = SmaliParser(null)
        lexer = SmaliLexer(null)
        tks = CommonTokenStream(lexer)

        smali = null
    }

    override fun getTree(): ParseTree? {
        return smali
    }


    override fun canAnalysis(): Boolean {
        return true
    }

    override fun parse(i: ANTLRInputStream) {
        lexer.setInputStream(i)
        parse(lexer)
    }

    override fun parse(lexer: SmaliLexer) {
        tks.tokenSource = lexer
        parser.tokenStream = tks
        val ctx = parser.smali()
        smali = ctx

    }

    val codes: Opcodes
        get() = Opcodes.forApi(if (hasMethodHandle) 26 else 14)

    override fun getLanguageType(): String {
        return "Smali"
    }

    override fun generateLexer(): SmaliLexer {
        val smaliLexer = SmaliLexer(null)

        return smaliLexer
    }

    public override fun tokenize(_tokens: MutableList<Pair>, lexer: SmaliLexer) {
        var isLabel = false
        ///if (item != null) {
        //   item.reset();
        // }
        var hasMethodHandle = false
        while (!abort) {
            val token = lexer.nextToken()
            val type = token.type
            if (type == Recognizer.EOF)
                break
            // ColorScheme.Colorable type1;
            when (type) {
                SmaliLexer.ANNOTATION_DIRECTIVE,
                SmaliLexer.ARRAY_DATA_DIRECTIVE,
                SmaliLexer.CATCHALL_DIRECTIVE,
                SmaliLexer.CATCH_DIRECTIVE,
                SmaliLexer.CLASS_DIRECTIVE,
                SmaliLexer.END_ANNOTATION_DIRECTIVE,
                SmaliLexer.END_ARRAY_DATA_DIRECTIVE,
                SmaliLexer.END_FIELD_DIRECTIVE,
                SmaliLexer.END_LOCAL_DIRECTIVE,
                SmaliLexer.END_PARAMETER_DIRECTIVE,
                SmaliLexer.END_METHOD_DIRECTIVE,
                SmaliLexer.END_PACKED_SWITCH_DIRECTIVE,
                SmaliLexer.END_SPARSE_SWITCH_DIRECTIVE,
                SmaliLexer.END_SUBANNOTATION_DIRECTIVE,
                SmaliLexer.ENUM_DIRECTIVE,
                SmaliLexer.EPILOGUE_DIRECTIVE -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                }
                SmaliLexer.FIELD_DIRECTIVE -> {
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                SmaliLexer.IMPLEMENTS_DIRECTIVE,
                SmaliLexer.LINE_DIRECTIVE,
                SmaliLexer.LOCAL_DIRECTIVE,
                SmaliLexer.LOCALS_DIRECTIVE,
                SmaliLexer.METHOD_DIRECTIVE,
                SmaliLexer.PACKED_SWITCH_DIRECTIVE,
                SmaliLexer.PARAMETER_DIRECTIVE,
                SmaliLexer.PROLOGUE_DIRECTIVE,
                SmaliLexer.REGISTERS_DIRECTIVE,
                SmaliLexer.RESTART_LOCAL_DIRECTIVE,
                SmaliLexer.SOURCE_DIRECTIVE,
                SmaliLexer.SPARSE_SWITCH_DIRECTIVE,
                SmaliLexer.SUBANNOTATION_DIRECTIVE,
                SmaliLexer.SUPER_DIRECTIVE -> {
                    //   type1 = ColorScheme.Colorable.PACKAGE;
                    _tokens.add(Pair(token.stopIndex, Lexer.PACKAGE))
                }
                SmaliLexer.ACCESS_SPEC,
                SmaliLexer.ANNOTATION_VISIBILITY -> {
                    // type1 = ColorScheme.Colorable.KEYWORD;
                    _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                }
                SmaliLexer.CLASS_DESCRIPTOR,
                SmaliLexer.VOID_TYPE,
                SmaliLexer.PRIMITIVE_TYPE,
                SmaliLexer.PRIMITIVE_LIST,
                SmaliLexer.TYPE_LIST,
                SmaliLexer.ARRAY_DESCRIPTOR -> {
                    //  type1 = ColorScheme.Colorable.TYPE;
                    _tokens.add(Pair(token.stopIndex, Lexer.TYPE))
                }
                SmaliLexer.LINE_COMMENT -> {
                    // type1 = ColorScheme.Colorable.COMMENT;
                    _tokens.add(Pair(token.stopIndex, Lexer.COMMENT))
                }
                SmaliLexer.INSTRUCTION_FORMAT3rc_CALL_SITE,
                SmaliLexer.INSTRUCTION_FORMAT35c_CALL_SITE -> {
                    hasMethodHandle = true
                    // type1 = ColorScheme.Colorable.KEYWORD;
                    _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                }
                SmaliLexer.INVALID_TOKEN -> {
                    //  type1 = ColorScheme.Colorable.ERROR;
                    _tokens.add(Pair(token.stopIndex, Lexer.ERROR))
                }
                SmaliLexer.BOOL_LITERAL,
                SmaliLexer.BYTE_LITERAL,
                SmaliLexer.CHAR_LITERAL,
                SmaliLexer.DOUBLE_LITERAL,
                SmaliLexer.DOUBLE_LITERAL_OR_ID,
                SmaliLexer.FLOAT_LITERAL_OR_ID,
                SmaliLexer.FLOAT_LITERAL,
                SmaliLexer.LONG_LITERAL,
                SmaliLexer.NULL_LITERAL,
                SmaliLexer.POSITIVE_INTEGER_LITERAL,
                SmaliLexer.NEGATIVE_INTEGER_LITERAL,
                SmaliLexer.SHORT_LITERAL,
                SmaliLexer.STRING_LITERAL -> {
                    //type1 = ColorScheme.Colorable.LITERAL;
                    _tokens.add(Pair(token.stopIndex, Lexer.LITERAL))
                }
                SmaliLexer.ARROW,
                SmaliLexer.AT,
                SmaliLexer.COMMA,
                SmaliLexer.DOTDOT,
                SmaliLexer.EQUAL,
                SmaliLexer.REGISTER -> {
                    //type1 = ColorScheme.Colorable.OPERATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                SmaliLexer.OPEN_BRACE,
                SmaliLexer.OPEN_PAREN,
                SmaliLexer.CLOSE_BRACE,
                SmaliLexer.CLOSE_PAREN -> {
                    //type1 = ColorScheme.Colorable.SEPARATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.SEPARATOR))
                }
                SmaliLexer.COLON -> {
                    isLabel = true
                    // type1 = ColorScheme.Colorable.OPERATOR;
                    _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                }
                SmaliLexer.SIMPLE_NAME -> {
                    if (isLabel) {
                        // type1 = ColorScheme.Colorable.OPERATOR;
                        _tokens.add(Pair(token.stopIndex, Lexer.OPERATOR))
                    } else {
                        // type1 = ColorScheme.Colorable.NAME;
                        _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                        isLabel = false
                    }
                }
                else -> {
                    // case WHITE_SPACE falls through to default
                    if (type == SmaliLexer.WHITE_SPACE)
                        isLabel = false
                    if (type >= SmaliLexer.INSTRUCTION_FORMAT10t && type <= SmaliLexer.INSTRUCTION_FORMAT51l) {
                        // type1 = ColorScheme.Colorable.KEYWORD;
                        _tokens.add(Pair(token.stopIndex, Lexer.KEYWORD))
                    } else {
                        //type1 = ColorScheme.Colorable.NAME;
                        _tokens.add(Pair(token.stopIndex, Lexer.NORMAL))
                    }
                }
            }
            //    Pair pair = new Pair(token.getStopIndex(), ColorScheme.getColor(type1));
            // _tokens.add(new Pair(token.getStopIndex(), ColorScheme.getColor(type1)));
        }
        this.hasMethodHandle = hasMethodHandle
    }

    override fun canFormat(): Boolean {
        return true
    }

    public override fun format(sb: IndentStringBuilder, lexer: SmaliLexer, width: Int, curPos: Int): Int {
        var newPos = -1
        var lastDirectiveType = 0
        var start = 0
        while (true) {
            val token = lexer.nextToken()
            val type = token.type
            if (type == Recognizer.EOF)
                break
            val text = token.text
            when (type) {
                SmaliLexer.ANNOTATION_DIRECTIVE -> {
                    if (lastDirectiveType == SmaliLexer.FIELD_DIRECTIVE ||
                        lastDirectiveType == SmaliLexer.LOCAL_DIRECTIVE ||
                        lastDirectiveType == SmaliLexer.PARAMETER_DIRECTIVE)
                        sb.indent(width)
                    lastDirectiveType = 0
                    sb.append(text)
                    sb.indent(width)
                }
                SmaliLexer.SUBANNOTATION_DIRECTIVE,
                SmaliLexer.METHOD_DIRECTIVE,
                SmaliLexer.PACKED_SWITCH_DIRECTIVE,
                SmaliLexer.ARRAY_DATA_DIRECTIVE,
                SmaliLexer.SPARSE_SWITCH_DIRECTIVE,
                SmaliLexer.OPEN_BRACE -> {
                    sb.append(text)
                    sb.indent(width)
                }
                SmaliLexer.FIELD_DIRECTIVE,
                SmaliLexer.LOCAL_DIRECTIVE,
                SmaliLexer.PARAMETER_DIRECTIVE -> {
                    sb.append(text)
                    lastDirectiveType = type
                }
                SmaliLexer.END_ANNOTATION_DIRECTIVE,
                SmaliLexer.END_SUBANNOTATION_DIRECTIVE,
                SmaliLexer.END_FIELD_DIRECTIVE,
                SmaliLexer.END_METHOD_DIRECTIVE,
                SmaliLexer.END_PACKED_SWITCH_DIRECTIVE,
                SmaliLexer.END_ARRAY_DATA_DIRECTIVE,
                SmaliLexer.END_SPARSE_SWITCH_DIRECTIVE,
                SmaliLexer.END_LOCAL_DIRECTIVE,
                SmaliLexer.END_PARAMETER_DIRECTIVE,
                SmaliLexer.CLOSE_BRACE -> {
                    sb.deindent(width)
                    sb.append(text)
                }
                SmaliLexer.STRING_LITERAL -> {
                    SmaliFormater.processStringOrChar(sb, text, true)
                }
                SmaliLexer.CHAR_LITERAL -> {
                    SmaliFormater.processStringOrChar(sb, text, false)
                }
                SmaliLexer.WHITE_SPACE -> {
                    SmaliFormater.processWhiteSpace(sb, text)
                }
                else -> sb.append(text)
            }

            if (type != SmaliLexer.FIELD_DIRECTIVE ||
                type != SmaliLexer.LOCAL_DIRECTIVE ||
                type != SmaliLexer.PARAMETER_DIRECTIVE)
                lastDirectiveType = 0
            val end = token.stopIndex + 1
            newPos = Antlr4LexTask.compute(sb.length, start, end, curPos, newPos)
            start = end
        }
        if (newPos == -1)
            newPos = sb.length - 1
        return newPos
    }

    private class LanguageSmali : Language() {

        init {
            val opcodes = Opcode.values()
            val size = opcodes.size
            val keywords = arrayOfNulls<String>(size)
            for (i in 0 until size)
                keywords[i] = opcodes[i].name
            setKeywords(keywords)
        }

        override fun complete(buf: ArrayList<String>, constraint: CharSequence): CharSequence {
            if (constraint[0] != 'L')
                return super.complete(buf, constraint)
            var word = constraint.toString()
            var i = word.indexOf(';')
            if (i == -1) {
                word = word.lowercase(Locale.getDefault())
                if (word.startsWith("[")) {
                    i = word.lastIndexOf('[')

                }
                for (type in Packages.getTypes()) {
                    if (type.lowercase(Locale.getDefault()).startsWith(word))
                        buf.add(type)
                }
                return word
            }
            var word1 = word.substring(i + 1)
            if (word1.startsWith("->")) {
                word1 = word1.substring(2).lowercase(Locale.getDefault())
                val type = word.substring(0, i + 1)
                for (m in Packages.getMembers(type)) {
                    if (m.lowercase(Locale.getDefault()).startsWith(word1))
                        buf.add(m)
                }
            }
            return word1
        }

    }
}
