package com.riyadm.apkrepacker.autotranslator.dictionary

import com.riyadm.patchengine.utils.IOUtil.closeQuietly
import java.io.File
import java.io.FileInputStream

class DictionaryReader(private val mDictionaryFile: File) {

    private val mDictionaryItemHashMap = HashMap<String, DictionaryItem>()

    fun clear() {
        mDictionaryItemHashMap.clear()
    }

    fun getDictionaryMap(): HashMap<String, DictionaryItem> {
        return mDictionaryItemHashMap
    }

    fun addToMap(original: String, translated: String?) {
        if (original != translated) {
            val dictionaryItem = mDictionaryItemHashMap[original]
            if (dictionaryItem != null) {
                dictionaryItem.translated = translated
                return
            }
            val dictionaryItem1 = DictionaryItem(original, translated)
            mDictionaryItemHashMap[original] = dictionaryItem1
        }
    }

    fun readDictionary() {
        clear()
        try {
            val fileInputStream = FileInputStream(mDictionaryFile)
            val parserHelper = Scanner(fileInputStream)
            while (true) {
                val stringLine = parserHelper.next_token()
                if (stringLine.length != 0) {
                    Scanner.checkToken(stringLine, "{")
                    //parserHelper.checkToken("\"");
                    val original = parserHelper.next_token()
                    //лог для теста
                    //DLog.d(original);
                    //parserHelper.checkToken("\"");
                    val translated = parserHelper.next_token()
                    //лог для теста
                    //DLog.d(translated);
                    parserHelper.checkToken("}")
                    addToMap(original, translated)
                } else {
                    closeQuietly(fileInputStream)
                    return
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
