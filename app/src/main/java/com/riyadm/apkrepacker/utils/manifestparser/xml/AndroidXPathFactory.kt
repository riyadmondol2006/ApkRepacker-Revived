/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.riyadm.apkrepacker.utils.manifestparser.xml

import com.riyadm.apkrepacker.utils.manifestparser.SdkConstants
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory

/**
 * XPath factory with automatic support for the android name space.
 */
class AndroidXPathFactory {

    /** Name space context for Android resource XML files. */
    private class AndroidNamespaceContext
    /**
     * Construct the context with the prefix associated with the android namespace.
     * @param androidPrefix the Prefix
     */
    (private val mAndroidPrefix: String?) : NamespaceContext {
        private val mAndroidPrefixes: List<String?> = listOf(mAndroidPrefix)

        override fun getNamespaceURI(prefix: String?): String {
            if (prefix != null) {
                if (prefix == mAndroidPrefix) {
                    return SdkConstants.ANDROID_URI
                }
            }

            return XMLConstants.NULL_NS_URI
        }

        override fun getPrefix(namespaceURI: String?): String? {
            if (SdkConstants.ANDROID_URI == namespaceURI) {
                return mAndroidPrefix
            }

            return null
        }

        @Suppress("UNCHECKED_CAST")
        override fun getPrefixes(namespaceURI: String?): Iterator<String>? {
            if (SdkConstants.ANDROID_URI == namespaceURI) {
                return mAndroidPrefixes.iterator() as Iterator<String>
            }

            return null
        }

        companion object {
            private val sThis = AndroidNamespaceContext(DEFAULT_NS_PREFIX)

            /**
             * Returns the default [AndroidNamespaceContext].
             */
            @JvmStatic
            fun getDefault(): AndroidNamespaceContext {
                return sThis
            }
        }
    }

    companion object {
        /** Default prefix for android name space: 'android' */
        const val DEFAULT_NS_PREFIX = "android" //$NON-NLS-1$

        private val sFactory: XPathFactory = XPathFactory.newInstance()

        /**
         * Creates a new XPath object, specifying which prefix in the query is used for the
         * android namespace.
         * @param androidPrefix The namespace prefix.
         */
        @JvmStatic
        fun newXPath(androidPrefix: String?): XPath {
            val xpath = sFactory.newXPath()
            xpath.namespaceContext = AndroidNamespaceContext(androidPrefix)
            return xpath
        }

        /**
         * Creates a new XPath object using the default prefix for the android namespace.
         * @see DEFAULT_NS_PREFIX
         */
        @JvmStatic
        fun newXPath(): XPath {
            val xpath = sFactory.newXPath()
            xpath.namespaceContext = AndroidNamespaceContext.getDefault()
            return xpath
        }
    }
}
