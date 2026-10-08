package com.riyadm.apkrepacker.autotranslator.dictionary

class DictionaryItem(source: String?, translated: String?) {

    @JvmField
    var mSource: String? = source

    @JvmField
    var mTranslated: String? = translated

    var translated: String?
        get() = mTranslated
        set(translated) {
            mTranslated = translated
        }

    var source: String?
        get() = mSource
        set(source) {
            mSource = source
        }

    override fun toString(): String {
        return "DictionaryItem{" +
                "source='" + mSource + '\'' +
                ", translated='" + mTranslated + '\'' +
                '}'
    }
}
