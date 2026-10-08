package com.riyadm.apkrepacker.autotranslator.browser

import org.apache.http.HttpHost
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets


class WebBrowser {
    private val cookieStore: MyCookieStore? = MyCookieStore()

    private val proxy = HttpHost("xxx", 8080, "http")
    private val useProxy = false
    private var useSSL = false

    private var userAgent: String? = null

    constructor(userAgent: String?) {
        this.userAgent = userAgent
    }

    constructor(paramBoolean: Boolean) {
        this.useSSL = paramBoolean
    }

    private fun getHttpClient(): DefaultHttpClient {
        if (this.useSSL)
            return SSLSocketFactoryEx.getNewHttpClient()
        return DefaultHttpClient()
    }

    private fun getString(input: InputStream): String {
        val bufferSize = 256 * 1024
        var readSize = 0

        try {
            val buffer = ByteArray(bufferSize)
            val maxStrSize = bufferSize - 1
            while (readSize < maxStrSize) {
                val ret = input.read(buffer, readSize, maxStrSize - readSize)
                if (ret <= 0) {
                    break
                }
                readSize += ret
            }

            if (readSize > 0) {
                return String(buffer, 0, readSize, StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return ""
    }

    fun get(strUrl: String?, referUrl: String?): String? {
        var str: String? = null
        try {
            val httpGet = HttpGet(strUrl)
            httpGet.params.setParameter("http.protocol.cookie-policy", "compatibility")
            val httpClient = getHttpClient()
            if (userAgent != null)
                httpGet.addHeader("User-Agent", userAgent)
            if (referUrl != null) {
                httpGet.addHeader("Referer", referUrl)
            }
            val cookieStore = this.cookieStore
            if (cookieStore != null) {
                httpClient.cookieStore = this.cookieStore
            }
            if (this.useProxy) {
                httpClient.params.setParameter("http.route.default-proxy", this.proxy)
            }
            httpClient.params.setParameter("http.connection.timeout", 15000)
            httpClient.params.setParameter("http.socket.timeout", 15000)
            str = getString(httpClient.execute(httpGet).entity.content)
            // Debug.dump(tag + ".html", str);
            val newCookies = httpClient.cookieStore.cookies
            this.cookieStore!!.addCookies(newCookies)
            return str
        } catch (e: Exception) {
            e.printStackTrace()
            //Debug.dump(tag + ".error", e.getMessage());
        }
        return null
    }

}
