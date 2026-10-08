package com.riyadm.apkrepacker.ide.editor.lexer

import com.a4455jkjh.lexer.JavaParser
import com.a4455jkjh.lexer.JavaParserBaseVisitor
import com.riyadm.codeeditor.util.IndentStringBuilder


import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

class JavaFormatter private constructor() : JavaParserBaseVisitor<Any?>() {
    private var tokens: CommonTokenStream? = null
    private var sb: IndentStringBuilder? = null
    private var width = 0

    private fun writeComment(tk: Token) {
        val left = tokens!!.getHiddenTokensToLeft(tk.tokenIndex)
        if (left != null) {
            for (t in left) {
                val type = t.type
                if (type == JavaParser.LINE_COMMENT) {
                    sb!!.append(t.text)
                    sb!!.append('\n')
                } else if (type == JavaParser.COMMENT) {
                    sb!!.append(t.text)
                    sb!!.append('\n')
                }
            }
        }
    }

    override fun visitCompilationUnit(ctx: JavaParser.CompilationUnitContext): Any? {

        return super.visitCompilationUnit(ctx)
    }

    override fun visitTerminal(node: TerminalNode): Any? {

        return super.visitTerminal(node)
    }

    companion object {
        private val formatters = ThreadLocal<JavaFormatter>()

        private fun getInstance(t: CommonTokenStream?, s: IndentStringBuilder?, w: Int): JavaFormatter {
            var f = formatters.get()
            if (f == null) {
                f = JavaFormatter()
                formatters.set(f)
            }
            f.tokens = t
            f.sb = s
            f.width = w
            return f
        }

        @JvmStatic
        fun format(tokens: CommonTokenStream?, sb: IndentStringBuilder?, width: Int, unit: JavaParser.CompilationUnitContext) {
            unit.accept(getInstance(tokens, sb, width))
        }
    }

}
