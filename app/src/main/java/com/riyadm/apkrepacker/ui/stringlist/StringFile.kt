package com.riyadm.apkrepacker.ui.stringlist

import java.io.File
import java.net.URI
import java.util.Locale

/** A `strings.xml` file together with the language folder suffix it belongs to ("default", "ru", "zh-rCN"…). */
class StringFile : File {

    private var langCode: String?
    private var locale: Locale?

    constructor(parent: File?, child: String, lang: String?) : super(parent, child) {
        langCode = lang
        locale = localeFor(lang)
    }

    constructor(pathname: String, lang: String?) : super(pathname) {
        langCode = lang
        locale = localeFor(lang)
    }

    constructor(parent: String?, child: String, lang: String?) : super(parent, child) {
        langCode = lang
        locale = localeFor(lang)
    }

    constructor(uri: URI, lang: String?) : super(uri) {
        langCode = lang
        locale = localeFor(lang)
    }

    fun lang(lang: String?) {
        langCode = lang
        locale = localeFor(lang)
    }

    fun lang(): String? = langCode

    fun locale(): Locale? = locale

    override fun getParentFile(): StringFile = StringFile(super.getParentFile(), "", langCode)

    private companion object {
        private val LOCALE_FOLDER = Regex("^([a-z]{2,3})(?:-r([A-Z]{2}))?$")

        /** Only folders that really name a locale get one; "night", "land", "sw600dp"… stay plain codes. */
        fun localeFor(code: String?): Locale? {
            val match = code?.let(LOCALE_FOLDER::matchEntire) ?: return null
            val (language, region) = match.destructured
            return if (region.isEmpty()) Locale(language) else Locale(language, region)
        }
    }
}
