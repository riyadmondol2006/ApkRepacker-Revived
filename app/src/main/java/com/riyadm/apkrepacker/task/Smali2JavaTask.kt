package com.riyadm.apkrepacker.task

import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.smali.LexerErrorInterface
import com.android.tools.smali.smali.SmaliOptions
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.TextEditorActivity
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import com.riyadm.apkrepacker.utils.Smali2Java
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.TokenSource
import org.antlr.runtime.tree.CommonTreeNodeStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class Smali2JavaTask(private val editorActivity: TextEditorActivity) : CoroutinesAsyncTask<File, CharSequence, Boolean>() {
    private var javaCode: String? = null
    private var smali: File? = null

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)
        editorActivity.openJavaText(javaCode, smali!!.name/*FileUtil.getNameVithoutExt(smali)*/)
        //dialog.hideProgress();
        if (!result!!)
            editorActivity.showTaskMessage(R.string.toast_error_decompile_smali_to_java)

    }

    override fun doInBackground(vararg params: File?): Boolean {
        var success = true
        for (file in params) {
            if (!process(file))
                success = false
        }
        return success
    }

    private fun process(smali: File?): Boolean {
        try {
            this.smali = smali
            // DexBuilder dexBuilder = new DexBuilder(Opcodes.getDefault());
            //  assembleSmaliFile(smali, dexBuilder, new SmaliOptions());
            javaCode = Smali2Java.translate(smali)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    companion object {
        @Suppress("unused")
        @Throws(Exception::class)
        private fun assembleSmaliFile(smaliFile: File, dexBuilder: DexBuilder, options: SmaliOptions): Boolean {
            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(smaliFile)
                val reader = InputStreamReader(fis, "UTF-8")

                val lexer: LexerErrorInterface = smaliFlexLexer(reader, options.apiLevel)
                (lexer as smaliFlexLexer).setSourceFile(smaliFile)
                val tokens = CommonTokenStream(lexer as TokenSource)

                if (options.printTokens) {
                    tokens.tokens

                    for (i in 0 until tokens.size()) {
                        val token = tokens.get(i)
                        if (token.channel == smaliParser.HIDDEN) {
                            continue
                        }

                        val tokenName: String = if (token.type == -1) {
                            "EOF"
                        } else {
                            smaliParser.tokenNames[token.type]
                        }
                        println(tokenName + ": " + token.text)
                    }

                    System.out.flush()
                }

                val parser = smaliParser(tokens)
                parser.setVerboseErrors(options.verboseErrors)
                parser.setAllowOdex(options.allowOdexOpcodes)
                parser.setApiLevel(options.apiLevel)

                val result = parser.smali_file()

                if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0) {
                    return false
                }

                val t = result.tree

                val treeStream = CommonTreeNodeStream(t)
                treeStream.tokenStream = tokens

                if (options.printTokens) {
                    println(t.toStringTree())
                }

                val dexGen = smaliTreeWalker(treeStream)
                dexGen.setApiLevel(options.apiLevel)

                dexGen.setVerboseErrors(options.verboseErrors)
                dexGen.setDexBuilder(dexBuilder)
                dexGen.smali_file()

                return dexGen.numberOfSyntaxErrors == 0
            } finally {
                fis?.close()
            }
        }
    }
}
