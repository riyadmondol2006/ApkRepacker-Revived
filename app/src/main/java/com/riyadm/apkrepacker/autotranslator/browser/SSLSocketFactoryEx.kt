package com.riyadm.apkrepacker.autotranslator.browser

import org.apache.http.HttpVersion
import org.apache.http.conn.scheme.PlainSocketFactory
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpProtocolParams
import java.io.IOException
import java.net.Socket
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SSLSocketFactoryEx @Throws(
    NoSuchAlgorithmException::class,
    KeyManagementException::class,
    KeyStoreException::class,
    UnrecoverableKeyException::class
) constructor(paramKeyStore: KeyStore?) : org.apache.http.conn.ssl.SSLSocketFactory(paramKeyStore) {

    private val sslContext: SSLContext = SSLContext.getInstance("TLS")

    init {
        val local1: TrustManager = object : X509TrustManager {
            override fun checkClientTrusted(paramArrayOfX509Certificate: Array<X509Certificate>?, paramString: String?) {
            }

            override fun checkServerTrusted(paramArrayOfX509Certificate: Array<X509Certificate>?, paramString: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate>? {
                return null
            }
        }
        this.sslContext.init(null, arrayOf(local1), null)
    }

    @Throws(IOException::class)
    override fun createSocket(): Socket {
        return this.sslContext.socketFactory.createSocket()
    }

    @Throws(IOException::class)
    override fun createSocket(paramSocket: Socket?, paramString: String?, paramInt: Int, paramBoolean: Boolean): Socket {
        return this.sslContext.socketFactory.createSocket(paramSocket, paramString, paramInt, paramBoolean)
    }

    companion object {
        @JvmStatic
        fun getNewHttpClient(): DefaultHttpClient {
            try {
                val localKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                localKeyStore.load(null, null)
                val localSSLSocketFactoryEx = SSLSocketFactoryEx(localKeyStore)
                localSSLSocketFactoryEx.hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                val localBasicHttpParams = BasicHttpParams()
                HttpProtocolParams.setVersion(localBasicHttpParams, HttpVersion.HTTP_1_1)
                HttpProtocolParams.setContentCharset(localBasicHttpParams, "UTF-8")
                val localSchemeRegistry = SchemeRegistry()
                localSchemeRegistry.register(Scheme("http", PlainSocketFactory.getSocketFactory(), 80))
                localSchemeRegistry.register(Scheme("https", localSSLSocketFactoryEx, 443))
                return DefaultHttpClient(
                    ThreadSafeClientConnManager(localBasicHttpParams, localSchemeRegistry), localBasicHttpParams
                )
            } catch (localException: Exception) {
                localException.printStackTrace()
            }
            return DefaultHttpClient()
        }
    }
}
