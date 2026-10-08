/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.riyadm.apkrepacker.utils.manifestparser.IAbstractFile
import com.riyadm.apkrepacker.utils.manifestparser.SdkConstants
import com.riyadm.apkrepacker.utils.manifestparser.StreamException
import com.riyadm.apkrepacker.utils.manifestparser.resources.Keyboard
import com.riyadm.apkrepacker.utils.manifestparser.resources.Navigation
import com.riyadm.apkrepacker.utils.manifestparser.resources.TouchScreen
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/**
 * Full Manifest parser that parses the manifest in details, including activities, instrumentations,
 * support-screens, and uses-configuration.
 */
class AndroidManifestParser {

    interface ManifestErrorHandler : ErrorHandler {
        /**
         * Handles a parsing error and an optional line number.
         */
        fun handleError(exception: Exception?, lineNumber: Int)

        /**
         * Checks that a class is valid and can be used in the Android Manifest.
         * <p>
         * Errors are put as `org.eclipse.core.resources.IMarker` on the manifest file.
         *
         * @param className the fully qualified name of the class to test.
         * @param superClassName the fully qualified name of the class it is supposed to extend.
         * @param testVisibility if <code>true</code>, the method will check the visibility of
         * the class or of its constructors.
         */
        fun checkClass(
            locator: Locator?, className: String?, superClassName: String?,
            testVisibility: Boolean
        )
    }

    /**
     * XML error and data handler used when parsing the AndroidManifest.xml file.
     * <p>
     * During parsing this will fill up the [ManifestData] object given to the constructor
     * and call out errors to the given [ManifestErrorHandler].
     */
    private class ManifestHandler
    /**
     * Creates a new [ManifestHandler].
     *
     * @param manifestData Class containing the manifest info obtained during the parsing.
     * @param errorHandler An optional error handler.
     */
    internal constructor(
        // --- temporary data/flags used during parsing
        private val mManifestData: ManifestData?,
        private val mErrorHandler: ManifestErrorHandler?
    ) : DefaultHandler() {
        private var mCurrentLevel = 0
        private var mValidLevel = 0
        private var mCurrentActivity: ManifestData.Activity? = null
        private var mLocator: Locator? = null

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#setDocumentLocator(org.xml.sax.Locator)
         */
        override fun setDocumentLocator(locator: Locator?) {
            mLocator = locator
            super.setDocumentLocator(locator)
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String,
         * java.lang.String, org.xml.sax.Attributes)
         */
        @Throws(SAXException::class)
        override fun startElement(
            uri: String?,
            localName: String?,
            name: String?,
            attributes: Attributes?
        ) {
            try {
                if (mManifestData == null) {
                    return
                }

                // if we're at a valid level
                if (mValidLevel == mCurrentLevel) {
                    var value: String?
                    when (mValidLevel) {
                        LEVEL_TOP -> if (AndroidManifest.NODE_MANIFEST == localName) {
                            // lets get the package name.
                            mManifestData.mPackage =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_PACKAGE,
                                    false /* hasNamespace */
                                )

                            // and the versionCode
                            val tmp =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_VERSIONCODE,
                                    true
                                )
                            if (tmp != null) {
                                try {
                                    mManifestData.mVersionCode = Integer.valueOf(tmp)
                                } catch (e: NumberFormatException) {
                                    // keep null in the field.
                                }
                            }
                            // and the versionName
                            mManifestData.mVersionName =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_VERSIONNAME,
                                    true /* hasNamespace */
                                )
                            mValidLevel++
                        }
                        LEVEL_INSIDE_MANIFEST -> if (AndroidManifest.NODE_APPLICATION == localName) {
                            processApplicationNode(attributes)
                            mValidLevel++
                        } else if (AndroidManifest.NODE_USES_SDK == localName) {
                            mManifestData.minSdkVersionString = (
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                                    true /* hasNamespace */
                                )
                            )
                            mManifestData.setTargetSdkVersionString(
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_TARGET_SDK_VERSION,
                                    true /* hasNamespace */
                                )
                            )
                        } else if (AndroidManifest.NODE_INSTRUMENTATION == localName) {
                            processInstrumentationNode(attributes)

                        } else if (AndroidManifest.NODE_SUPPORTS_SCREENS == localName) {
                            processSupportsScreensNode(attributes)

                        } else if (AndroidManifest.NODE_USES_CONFIGURATION == localName) {
                            processUsesConfiguration(attributes)

                        } else if (AndroidManifest.NODE_USES_FEATURE == localName) {
                            val feature = ManifestData.UsesFeature()

                            // get the name
                            value =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_NAME,
                                    true /* hasNamespace */
                                )
                            if (value != null) {
                                feature.mName = value
                            }

                            // read the required attribute
                            value =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_REQUIRED,
                                    true /*hasNamespace*/
                                )
                            if (value != null) {
                                val b: Boolean = java.lang.Boolean.valueOf(value)
                                feature.mRequired = b
                            }

                            // read the gl es attribute
                            value =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_GLESVERSION,
                                    true /*hasNamespace*/
                                )
                            if (value != null) {
                                try {
                                    val version = Integer.decode(value)
                                    feature.mGlEsVersion = version
                                } catch (e: NumberFormatException) {
                                    // ignore
                                }
                            }

                            mManifestData.mFeatures.add(feature)
                        }
                        LEVEL_INSIDE_APPLICATION -> if (AndroidManifest.NODE_ACTIVITY == localName
                            || AndroidManifest.NODE_ACTIVITY_ALIAS == localName
                        ) {
                            processActivityNode(attributes)
                            mValidLevel++
                        } else if (AndroidManifest.NODE_SERVICE == localName) {
                            processNode(attributes, SdkConstants.CLASS_SERVICE, localName)
                            mValidLevel++
                        } else if (AndroidManifest.NODE_RECEIVER == localName) {
                            processNode(
                                attributes,
                                SdkConstants.CLASS_BROADCASTRECEIVER,
                                localName
                            )
                            mValidLevel++
                        } else if (AndroidManifest.NODE_PROVIDER == localName) {
                            processNode(
                                attributes, SdkConstants.CLASS_CONTENTPROVIDER, localName
                            )
                            mValidLevel++
                        } else if (AndroidManifest.NODE_USES_LIBRARY == localName) {
                            value =
                                getAttributeValue(
                                    attributes,
                                    AndroidManifest.ATTRIBUTE_NAME,
                                    true /* hasNamespace */
                                )
                            if (value != null) {
                                val library = ManifestData.UsesLibrary()
                                library.mName = value

                                // read the required attribute
                                value =
                                    getAttributeValue(
                                        attributes,
                                        AndroidManifest.ATTRIBUTE_REQUIRED,
                                        true /*hasNamespace*/
                                    )
                                if (value != null) {
                                    val b: Boolean = java.lang.Boolean.valueOf(value)
                                    library.mRequired = b
                                }

                                mManifestData.mLibraries.add(library)
                            }
                        }
                        LEVEL_INSIDE_APP_COMPONENT ->
                            // only process this level if we are in an activity
                            if (mCurrentActivity != null &&
                                AndroidManifest.NODE_INTENT == localName
                            ) {
                                mCurrentActivity!!.resetIntentFilter()
                                mValidLevel++
                            }
                        LEVEL_INSIDE_INTENT_FILTER -> {
                            val currentActivity = mCurrentActivity
                            if (currentActivity != null) {
                                if (AndroidManifest.NODE_ACTION == localName) {
                                    // get the name attribute
                                    val action = getAttributeValue(
                                        attributes,
                                        AndroidManifest.ATTRIBUTE_NAME,
                                        true /* hasNamespace */
                                    )
                                    if (action != null) {
                                        currentActivity.setHasAction(true)
                                        currentActivity.setHasMainAction(
                                            ACTION_MAIN == action
                                        )
                                    }
                                } else if (AndroidManifest.NODE_CATEGORY == localName) {
                                    val category = getAttributeValue(
                                        attributes,
                                        AndroidManifest.ATTRIBUTE_NAME,
                                        true /* hasNamespace */
                                    )
                                    if (CATEGORY_LAUNCHER == category) {
                                        currentActivity.setHasLauncherCategory(true)
                                    }
                                }

                                // no need to increase mValidLevel as we don't process anything
                                // below this level.
                            }
                        }
                    }
                }

                mCurrentLevel++
            } finally {
                super.startElement(uri, localName, name, attributes)
            }
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String,
         * java.lang.String)
         */
        @Throws(SAXException::class)
        override fun endElement(uri: String?, localName: String?, name: String?) {
            try {
                if (mManifestData == null) {
                    return
                }

                // decrement the levels.
                if (mValidLevel == mCurrentLevel) {
                    mValidLevel--
                }
                mCurrentLevel--

                // if we're at a valid level
                // process the end of the element
                if (mValidLevel == mCurrentLevel) {
                    when (mValidLevel) {
                        LEVEL_INSIDE_APPLICATION -> mCurrentActivity = null
                        LEVEL_INSIDE_APP_COMPONENT -> {
                            // if we found both a main action and a launcher category, this is our
                            // launcher activity!
                            val currentActivity = mCurrentActivity
                            if (mManifestData.mLauncherActivity == null &&
                                currentActivity != null &&
                                currentActivity.isHomeActivity &&
                                currentActivity.isExported
                            ) {
                                mManifestData.mLauncherActivity = currentActivity
                            }
                        }
                        else -> {
                        }
                    }

                }
            } finally {
                super.endElement(uri, localName, name)
            }
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#error(org.xml.sax.SAXParseException)
         */
        override fun error(e: SAXParseException) {
            mErrorHandler?.handleError(e, e.lineNumber)
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#fatalError(org.xml.sax.SAXParseException)
         */
        override fun fatalError(e: SAXParseException) {
            mErrorHandler?.handleError(e, e.lineNumber)
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#warning(org.xml.sax.SAXParseException)
         */
        @Throws(SAXException::class)
        override fun warning(e: SAXParseException?) {
            mErrorHandler?.warning(e)
        }

        /**
         * Processes the application node.
         *
         * @param attributes the attributes for the application node.
         */
        private fun processApplicationNode(attributes: Attributes?) {
            val manifestData = mManifestData!!
            var value =
                getAttributeValue(
                    attributes, AndroidManifest.ATTRIBUTE_PROCESS, true /* hasNamespace */
                )
            if (value != null) {
                manifestData.addProcessName(value)
                manifestData.mDefaultProcess = value
            }

            value =
                getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_DEBUGGABLE,
                    true /* hasNamespace*/
                )
            if (value != null) {
                manifestData.mDebuggable = java.lang.Boolean.parseBoolean(value)
            }

            value =
                getAttributeValue(
                    attributes, AndroidManifest.ATTRIBUTE_NAME, true /* hasNamespace*/
                )

            if (value != null) {
                manifestData.mKeepClasses.add(
                    ManifestData.KeepClass(
                        combinePackageAndClassName(manifestData.mPackage, value),
                        null,
                        AndroidManifest.NODE_APPLICATION
                    )
                )
            }

            value =
                getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_BACKUP_AGENT,
                    true /* hasNamespace*/
                )

            if (value != null) {
                manifestData.mKeepClasses.add(
                    ManifestData.KeepClass(
                        combinePackageAndClassName(manifestData.mPackage, value),
                        null,
                        AndroidManifest.ATTRIBUTE_BACKUP_AGENT
                    )
                )
            }
        }

        /**
         * Processes the activity node.
         *
         * @param attributes the attributes for the activity node.
         */
        private fun processActivityNode(attributes: Attributes?) {
            val manifestData = mManifestData!!
            // lets get the activity name, and add it to the list
            var activityName = getAttributeValue(
                attributes, AndroidManifest.ATTRIBUTE_NAME,
                true /* hasNamespace */
            )
            if (activityName != null) {
                activityName = combinePackageAndClassName(manifestData.mPackage, activityName)

                // get the exported flag.
                val exportedStr = getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_EXPORTED, true
                )
                val exported = exportedStr == null ||
                        exportedStr.lowercase(Locale.US) == "true" //$NON-NLS-1$
                val activity = ManifestData.Activity(activityName, exported)
                mCurrentActivity = activity
                manifestData.mActivities.add(activity)

                mErrorHandler?.checkClass(
                    mLocator, activityName, SdkConstants.CLASS_ACTIVITY,
                    true /* testVisibility */
                )
            } else {
                // no activity found! Aapt will output an error,
                // so we don't have to do anything
                mCurrentActivity = null
            }

            var processName = getAttributeValue(
                attributes, AndroidManifest.ATTRIBUTE_PROCESS,
                true /* hasNamespace */
            )
            if (processName != null) {
                manifestData.addProcessName(processName)
            }

            if (processName == null || processName.isEmpty()) {
                processName = manifestData.defaultProcess
            }

            if (activityName != null) {
                manifestData.mKeepClasses.add(
                    ManifestData.KeepClass(
                        activityName, processName, AndroidManifest.NODE_ACTIVITY
                    )
                )
            }
        }

        /**
         * Processes the service/receiver/provider nodes.
         *
         * @param attributes the attributes for the activity node.
         * @param superClassName the fully qualified name of the super class that this
         * @param localName the tag of the node node is representing
         */
        private fun processNode(
            attributes: Attributes?,
            superClassName: String?,
            localName: String?
        ) {
            val manifestData = mManifestData!!
            // lets get the class name, and check it if required.
            var serviceName = getAttributeValue(
                attributes, AndroidManifest.ATTRIBUTE_NAME,
                true /* hasNamespace */
            )
            if (serviceName != null) {
                serviceName = combinePackageAndClassName(manifestData.mPackage, serviceName)

                mErrorHandler?.checkClass(
                    mLocator, serviceName, superClassName,
                    false /* testVisibility */
                )
            }

            var processName = getAttributeValue(
                attributes, AndroidManifest.ATTRIBUTE_PROCESS,
                true /* hasNamespace */
            )
            if (processName != null) {
                manifestData.addProcessName(processName)
            }

            if (processName == null || processName.isEmpty()) {
                processName = manifestData.defaultProcess
            }

            if (serviceName != null) {
                manifestData.mKeepClasses.add(
                    ManifestData.KeepClass(serviceName, processName, localName)
                )
            }
        }

        /**
         * Processes the instrumentation node.
         * @param attributes the attributes for the instrumentation node.
         */
        private fun processInstrumentationNode(attributes: Attributes?) {
            val manifestData = mManifestData!!
            // lets get the class name, and check it if required.
            val instrumentationName = getAttributeValue(
                attributes,
                AndroidManifest.ATTRIBUTE_NAME,
                true /* hasNamespace */
            )
            if (instrumentationName != null) {
                val instrClassName =
                    combinePackageAndClassName(manifestData.mPackage, instrumentationName)
                val targetPackage = getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_TARGET_PACKAGE,
                    true /* hasNamespace */
                )
                manifestData.mInstrumentations.add(
                    ManifestData.Instrumentation(instrClassName, targetPackage)
                )
                manifestData.mKeepClasses.add(
                    ManifestData.KeepClass(
                        instrClassName, null, AndroidManifest.NODE_INSTRUMENTATION
                    )
                )
                mErrorHandler?.checkClass(
                    mLocator, instrClassName,
                    SdkConstants.CLASS_INSTRUMENTATION, true /* testVisibility */
                )
            }
        }

        /**
         * Processes the supports-screens node.
         * @param attributes the attributes for the supports-screens node.
         */
        private fun processSupportsScreensNode(attributes: Attributes?) {
            val manifestData = mManifestData!!
            val supportsScreens = ManifestData.SupportsScreens()
            manifestData.mSupportsScreensFromManifest = supportsScreens

            supportsScreens.setResizeable(
                getAttributeBooleanValue(
                    attributes, AndroidManifest.ATTRIBUTE_RESIZEABLE, true /*hasNamespace*/
                )
            )

            supportsScreens.setAnyDensity(
                getAttributeBooleanValue(
                    attributes, AndroidManifest.ATTRIBUTE_ANYDENSITY, true /*hasNamespace*/
                )
            )

            supportsScreens.setSmallScreens(
                getAttributeBooleanValue(
                    attributes, AndroidManifest.ATTRIBUTE_SMALLSCREENS, true /*hasNamespace*/
                )
            )

            supportsScreens.setNormalScreens(
                getAttributeBooleanValue(
                    attributes, AndroidManifest.ATTRIBUTE_NORMALSCREENS, true /*hasNamespace*/
                )
            )

            supportsScreens.setLargeScreens(
                getAttributeBooleanValue(
                    attributes, AndroidManifest.ATTRIBUTE_LARGESCREENS, true /*hasNamespace*/
                )
            )
        }

        /**
         * Processes the supports-screens node.
         * @param attributes the attributes for the supports-screens node.
         */
        private fun processUsesConfiguration(attributes: Attributes?) {
            val manifestData = mManifestData!!
            val usesConfiguration = ManifestData.UsesConfiguration()
            manifestData.mUsesConfiguration = usesConfiguration

            usesConfiguration.mReqFiveWayNav = getAttributeBooleanValue(
                attributes,
                AndroidManifest.ATTRIBUTE_REQ_5WAYNAV, true /*hasNamespace*/
            )
            usesConfiguration.mReqNavigation = Navigation.getEnum(
                getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_REQ_NAVIGATION, true /*hasNamespace*/
                )
            )
            usesConfiguration.mReqHardKeyboard = getAttributeBooleanValue(
                attributes,
                AndroidManifest.ATTRIBUTE_REQ_HARDKEYBOARD, true /*hasNamespace*/
            )
            usesConfiguration.mReqKeyboardType = Keyboard.getEnum(
                getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_REQ_KEYBOARDTYPE, true /*hasNamespace*/
                )
            )
            usesConfiguration.mReqTouchScreen = TouchScreen.getEnum(
                getAttributeValue(
                    attributes,
                    AndroidManifest.ATTRIBUTE_REQ_TOUCHSCREEN, true /*hasNamespace*/
                )
            )
        }

        /**
         * Searches through the attributes list for a particular one and returns its value.
         *
         * @param attributes the attribute list to search through
         * @param attributeName the name of the attribute to look for.
         * @param hasNamespace indicates whether the attribute has an android namespace.
         * @return a String with the value or null if the attribute was not found.
         * @see SdkConstants.ANDROID_URI
         */
        private fun getAttributeValue(
            attributes: Attributes?, attributeName: String, hasNamespace: Boolean
        ): String? {
            val count = attributes!!.length
            for (i in 0 until count) {
                if (attributeName == attributes.getLocalName(i)
                    && ((hasNamespace && SdkConstants.ANDROID_URI == attributes.getURI(i))
                            || (!hasNamespace && attributes.getURI(i).isEmpty()))
                ) {
                    return attributes.getValue(i)
                }
            }

            return null
        }

        /**
         * Searches through the attributes list for a particular one and returns its value as a
         * Boolean. If the attribute is not present, this will return null.
         *
         * @param attributes the attribute list to search through
         * @param attributeName the name of the attribute to look for.
         * @param hasNamespace indicates whether the attribute has an android namespace.
         * @return a String with the value or null if the attribute was not found.
         * @see SdkConstants.ANDROID_URI
         */
        private fun getAttributeBooleanValue(
            attributes: Attributes?, attributeName: String, hasNamespace: Boolean
        ): Boolean? {
            val count = attributes!!.length
            for (i in 0 until count) {
                if (attributeName == attributes.getLocalName(i)
                    && ((hasNamespace && SdkConstants.ANDROID_URI == attributes.getURI(i))
                            || (!hasNamespace && attributes.getURI(i).isEmpty()))
                ) {
                    val attr = attributes.getValue(i)
                    return if (attr != null) {
                        java.lang.Boolean.valueOf(attr)
                    } else {
                        null
                    }
                }
            }

            return null
        }

        companion object {
            /**
             * Combines a java package, with a class value from the manifest to make a fully qualified
             * class name
             *
             * @param javaPackage the java package from the manifest.
             * @param className the class name from the manifest.
             * @return the fully qualified class name.
             */
            private fun combinePackageAndClassName(
                javaPackage: String?, className: String?
            ): String? {
                if (className == null || className.isEmpty()) {
                    return javaPackage
                }
                if (javaPackage == null || javaPackage.isEmpty()) {
                    return className
                }

                // the class name can be a subpackage (starts with a '.'
                // char), a simple class name (no dot), or a full java package
                val startWithDot = (className[0] == '.')
                val hasDot = (className.indexOf('.') != -1)
                if (startWithDot || !hasDot) {

                    // add the concatenation of the package and class name
                    return if (startWithDot) {
                        javaPackage + className
                    } else {
                        "$javaPackage.$className"
                    }
                } else {
                    // just add the class as it should be a fully qualified java name.
                    return className
                }
            }
        }
    }

    companion object {
        private const val LEVEL_TOP = 0
        private const val LEVEL_INSIDE_MANIFEST = 1
        private const val LEVEL_INSIDE_APPLICATION = 2
        private const val LEVEL_INSIDE_APP_COMPONENT = 3
        private const val LEVEL_INSIDE_INTENT_FILTER = 4

        private const val ACTION_MAIN = "android.intent.action.MAIN" //$NON-NLS-1$
        private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER" //$NON-NLS-1$

        private val sParserFactory: SAXParserFactory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // XmlUtils.configureSaxFactory(sParserFactory, true, false);
        }

        /**
         * Parses the Android Manifest, and returns a [ManifestData] object containing the result
         * of the parsing.
         *
         * @param manifestFile the [IAbstractFile] representing the manifest file.
         * @param gatherData indicates whether the parsing will extract data from the manifest. If false
         *     the method will always return null.
         * @param errorHandler an optional errorHandler.
         * @return A class containing the manifest info obtained during the parsing, or null on error.
         * @throws IOException If there was a problem parsing the file
         * @throws SAXException If any SAX errors occurred during processing.
         */
        @JvmStatic
        @Throws(IOException::class, SAXException::class)
        fun parse(
            manifestFile: IAbstractFile?, gatherData: Boolean, errorHandler: ManifestErrorHandler?
        ): ManifestData? {
            if (manifestFile != null) {
                val parser: SAXParser
                try {
                    parser = sParserFactory.newSAXParser()
                    // XmlUtils.createSaxParser(sParserFactory);
                } catch (e: ParserConfigurationException) {
                    throw RuntimeException(e)
                } catch (e: SAXException) {
                    throw RuntimeException(e)
                }

                var data: ManifestData? = null
                if (gatherData) {
                    data = ManifestData()
                }

                val manifestHandler = ManifestHandler(data, errorHandler)

                try {
                    manifestFile.getContents().use { `is` ->
                        parser.parse(InputSource(`is`), manifestHandler)
                    }
                } catch (e: StreamException) {
                    throw IOException(e)
                }

                return data
            }

            return null
        }

        /**
         * Parses the Android Manifest, and returns an object containing the result of the parsing.
         *
         * <p>This is the equivalent of calling `parse(manifestFile, true, null)`.
         *
         * @param manifestFile the manifest file to parse.
         */
        @JvmStatic
        @Throws(IOException::class, SAXException::class)
        fun parse(manifestFile: IAbstractFile?): ManifestData? {
            return parse(manifestFile, true, null)
        }

        /**
         * Parses the Android Manifest from an [InputStream], and returns a [ManifestData]
         * object containing the result of the parsing.
         *
         * @param manifestFileStream the [InputStream] representing the manifest file.
         * @return A class containing the manifest info obtained during the parsing or null on error.
         */
        @JvmStatic
        @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
        fun parse(manifestFileStream: InputStream?): ManifestData? {
            if (manifestFileStream != null) {
                val parser = sParserFactory.newSAXParser()
                //XmlUtils.createSaxParser(sParserFactory);

                val data = ManifestData()

                val manifestHandler = ManifestHandler(data, null)
                parser.parse(InputSource(manifestFileStream), manifestHandler)

                return data
            }

            return null
        }

        @JvmStatic
        @Throws(IOException::class)
        fun parse(manifestFile: File): ManifestData {
            try {
                BufferedInputStream(FileInputStream(manifestFile)).use { `is` ->
                    return parse(`is`)!!
                }
            } catch (e: SAXException) {
                throw IOException(e)
            } catch (e: ParserConfigurationException) {
                throw IOException(e)
            }
        }
    }
}
