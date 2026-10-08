package com.riyadm.apkrepacker.ui.filemanager.batch

import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import java.util.regex.Matcher
import java.util.regex.Pattern

class BatchRenameProcessor(pattern: String, variableConfig: VariableConfig) {
    private val mPattern: String = pattern
    private val mVariableMatcher: VariableMatcher = VariableMatcher(variableConfig)
    private var pattern: Pattern? = null
    private var mReplace: String? = null

    fun process(fileHolder: FileHolder): String {
        val patternSb = StringBuilder(this.mPattern)
        var i2 = 0
        while (i2 < patternSb.length) {
            val charAt = patternSb[i2]
            var variable: Variable? = null
            if (charAt == '%' && i2 + 1 < patternSb.length) {
                val i = i2 + 1
                variable = this.mVariableMatcher.match(patternSb.substring(i, i2 + 2))
            } else if (charAt == '#') {
                variable = this.mVariableMatcher.match(patternSb.substring(i2, i2 + 1))
            }
            if (variable != null) {
                val apply = variable.apply(patternSb, i2, fileHolder)
                if (apply == 0) {
                    patternSb.delete(i2, i2 + 2)
                } else {
                    i2 += apply - 1
                }
            }
            i2++
        }
        if (this.pattern == null) {
            return patternSb.toString()
        }
        return this.pattern!!.matcher(if (patternSb.length == 0) fileHolder.name else patternSb.toString()).replaceAll(this.mReplace)
    }

    fun replaceText(replaceText: String, replaceWith: String?, useRegex: Boolean) {
        if (useRegex) {
            this.pattern = Pattern.compile(replaceText)
        } else {
            this.pattern = Pattern.compile(Pattern.quote(replaceText))
        }
        // Plain-text mode: '$' and '\\' in the replacement are literal.
        this.mReplace = if (useRegex || replaceWith == null) replaceWith else Matcher.quoteReplacement(replaceWith)
    }
}
