package com.riyadm.apkrepacker.autotranslator.translator

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.riyadm.apkrepacker.autotranslator.browser.WebBrowser
import com.riyadm.apkrepacker.utils.common.DLog
import java.net.URLEncoder
import java.util.Objects


open class Translator(target: String?) {

    private val browser: WebBrowser
    private val START_URL = "https://translate.google.com/"
    private val translateUrl = "https://translate.google.com/translate_a/single?client=gtx&dt=t&dj=1&ie=UTF-8&sl=auto&tl="
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36"

    // This is a temporary var
    private var jsonIndex = 0

    private val targetLangCode: String?

    init {
        this.browser = WebBrowser(USER_AGENT)
        this.targetLangCode = target
    }

    protected open fun encodeToUrl(str: String?): String? {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }

    //translating item
    fun translate(items: List<TranslateItem>) {
        // Concat the string
        val queryBuf = StringBuilder()
        for (item in items) {
            queryBuf.append(item.originValue)
            queryBuf.append('\n')
        }
        queryBuf.deleteCharAt(queryBuf.length - 1)
        val contentToTranslate = queryBuf.toString()

        val url = translateUrl + targetLangCode + "&q=" + encodeToUrl(contentToTranslate)
        // String url = "https://translate.google.com/translate_a/single?client=webapp&sl=auto&tl="
        //         + targetLangCode + "&hl=en&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk="
        //       + encodeToUrl(getToken(tkk, contentToTranslate)) + "&q=" + encodeToUrl(contentToTranslate);
        DLog.d("url=%s", url)

        //create browser and translate strings from google translator and get translated json content
        val content = browser.get(url, START_URL)

        // Succeed
        if (content != null)
        // if (content != null && content.startsWith("[[[\"") && (position = content.lastIndexOf("]")) != -1) {
            parseContent(content, items)
        //  }
        DLog.d(String.format("content=%s", content))
    }

    fun translate(value: String?): String? {
        var translated: String? = null
        val url = translateUrl + targetLangCode + "&q=" + encodeToUrl(value)
        DLog.d("url=%s", url)

        //create browser and translate strings from google translator and get translated json content
        val content = browser.get(url, START_URL)

        // Succeed
        if (content != null) {
            @Suppress("DEPRECATION")
            val jsonParser = JsonParser()
            @Suppress("DEPRECATION")
            val element = jsonParser.parse(content)
            val sentences = element.asJsonObject.get("sentences").asJsonArray
            translated = checkIsFormattedValue(Objects.requireNonNull(extractAllValueFromJson(sentences))!!)
        }
        return translated
    }

    // Parse the content and save result to items
    private fun parseContent(jsonResponse: String, items: List<TranslateItem>) {
        try {
            @Suppress("DEPRECATION")
            val jsonParser = JsonParser()
            @Suppress("DEPRECATION")
            val element = jsonParser.parse(jsonResponse)
            val sentences = element.asJsonObject.get("sentences").asJsonArray
            // Only one item, all the translation content should save to it
            if (items.size == 1) {
                items[0].translatedValue = checkIsFormattedValue(Objects.requireNonNull(extractAllValueFromJson(sentences))!!)
                DLog.d("DEBUG", items[0].originValue + " ---> " + items[0].translatedValue)
            } else {
                this.jsonIndex = 0
                for (item in items) {
                    item.translatedValue = checkIsFormattedValue(Objects.requireNonNull(extractOneItemFromJson(sentences))!!)
                    DLog.d("DEBUG", String.format("translated: %s ---> %1s", item.originValue, item.translatedValue))
                }
                /*for (int i = 0; i < items.size(); i++) {
                    TranslateItem item = items.get(i);
                    item.translatedValue = checkIsFormattedValue(Objects.requireNonNull(extractOneItemFromJson(values)));
                    DLog.d("DEBUG", String.format("translated: %s ---> %1s", item.originValue, item.translatedValue));
                }*/
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkIsFormattedValue(inputString: String): String {
        return if (inputString.contains("%") || inputString.contains("$"))
            inputString.replace("%\\s{0,2}(\\d)\\s{0,2}\\$\\s{0,2}([dDsSдДсС])".toRegex(), "%$1\\$$2")
                .replace("%\\s{0,2}(\\d|\\w)".toRegex(), "%$1")
                .replace("%\\s{0,2}([dDдД])".toRegex(), "%d")
                .replace("%\\s{0,2}([sSсС])".toRegex(), "%s")
                .replace("%\\s{0,2}([dDдД])".toRegex(), "\\\$d")
                .replace("%\\s{0,2}([sSсС])".toRegex(), "\\\$s")
                .replace("([a-zA-Za-яА-Я:/.,])%(\\d|\\w)".toRegex(), "$1 %$2")
        else
            inputString
    }

    // In Json, one item may be splited into several arrays
    // jsonIndex will be used
    private fun extractOneItemFromJson(values: JsonArray): String? {
        val sb = StringBuilder()
        while (jsonIndex < values.size()) {
            val element1 = values.get(jsonIndex++)
            val curVal: String? = element1.asJsonObject.get("trans").asString
            if (curVal == null) {
                break
            }

            sb.append(curVal)
            if (curVal.endsWith("\n")) {
                sb.deleteCharAt(sb.length - 1)
                break
            }
        }
        if (sb.length > 0) {
            return sb.toString()
        }

        return null
    }

    private fun extractAllValueFromJson(values: JsonArray): String? {
        val sb = StringBuilder()
        var jsonIndex = 0

        if (values.size() > 0) {
            while (jsonIndex < values.size()) {
                val element = values.get(jsonIndex++)
                val curVal: String? = element.asJsonObject.get("trans").asString
                if (curVal == null) {
                    break
                }
                sb.append(curVal)
            }
            if (sb.length > 0) {
                return sb.toString()
            }

        }
        return null
    }

    companion object {
        private fun decodeChar(c1: Char, c2: Char): Char {
            val i1 = hex2Int(c1)
            val i2 = hex2Int(c2)
            return (i1 * 16 + i2).toChar()
        }

        private fun hex2Int(c: Char): Int {
            return if (c >= '0' && c <= '9') {
                c - '0'
            } else if (c >= 'a' && c <= 'f') {
                c - 'a' + 10
            } else {
                c - 'A' + 10
            }
        }
    }
}
