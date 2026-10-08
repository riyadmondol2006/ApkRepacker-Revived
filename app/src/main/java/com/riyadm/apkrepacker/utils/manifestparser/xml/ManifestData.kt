/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.riyadm.apkrepacker.utils.manifestparser.resources.Keyboard
import com.riyadm.apkrepacker.utils.manifestparser.resources.Navigation
import com.riyadm.apkrepacker.utils.manifestparser.resources.TouchScreen
import java.util.TreeSet

/**
 * Class containing the manifest info obtained during the parsing.
 */
class ManifestData {

    /** Application package */
    internal var mPackage: String? = null
    /** Application version code, null if the attribute is not present. */
    internal var mVersionCode: Int? = null
    /** Application version name, null if the attribute is not present. */
    internal var mVersionName: String? = null
    /** Default Dex process */
    internal var mDefaultProcess: String? = null
    /** List of all activities */
    internal val mActivities = ArrayList<Activity>()
    /** List of all activities, services, receivers and providers to keep for Proguard and Dex * */
    internal val mKeepClasses = ArrayList<KeepClass>()
    /** Launcher activity */
    internal var mLauncherActivity: Activity? = null
    /** list of process names declared by the manifest */
    internal var mProcesses: MutableSet<String>? = null
    /** debuggable attribute value. If null, the attribute is not present. */
    internal var mDebuggable: Boolean? = null
    /** API level requirement. Default is 1 even if missing. If value is a codename, then it'll be
     * 0 instead. */
    private var mMinSdkVersion = 1
    private var mTargetSdkVersion = 0
    /** List of all instrumentations declared by the manifest */
    internal val mInstrumentations = ArrayList<Instrumentation>()
    /** List of all libraries in use declared by the manifest */
    internal val mLibraries = ArrayList<UsesLibrary>()
    /** List of all feature in use declared by the manifest */
    internal val mFeatures = ArrayList<UsesFeature>()

    internal var mSupportsScreensFromManifest: SupportsScreens? = null
    internal var mSupportsScreensValues: SupportsScreens? = null
    internal var mUsesConfiguration: UsesConfiguration? = null

    /**
     * Instrumentation info obtained from manifest
     */
    class Instrumentation internal constructor(
        private val mName: String?,
        private val mTargetPackage: String?
    ) {
        /**
         * Returns the fully qualified instrumentation class name
         */
        val name: String?
            get() = mName

        /**
         * Returns the Android app package that is the target of this instrumentation
         */
        val targetPackage: String?
            get() = mTargetPackage
    }

    /**
     * Activity info obtained from the manifest.
     */
    class Activity(private val mName: String?, private val mIsExported: Boolean) {
        private var mHasAction = false
        private var mHasMainAction = false
        private var mHasLauncherCategory = false

        val name: String?
            get() = mName

        val isExported: Boolean
            get() = mIsExported

        fun hasAction(): Boolean {
            return mHasAction
        }

        val isHomeActivity: Boolean
            get() = mHasMainAction && mHasLauncherCategory

        internal fun setHasAction(hasAction: Boolean) {
            mHasAction = hasAction
        }

        /** If the activity doesn't yet have a filter set for the launcher, this resets both
         * flags. This is to handle multiple intent-filters where one could have the valid
         * action, and another one of the valid category.
         */
        internal fun resetIntentFilter() {
            if (isHomeActivity == false) {
                mHasLauncherCategory = false
                mHasMainAction = false
            }
        }

        internal fun setHasMainAction(hasMainAction: Boolean) {
            mHasMainAction = hasMainAction
        }

        internal fun setHasLauncherCategory(hasLauncherCategory: Boolean) {
            mHasLauncherCategory = hasLauncherCategory
        }
    }

    class KeepClass(
        private val mName: String?,
        private val mProcess: String?,
        private val mType: String?
    ) {
        val name: String?
            get() = mName

        val process: String?
            get() = mProcess

        val type: String?
            get() = mType
    }

    /**
     * Class representing the <code>supports-screens</code> node in the manifest.
     * By default, all the getters will return null if there was no value defined in the manifest.
     *
     * To get an instance with all the actual values, use [resolveSupportsScreensValues]
     */
    class SupportsScreens {
        private var mResizeable: Boolean? = null
        private var mAnyDensity: Boolean? = null
        private var mSmallScreens: Boolean? = null
        private var mNormalScreens: Boolean? = null
        private var mLargeScreens: Boolean? = null

        constructor()

        /**
         * Instantiate an instance from a string. The string must have been created with
         * [getEncodedValues].
         * @param value the string.
         */
        constructor(value: String) {
            val values = value.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            mAnyDensity = java.lang.Boolean.valueOf(values[0])
            mResizeable = java.lang.Boolean.valueOf(values[1])
            mSmallScreens = java.lang.Boolean.valueOf(values[2])
            mNormalScreens = java.lang.Boolean.valueOf(values[3])
            mLargeScreens = java.lang.Boolean.valueOf(values[4])
        }

        /**
         * Returns a version of the receiver for which all values have been set, even if they
         * were not present in the manifest.
         * @param targetSdkVersion the target api level of the app, since this has an effect
         * on default values.
         */
        fun resolveSupportsScreensValues(targetSdkVersion: Int): SupportsScreens {
            val result = getDefaultValues(targetSdkVersion)

            // Override the default with the existing values:
            if (mResizeable != null) result.mResizeable = mResizeable
            if (mAnyDensity != null) result.mAnyDensity = mAnyDensity
            if (mSmallScreens != null) result.mSmallScreens = mSmallScreens
            if (mNormalScreens != null) result.mNormalScreens = mNormalScreens
            if (mLargeScreens != null) result.mLargeScreens = mLargeScreens

            return result
        }

        /**
         * returns the value of the <code>resizeable</code> attribute or null if not present.
         */
        val resizeable: Boolean?
            get() = mResizeable

        internal fun setResizeable(resizeable: Boolean?) {
            mResizeable = getConstantBoolean(resizeable)
        }

        /**
         * returns the value of the <code>anyDensity</code> attribute or null if not present.
         */
        val anyDensity: Boolean?
            get() = mAnyDensity

        internal fun setAnyDensity(anyDensity: Boolean?) {
            mAnyDensity = getConstantBoolean(anyDensity)
        }

        /**
         * returns the value of the <code>smallScreens</code> attribute or null if not present.
         */
        val smallScreens: Boolean?
            get() = mSmallScreens

        internal fun setSmallScreens(smallScreens: Boolean?) {
            mSmallScreens = getConstantBoolean(smallScreens)
        }

        /**
         * returns the value of the <code>normalScreens</code> attribute or null if not present.
         */
        val normalScreens: Boolean?
            get() = mNormalScreens

        internal fun setNormalScreens(normalScreens: Boolean?) {
            mNormalScreens = getConstantBoolean(normalScreens)
        }

        /**
         * returns the value of the <code>largeScreens</code> attribute or null if not present.
         */
        val largeScreens: Boolean?
            get() = mLargeScreens

        internal fun setLargeScreens(largeScreens: Boolean?) {
            mLargeScreens = getConstantBoolean(largeScreens)
        }

        /**
         * Returns either [java.lang.Boolean.TRUE] or [java.lang.Boolean.FALSE] based on the value of
         * the given Boolean object.
         */
        private fun getConstantBoolean(v: Boolean?): Boolean? {
            if (v != null) {
                return if (v == true) {
                    java.lang.Boolean.TRUE
                } else {
                    java.lang.Boolean.FALSE
                }
            }

            return null
        }

        override fun equals(other: Any?): Boolean {
            if (other is SupportsScreens) {
                // since all the fields are guaranteed to be either Boolean.TRUE or Boolean.FALSE
                // (or null), we can simply check they are identical and not bother with
                // calling equals (which would require to check != null.
                // see #getConstanntBoolean(Boolean)
                return mResizeable == other.mResizeable &&
                        mAnyDensity == other.mAnyDensity &&
                        mSmallScreens == other.mSmallScreens &&
                        mNormalScreens == other.mNormalScreens &&
                        mLargeScreens == other.mLargeScreens
            }

            return false
        }

        /* Override hashCode, mostly to make Eclipse happy and not warn about it.
         * And if you ever put this in a Map or Set, it will avoid surprises. */
        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + (mAnyDensity?.hashCode() ?: 0)
            result = prime * result + (mLargeScreens?.hashCode() ?: 0)
            result = prime * result + (mNormalScreens?.hashCode() ?: 0)
            result = prime * result + (mResizeable?.hashCode() ?: 0)
            result = prime * result + (mSmallScreens?.hashCode() ?: 0)
            return result
        }

        /**
         * Returns true if the two instances support the same screen sizes.
         * This is similar to [equals] except that it ignores the values of
         * [anyDensity] and [resizeable].
         * @param support the other instance to compare to.
         * @return true if the two instances support the same screen sizes.
         */
        fun hasSameScreenSupportAs(support: SupportsScreens): Boolean {
            // since all the fields are guaranteed to be either Boolean.TRUE or Boolean.FALSE
            // (or null), we can simply check they are identical and not bother with
            // calling equals (which would require to check != null.
            // see #getConstanntBoolean(Boolean)

            // This only checks that matter here are the screen sizes. resizeable and anyDensity
            // are not checked.
            return mSmallScreens == support.mSmallScreens &&
                    mNormalScreens == support.mNormalScreens &&
                    mLargeScreens == support.mLargeScreens
        }

        /**
         * Returns true if the two instances have strictly different screen size support.
         * This means that there is no screen size that they both support.
         * @param support the other instance to compare to.
         * @return true if they are strictly different.
         */
        fun hasStrictlyDifferentScreenSupportAs(support: SupportsScreens): Boolean {
            // since all the fields are guaranteed to be either Boolean.TRUE or Boolean.FALSE
            // (or null), we can simply check they are identical and not bother with
            // calling equals (which would require to check != null.
            // see #getConstanntBoolean(Boolean)

            // This only checks that matter here are the screen sizes. resizeable and anyDensity
            // are not checked.
            return (mSmallScreens != true || support.mSmallScreens != true) &&
                    (mNormalScreens != true || support.mNormalScreens != true) &&
                    (mLargeScreens != true || support.mLargeScreens != true)
        }

        /**
         * Comparison of 2 Supports-screens. This only uses screen sizes (ignores resizeable and
         * anyDensity), and considers that
         * [hasStrictlyDifferentScreenSupportAs] returns true and
         * [overlapWith] returns false.
         * @throws IllegalArgumentException if the two instanced are not strictly different or
         * overlap each other
         * @see hasStrictlyDifferentScreenSupportAs
         * @see overlapWith
         */
        fun compareScreenSizesWith(o: SupportsScreens): Int {
            if (hasStrictlyDifferentScreenSupportAs(o) == false) {
                throw IllegalArgumentException("The two instances are not strictly different.")
            }
            if (overlapWith(o)) {
                throw IllegalArgumentException("The two instances overlap each other.")
            }

            var comp = mLargeScreens!!.compareTo(o.mLargeScreens!!)
            if (comp != 0) return comp

            comp = mNormalScreens!!.compareTo(o.mNormalScreens!!)
            if (comp != 0) return comp

            comp = mSmallScreens!!.compareTo(o.mSmallScreens!!)
            if (comp != 0) return comp

            return 0
        }

        /**
         * Returns a string encoding of the content of the instance. This string can be used to
         * instantiate a [SupportsScreens] object through
         * [SupportsScreens(String)][SupportsScreens].
         */
        fun getEncodedValues(): String {
            return String.format(
                "%1\$s|%2\$s|%3\$s|%4\$s|%5\$s",
                mAnyDensity, mResizeable, mSmallScreens, mNormalScreens, mLargeScreens
            )
        }

        override fun toString(): String {
            val sb = StringBuilder()

            var alreadyOutputSomething = false

            if (true == mSmallScreens) {
                alreadyOutputSomething = true
                sb.append("small")
            }

            if (true == mNormalScreens) {
                if (alreadyOutputSomething) {
                    sb.append(", ")
                }
                alreadyOutputSomething = true
                sb.append("normal")
            }

            if (true == mLargeScreens) {
                if (alreadyOutputSomething) {
                    sb.append(", ")
                }
                alreadyOutputSomething = true
                sb.append("large")
            }

            if (alreadyOutputSomething == false) {
                sb.append("<none>")
            }

            return sb.toString()
        }

        /**
         * Returns true if the two instance overlap with each other.
         * This can happen if one instances supports a size, when the other instance doesn't while
         * supporting a size above and a size below.
         * @param otherSS the other supports-screens to compare to.
         */
        fun overlapWith(otherSS: SupportsScreens): Boolean {
            if (mSmallScreens == null || mNormalScreens == null || mLargeScreens == null ||
                otherSS.mSmallScreens == null || otherSS.mNormalScreens == null ||
                otherSS.mLargeScreens == null
            ) {
                throw IllegalArgumentException("Some screen sizes Boolean are not initialized")
            }

            if (mSmallScreens == true && mNormalScreens == false &&
                mLargeScreens == true
            ) {
                return otherSS.mNormalScreens == true
            }

            if (otherSS.mSmallScreens == true && otherSS.mNormalScreens == false &&
                otherSS.mLargeScreens == true
            ) {
                return mNormalScreens == true
            }

            return false
        }

        companion object {
            /**
             * Returns an instance of [SupportsScreens] initialized with the default values
             * based on the given targetSdkVersion.
             * @param targetSdkVersion
             */
            @JvmStatic
            fun getDefaultValues(targetSdkVersion: Int): SupportsScreens {
                val result = SupportsScreens()

                result.mNormalScreens = java.lang.Boolean.TRUE
                // Screen size and density became available in Android 1.5/API3, so before that
                // non normal screens were not supported by default. After they are considered
                // supported.
                val v = if (targetSdkVersion <= 3) java.lang.Boolean.FALSE else java.lang.Boolean.TRUE
                result.mLargeScreens = v
                result.mSmallScreens = v
                result.mAnyDensity = v
                result.mResizeable = v

                return result
            }
        }
    }

    /**
     * Class representing a <code>uses-library</code> node in the manifest.
     */
    class UsesLibrary {
        internal var mName: String? = null
        internal var mRequired: Boolean? = java.lang.Boolean.TRUE // default is true even if missing

        val name: String?
            get() = mName

        val required: Boolean?
            get() = mRequired
    }

    /**
     * Class representing a <code>uses-feature</code> node in the manifest.
     */
    class UsesFeature {
        internal var mName: String? = null
        internal var mGlEsVersion = 0
        internal var mRequired: Boolean? = java.lang.Boolean.TRUE  // default is true even if missing

        val name: String?
            get() = mName

        /**
         * Returns the value of the glEsVersion attribute, or 0 if the attribute was not present.
         */
        val glEsVersion: Int
            get() = mGlEsVersion

        val required: Boolean?
            get() = mRequired
    }

    /**
     * Class representing the <code>uses-configuration</code> node in the manifest.
     */
    class UsesConfiguration {
        internal var mReqFiveWayNav: Boolean? = null
        internal var mReqHardKeyboard: Boolean? = null
        internal var mReqKeyboardType: Keyboard? = null
        internal var mReqTouchScreen: TouchScreen? = null
        internal var mReqNavigation: Navigation? = null

        /**
         * returns the value of the <code>reqFiveWayNav</code> attribute or null if not present.
         */
        val reqFiveWayNav: Boolean?
            get() = mReqFiveWayNav

        /**
         * returns the value of the <code>reqNavigation</code> attribute or null if not present.
         */
        val reqNavigation: Navigation?
            get() = mReqNavigation

        /**
         * returns the value of the <code>reqHardKeyboard</code> attribute or null if not present.
         */
        val reqHardKeyboard: Boolean?
            get() = mReqHardKeyboard

        /**
         * returns the value of the <code>reqKeyboardType</code> attribute or null if not present.
         */
        val reqKeyboardType: Keyboard?
            get() = mReqKeyboardType

        /**
         * returns the value of the <code>reqTouchScreen</code> attribute or null if not present.
         */
        val reqTouchScreen: TouchScreen?
            get() = mReqTouchScreen
    }

    /**
     * Returns the package defined in the manifest, if found.
     * @return The package name or null if not found.
     */
    val `package`: String?
        get() = mPackage

    /**
     * Returns the versionCode value defined in the manifest, if found, null otherwise.
     * @return the versionCode or null if not found.
     */
    val versionCode: Int?
        get() = mVersionCode

    /**
     * Returns the versionName value defined in the manifest, if found, null otherwise.
     *
     * @return the versionName or null if not found.
     */
    val versionName: String?
        get() = mVersionName

    /**
     * Returns the list of activities found in the manifest.
     * @return An array of fully qualified class names, or empty if no activity were found.
     */
    val activities: Array<Activity>
        get() = mActivities.toTypedArray()

    /**
     * Returns the list of activities, services, receivers and providers found in the manifest.
     *
     * @return An array of fully qualified class names, or empty if no classes to keep were found.
     */
    val keepClasses: Array<KeepClass>
        get() = mKeepClasses.toTypedArray()

    /**
     * Returns the name of one activity found in the manifest, that is configured to show up in the
     * HOME screen.
     *
     * @return the fully qualified name of a HOME activity or null if none were found.
     */
    val launcherActivity: Activity?
        get() = mLauncherActivity

    /**
     * Returns the list of process names declared by the manifest.
     */
    val processes: Array<String>
        get() {
            val processes = mProcesses
            if (processes != null) {
                return processes.toTypedArray()
            }

            return arrayOf()
        }

    val defaultProcess: String?
        get() = mDefaultProcess

    /**
     * Returns the <code>debuggable</code> attribute value or null if it is not set.
     */
    val debuggable: Boolean?
        get() = mDebuggable

    /**
     * API level requirement. if null the attribute was not present.
     * Returns the <code>minSdkVersion</code> attribute, or null if it's not set.
     */
    var minSdkVersionString: String? = null
        /**
         * Sets the value of the <code>minSdkVersion</code> attribute.
         * @param minSdkVersion the string value of the attribute in the manifest.
         */
        set(minSdkVersion) {
            field = minSdkVersion
            if (minSdkVersion != null) {
                mMinSdkVersion = try {
                    Integer.parseInt(minSdkVersion)
                } catch (e: NumberFormatException) {
                    MIN_SDK_CODENAME
                }
            }
        }

    /**
     * Returns the <code>minSdkVersion</code> attribute, or 0 if it's not set or is a codename.
     * @see minSdkVersionString
     */
    val minSdkVersion: Int
        get() = mMinSdkVersion

    /**
     * Sets the value of the <code>minSdkVersion</code> attribute.
     * @param targetSdkVersion the string value of the attribute in the manifest.
     */
    fun setTargetSdkVersionString(targetSdkVersion: String?) {
        if (targetSdkVersion != null) {
            try {
                mTargetSdkVersion = Integer.parseInt(targetSdkVersion)
            } catch (e: NumberFormatException) {
                // keep the value at 0.
            }
        }
    }

    /**
     * Returns the <code>targetSdkVersion</code> attribute, or the same value as
     * [minSdkVersion] if it was not set in the manifest.
     */
    val targetSdkVersion: Int
        get() {
            if (mTargetSdkVersion == 0) {
                return minSdkVersion
            }

            return mTargetSdkVersion
        }

    /**
     * Returns the list of instrumentations found in the manifest.
     * @return An array of [Instrumentation], or empty if no instrumentations were
     * found.
     */
    val instrumentations: Array<Instrumentation>
        get() = mInstrumentations.toTypedArray()

    /**
     * Returns the list of libraries in use found in the manifest.
     * @return An array of [UsesLibrary] objects, or empty if no libraries were found.
     */
    val usesLibraries: Array<UsesLibrary>
        get() = mLibraries.toTypedArray()

    /**
     * Returns the list of features in use found in the manifest.
     * @return An array of [UsesFeature] objects, or empty if no libraries were found.
     */
    val usesFeatures: Array<UsesFeature>
        get() = mFeatures.toTypedArray()

    /**
     * Returns the glEsVersion from a `<uses-feature>` or [GL_ES_VERSION_NOT_SET]
     * if not set.
     */
    val glEsVersion: Int
        get() {
            for (feature in mFeatures) {
                if (feature.mGlEsVersion > 0) {
                    return feature.mGlEsVersion
                }
            }
            return GL_ES_VERSION_NOT_SET
        }

    /**
     * Returns the [SupportsScreens] object representing the <code>supports-screens</code>
     * node, or null if the node doesn't exist at all.
     * Some values in the [SupportsScreens] instance maybe null, indicating that they
     * were not present in the manifest. To get an instance that contains the values, as seen
     * by the Android platform when the app is running, use [supportsScreensValues].
     */
    val supportsScreensFromManifest: SupportsScreens?
        get() = mSupportsScreensFromManifest

    /**
     * Returns an always non-null instance of [SupportsScreens] that's been initialized with
     * the default values, and the values from the manifest.
     * The default values depends on the manifest values for minSdkVersion and targetSdkVersion.
     */
    val supportsScreensValues: SupportsScreens
        @Synchronized
        get() {
            var values = mSupportsScreensValues
            if (values == null) {
                val fromManifest = mSupportsScreensFromManifest
                values = if (fromManifest == null) {
                    SupportsScreens.getDefaultValues(targetSdkVersion)
                } else {
                    // get a SupportsScreen that replace the missing values with default values.
                    fromManifest.resolveSupportsScreensValues(targetSdkVersion)
                }
                mSupportsScreensValues = values
            }

            return values
        }

    /**
     * Returns the [UsesConfiguration] object representing the <code>uses-configuration</code>
     * node, or null if the node doesn't exist at all.
     */
    val usesConfiguration: UsesConfiguration?
        get() = mUsesConfiguration

    internal fun addProcessName(processName: String) {
        var processes = mProcesses
        if (processes == null) {
            processes = TreeSet()
            mProcesses = processes
        }

        if (processName.startsWith(":")) {
            processes.add(mPackage + processName)
        } else {
            processes.add(processName)
        }
    }

    companion object {
        /**
         * Value returned by [getMinSdkVersion] when the value of the minSdkVersion attribute
         * in the manifest is a codename and not an integer value.
         */
        const val MIN_SDK_CODENAME = 0

        /**
         * Value returned by [getGlEsVersion] when there are no `<uses-feature>` node
         * with the attribute glEsVersion set.
         */
        const val GL_ES_VERSION_NOT_SET = -1
    }
}
