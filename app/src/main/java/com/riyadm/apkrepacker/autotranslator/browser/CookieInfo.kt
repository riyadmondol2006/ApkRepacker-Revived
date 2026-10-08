package com.riyadm.apkrepacker.autotranslator.browser

import java.io.Serializable
import java.util.Date

class CookieInfo : Serializable {
    private var domain: String? = null
    private var expiryDate: Date? = null
    private var name: String? = null
    private var value: String? = null

    fun getCookieDate(): Date? {
        return this.expiryDate
    }

    fun getCookieDomain(): String? {
        return this.domain
    }

    fun getCookieName(): String? {
        return this.name
    }

    fun getCookieValue(): String? {
        return this.value
    }

    fun setCookieDate(paramDate: Date?) {
        this.expiryDate = paramDate
    }

    fun setCookieDomain(paramString: String?) {
        this.domain = paramString
    }

    fun setCookieName(paramString: String?) {
        this.name = paramString
    }

    fun setCookieValue(paramString: String?) {
        this.value = paramString
    }

    companion object {
        private const val serialVersionUID = -1906450774713846916L
    }
}
