package com.riyadm.apkrepacker.ide.editor.lexer

import com.riyadm.codeeditor.lang.Language

open class NormalLanguage : Language() {
    init {
        keywords = arrayOf<String>()
    }
}
