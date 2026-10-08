package com.riyadm.apkrepacker.utils.qickedit

import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException

class ManifestAnalyser {

    constructor(apk: File?) {
        apkFile = apk
    }

    constructor(apk: String) {
        apkFile = File(apk)
    }

    companion object {
        private var apkFile: File? = null

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun isSplitRequired(): Boolean {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "isSplitRequired") {
                            return parser.getAttributeBooleanValue(i, true)
                        }
                    }
                }
            }
            return false
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getMinSdkVersion(): Int {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses-sdk") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "minSdkVersion") {
                            return parser.getAttributeIntValue(i, -1)
                        }
                    }
                }
            }
            return -1
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getTargetSdkVersion(): Int {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses-sdk") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "targetSdkVersion") {
                            return parser.getAttributeIntValue(i, -1)
                        }
                    }
                }
            }
            return -1
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getPackageName(): String? {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "manifest") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "package") {
                            return parser.getAttributeValue(i)
                        }
                    }
                }
            }
            return ""
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getVersionName(): String? {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "manifest") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "versionName") {
                            return parser.getAttributeValue(i)
                        }
                    }
                }
            }
            return ""
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getVersionCode(): Int {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "manifest") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "versionCode") {
                            return parser.getAttributeIntValue(i, 0)
                        }
                    }
                }
            }
            return 0
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun getPackageLabel(): String? {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "label") {
                            return parser.getAttributeValue(i)
                        }
                    }
                }
            }
            return ""
        }

        @Throws(IOException::class)
        private fun getParserForManifest(apkFile: File?): XmlResourceParser {
            val assetManagerInstance = getAssetManager()
            val cookie = addAssets(apkFile, assetManagerInstance)
            return (assetManagerInstance as AssetManager?)!!.openXmlResourceParser(cookie, "AndroidManifest.xml")
        }

        private fun addAssets(apkFile: File?, assetManagerInstance: Any?): Int {
            try {
                val addAssetPath = assetManagerInstance!!.javaClass.getMethod("addAssetPath", String::class.java)
                return addAssetPath.invoke(assetManagerInstance, apkFile!!.absolutePath) as Int
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
            return -1
        }

        @Suppress("DEPRECATION")
        private fun getAssetManager(): Any? {
            var assetManagerClass: Class<*>? = null
            try {
                assetManagerClass = Class.forName("android.content.res.AssetManager")
                val assetManagerInstance = assetManagerClass.newInstance()
                return assetManagerInstance
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            } catch (e: InstantiationException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
            return null
        }
    }
}
