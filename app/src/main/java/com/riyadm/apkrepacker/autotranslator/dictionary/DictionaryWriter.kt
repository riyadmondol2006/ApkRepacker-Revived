package com.riyadm.apkrepacker.autotranslator.dictionary

import com.jecelyin.common.utils.IOUtils
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import java.io.File

class DictionaryWriter(private val mDictionaryFile: File) {

    fun writeDictionary(translateItems: List<TranslateItem>) {
        Thread {
            val sb = StringBuilder(1024)
            for (translateItem in translateItems) {
                val originValue = translateItem.originValue
                val translatedValue = translateItem.translatedValue
                if (!(originValue == null || translatedValue == null || originValue == translatedValue)) {
                    sb.append('{')
                    sb.append(LINE_SEPARATOR_WIN)
                    sb.append("  \"")
                    sb.append(eolValue(originValue))
                    sb.append('\"')
                    sb.append(LINE_SEPARATOR_WIN)
                    sb.append("  \"")
                    sb.append(eolValue(translatedValue))
                    sb.append('\"')
                    sb.append(LINE_SEPARATOR_WIN)
                    sb.append('}')
                    sb.append(LINE_SEPARATOR_WIN)
                }
            }
            IOUtils.writeFile(mDictionaryFile, sb.toString())
        }.start()
    }

    private fun eolValue(value: String): String {
        return value.replace("\\", "\\\\").replace("\u000C", "\\f")
            .replace("\n", "\\n").replace("\r", "\\r")
            .replace("\t", "\\t").replace("\b", "\\b")
            .replace("\"", "\\\"")
    }

    companion object {
        private const val LINE_SEPARATOR_WIN = "\r\n"
    }
}
