package com.riyadm.apkrepacker.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys

class Theme private constructor(c: Context) {

    enum class Mode {
        /**
         * Use a single selected theme
         */
        CONCRETE,

        /**
         * Choose between two selected light and dark themes depending on system theme (Android Q+)
         */
        AUTO_LIGHT_DARK
    }

    private val mContext: Context = c

    private val mPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)

    private val mThemes: MutableList<ThemeDescriptor>

    private val mLiveTheme = MutableLiveData<ThemeDescriptor>()

    private var mMode: Mode

    init {
        val defaultMode = if (AppUtils.apiIsAtLeast(Build.VERSION_CODES.Q)) Mode.AUTO_LIGHT_DARK else Mode.CONCRETE
        mMode = mPrefs.getString(PreferenceKeys.KEY_THEME_MODE, null)
            ?.let { saved -> Mode.entries.firstOrNull { it.name == saved } }
            ?: defaultMode

        mThemes = ArrayList()
        mThemes.add(ThemeDescriptor(0, R.style.Theme_Revived_Light, R.string.theme_light, false))
        mThemes.add(ThemeDescriptor(1, R.style.Theme_Revived_Dark, R.string.theme_dark, true))
        mThemes.add(ThemeDescriptor(2, R.style.Theme_Revived_RenaLight, R.string.theme_rena_light, false))
        mThemes.add(ThemeDescriptor(3, R.style.Theme_Revived_Rena, R.string.theme_rena, true))
        // Material You: colors come from the wallpaper (Android 12+), applied by BaseActivity.
        if (DynamicColors.isDynamicColorAvailable()) {
            mThemes.add(ThemeDescriptor(4, R.style.Theme_Revived_DynamicLight, R.string.theme_dynamic_light, false, true))
            mThemes.add(ThemeDescriptor(5, R.style.Theme_Revived_DynamicDark, R.string.theme_dynamic_dark, true, true))
        }

        invalidateLiveTheme()

        sInstance = this
    }

    val themes: List<ThemeDescriptor>
        get() = mThemes

    val currentTheme: ThemeDescriptor
        get() = when (mMode) {
            Mode.CONCRETE -> concreteTheme
            Mode.AUTO_LIGHT_DARK -> {
                if (shouldUseDarkThemeForAutoMode())
                    darkTheme
                else
                    lightTheme
            }
        }

    val liveTheme: LiveData<ThemeDescriptor>
        get() = mLiveTheme

    val themeMode: Mode
        get() = mMode

    fun setMode(mode: Mode) {
        if (mode == mMode)
            return

        mPrefs.edit().putString(PreferenceKeys.KEY_THEME_MODE, mode.name).apply()
        mMode = mode

        invalidateLiveTheme()
    }

    /** Java: getConcreteTheme()/setConcreteTheme(ThemeDescriptor) */
    var concreteTheme: ThemeDescriptor
        get() = getThemeDescriptorById(getThemeId(THEME_TAG_CONCRETE, DEFAULT_LIGHT_THEME_ID))
        set(theme) {
            saveThemeId(THEME_TAG_CONCRETE, theme.id)

            if (themeMode == Mode.CONCRETE)
                invalidateLiveTheme()
        }

    /** Method-style setter for Kotlin callers holding a nullable descriptor (NPE on null, like the Java original). */
    @JvmName("setConcreteThemeNullable")
    fun setConcreteTheme(theme: ThemeDescriptor?) {
        concreteTheme = theme!!
    }

    fun setConcreteTheme(theme: Int) {
        saveThemeId(THEME_TAG_CONCRETE, theme)

        if (themeMode == Mode.CONCRETE)
            invalidateLiveTheme()
    }

    /** Java: getLightTheme()/setLightTheme(ThemeDescriptor) */
    var lightTheme: ThemeDescriptor
        get() = getThemeDescriptorById(getThemeId(THEME_TAG_LIGHT, DEFAULT_LIGHT_THEME_ID))
        set(theme) {
            saveThemeId(THEME_TAG_LIGHT, theme.id)

            if (themeMode == Mode.AUTO_LIGHT_DARK && !shouldUseDarkThemeForAutoMode())
                invalidateLiveTheme()
        }

    /** Method-style setter for Kotlin callers holding a nullable descriptor (NPE on null, like the Java original). */
    @JvmName("setLightThemeNullable")
    fun setLightTheme(theme: ThemeDescriptor?) {
        lightTheme = theme!!
    }

    fun setLightTheme(theme: Int) {
        saveThemeId(THEME_TAG_LIGHT, theme)

        if (themeMode == Mode.AUTO_LIGHT_DARK && !shouldUseDarkThemeForAutoMode())
            invalidateLiveTheme()
    }

    /** Java: getDarkTheme()/setDarkTheme(ThemeDescriptor) */
    var darkTheme: ThemeDescriptor
        get() = getThemeDescriptorById(getThemeId(THEME_TAG_DARK, DEFAULT_DARK_THEME_ID))
        set(theme) {
            saveThemeId(THEME_TAG_DARK, theme.id)

            if (themeMode == Mode.AUTO_LIGHT_DARK && shouldUseDarkThemeForAutoMode())
                invalidateLiveTheme()
        }

    fun setDarkTheme(theme: Int) {
        saveThemeId(THEME_TAG_DARK, theme)

        if (themeMode == Mode.AUTO_LIGHT_DARK && shouldUseDarkThemeForAutoMode())
            invalidateLiveTheme()
    }

    /** Method-style setter for Kotlin callers holding a nullable descriptor (NPE on null, like the Java original). */
    @JvmName("setDarkThemeNullable")
    fun setDarkTheme(theme: ThemeDescriptor?) {
        darkTheme = theme!!
    }

    private fun shouldUseDarkThemeForAutoMode(): Boolean {
        return (mContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getThemeDescriptorById(themeId: Int): ThemeDescriptor {
        return mThemes.getOrElse(themeId) { mThemes[0] }
    }

    private fun getThemeId(themeTag: String, defaultThemeId: Int): Int {
        return mPrefs.getInt(PreferenceKeys.KEY_CURRENT_THEME + "." + themeTag, defaultThemeId)
    }

    private fun saveThemeId(themeTag: String, themeId: Int) {
        mPrefs.edit().putInt(PreferenceKeys.KEY_CURRENT_THEME + "." + themeTag, themeId).apply()
    }

    private fun invalidateLiveTheme() {
        val currentTheme = currentTheme
        if (currentTheme != mLiveTheme.value)
            mLiveTheme.value = currentTheme
    }

    class ThemeDescriptor internal constructor(
        val id: Int,
        @get:StyleRes val theme: Int,
        @StringRes private val mNameStringRes: Int,
        val isDark: Boolean,
        /** Colors are taken from the system wallpaper palette (see [DynamicColors]). */
        val isDynamic: Boolean = false
    ) {
        fun getName(c: Context): String {
            return c.getString(mNameStringRes)
        }

        override fun equals(other: Any?): Boolean {
            return other is ThemeDescriptor && other.id == id
        }

        override fun hashCode(): Int = id
    }

    companion object {
        private const val THEME_TAG_CONCRETE = "concrete"
        private const val THEME_TAG_LIGHT = "light"
        private const val THEME_TAG_DARK = "dark"

        private const val DEFAULT_LIGHT_THEME_ID = 0
        private const val DEFAULT_DARK_THEME_ID = 1

        private var sInstance: Theme? = null

        @JvmStatic
        fun getInstance(c: Context?): Theme {
            synchronized(Theme::class.java) {
                return sInstance ?: Theme(requireNotNull(c) { "Theme needs a Context on first use" })
            }
        }

        @JvmStatic
        fun apply(c: Context): ThemeDescriptor {
            val theme = getInstance(c)
            val currentTheme = theme.currentTheme
            c.setTheme(currentTheme.theme)

            // The system dark mode may have changed since the last call; re-publish so observers recreate.
            theme.invalidateLiveTheme()

            return currentTheme
        }

        /**
         * Convenience method for [getInstance].[liveTheme].[LiveData.observe]
         */
        @JvmStatic
        fun observe(c: Context?, owner: LifecycleOwner, observer: Observer<ThemeDescriptor>) {
            getInstance(c).liveTheme.observe(owner, observer)
        }
    }
}
