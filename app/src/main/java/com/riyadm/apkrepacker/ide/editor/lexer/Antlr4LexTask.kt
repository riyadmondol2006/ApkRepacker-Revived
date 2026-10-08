package com.riyadm.apkrepacker.ide.editor.lexer

import com.riyadm.codeeditor.lang.Language
import com.riyadm.codeeditor.util.IndentStringBuilder
import com.riyadm.codeeditor.util.LexTask
//import com.riyadm.codeeditor.util.Lexer;
import com.riyadm.codeeditor.util.Pair

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


abstract class Antlr4LexTask<L : Lexer> protected constructor(language: Language) : LexTask(language) {

    private val lexer: L = generateLexer()


    protected constructor(tokens: Vocabulary) : this(Antlr4Lanugage(tokens))

    protected open fun canAnalysis(): Boolean {
        return false
    }

    protected abstract fun generateLexer(): L

    @Synchronized
    final override fun tokenize(_tokens: MutableList<Pair>, text: String) {
        val input = ANTLRInputStream(text)
        val lexer = this.lexer
        lexer.setInputStream(input)
        tokenize(_tokens, lexer)
        if (canAnalysis()) {
            input.reset()
            stopLast()
            analysis.execute {
                parse(input)
                //EditorPagerAdapter.INSTANCE.resetError();
            }
        }
    }

    protected open fun parse(i: ANTLRInputStream) {
    }

    protected open fun stopLast() {
        // TODO: Implement this method
    }

    protected abstract fun tokenize(_tokens: MutableList<Pair>, lexer: L)

    @Synchronized
    override fun format(sb: IndentStringBuilder, input: CharSequence, width: Int, curPos: Int): Int {
        lexer.setInputStream(ANTLRInputStream(input.toString()))
        return format(sb, lexer, width, curPos)
    }

    protected open fun format(sb: IndentStringBuilder, lexer: L, width: Int, curPos: Int): Int {
        return curPos
    }

    override fun expandSelection(text: String, oldStart: Int, oldEnd: Int): LexTask.Selection {
        var tree = getTree()
        if (tree == null) {
            val lexer = this.lexer
            lexer.reset()
            parse(lexer)
            tree = getTree()
        }
        var s: LexTask.Selection? = null
        if (tree != null)
            s = expandSelection(tree, oldStart, oldEnd - 1)
        if (s == null)
            return super.expandSelection(text, oldStart, oldEnd)
        return s
    }

    protected abstract fun parse(lexer: L)

    protected open fun getTree(): ParseTree? {
        return null
    }

    private fun expandSelection(tree: ParseTree, oldStart: Int, oldEnd: Int): LexTask.Selection? {
        if (tree is TerminalNode) {//只包含一个Token
            val tk = tree.symbol
            val start = tk.startIndex
            val end = tk.stopIndex
            return expandSelection(oldStart, oldEnd, start, end)
        } else {
            val ctx = tree as ParserRuleContext
            val start = ctx.start.startIndex
            val end = ctx.stop.stopIndex
            if (oldStart >= start && oldEnd <= end) {
                //当前选择位置在这个节点内
                if (oldStart == start && oldEnd == end)
                    return null
                val count = ctx.childCount
                //遍历子节点
                for (i in 0 until count) {
                    val tree1 = ctx.getChild(i)
                    val selection = expandSelection(tree1, oldStart, oldEnd)
                    //当前子节点不符合
                    if (selection == null)
                        continue
                    //符合
                    return selection
                }
                return expandSelection(oldStart, oldEnd, start, end)
            }
        }
        return null
    }

    private fun expandSelection(oldStart: Int, oldEnd: Int, start: Int, end: Int): LexTask.Selection? {
        if (oldStart >= start && oldEnd <= end) {
            if (oldStart == start && oldEnd == end)
                return null
            return LexTask.Selection(start, end + 1)
        }
        return null
    }

    open class Antlr4Lanugage internal constructor(tokens: Vocabulary) : Language() {
        init {
            keywords = LexerUtil.getKeywords(tokens)
        }
    }

    companion object {
        private val analysis: ExecutorService = Executors.newSingleThreadExecutor()

        @JvmStatic
        fun compute(max: Int, s: Int, e: Int, c: Int, n: Int): Int {
            var n = n
            if (e < c)
                n = max + 1
            else if (c >= s && c <= e) {
                n = max - e + c
                val s1 = max - e + s
                if (n < s1)
                    n = s1
                n++
            }
            return n
        }
    }
}
