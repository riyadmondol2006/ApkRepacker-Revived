package com.riyadm.apkrepacker.autotranslator.browser

import org.apache.http.client.CookieStore
import org.apache.http.cookie.Cookie
import org.apache.http.impl.cookie.BasicClientCookie
import java.util.Date


class MyCookieStore : CookieStore {

    private var cookieList: MutableList<CookieInfo>? = getCookieFile("CookieFile")

    init {
        if (this.cookieList == null)
            this.cookieList = ArrayList()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getCookieFile(filename: String): MutableList<CookieInfo> {
        return ArrayList()
    }

    @Suppress("unused")
    private fun dumpCookieList() {
        val localIterator = this.cookieList!!.iterator()
        while (true) {
            if (!localIterator.hasNext())
                return
            val localCookieInfo = localIterator.next()
            /*Debug.log("\tName: " + localCookieInfo.getCookieName());
			Debug.log("\tValue: " + localCookieInfo.getCookieValue());
			Debug.log("\tDomain: " + localCookieInfo.getCookieDomain());
			Debug.log("\tDate: " + localCookieInfo.getCookieDate());*/
        }
    }

    // Update the cookie
    private fun updateCookieList(cookieInfo: CookieInfo) {

        val name = cookieInfo.getCookieName()

        for (ci in this.cookieList!!) {
            // The same name already exists
            if (ci.getCookieName()!! == name) {
                ci.setCookieDate(cookieInfo.getCookieDate())
                ci.setCookieDomain(cookieInfo.getCookieDomain())
                ci.setCookieName(cookieInfo.getCookieName())
                ci.setCookieValue(cookieInfo.getCookieValue())
                return
            }
        }

        this.cookieList!!.add(cookieInfo)
    }

    // Add one cookie to the store
    override fun addCookie(cookie: Cookie?) {
        if (cookie == null)
            return

        if (this.cookieList == null) {
            this.cookieList = ArrayList()
        }

        val cookieInfo = CookieInfo()
        if (cookie.expiryDate != null)
            cookieInfo.setCookieDate(cookie.expiryDate)
        cookieInfo.setCookieName(cookie.name)
        cookieInfo.setCookieValue(cookie.value)
        cookieInfo.setCookieDomain(cookie.domain)

        updateCookieList(cookieInfo)
    }

    fun addCookies(cookies: List<Cookie>) {
        for (cookie in cookies) {
            addCookie(cookie)
        }
    }

    override fun clear() {
    }

    override fun clearExpired(paramDate: Date?): Boolean {
        return false
    }

    fun convertCookie(paramCookieInfo: CookieInfo): Cookie {
        val localBasicClientCookie = BasicClientCookie(
            paramCookieInfo.getCookieName(),
            paramCookieInfo.getCookieValue()
        )
        localBasicClientCookie.domain = paramCookieInfo.getCookieDomain()
        localBasicClientCookie.expiryDate = paramCookieInfo.getCookieDate()
        return localBasicClientCookie
    }

    override fun getCookies(): List<Cookie> {
        val ret = ArrayList<Cookie>()

        if (this.cookieList != null) {
            for (i in this.cookieList!!.indices) {
                val cookie = cookieList!![i]
                ret.add(convertCookie(cookie))
            }
        }

        return ret
    }

}
