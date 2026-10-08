package com.riyadm.apkrepacker.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.jecelyin.editor.v2.EditorPreferences
import com.riyadm.apkrepacker.ui.motion.enableExpressiveTransitions
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.EdgeToEdgeUtils
import com.riyadm.apkrepacker.utils.Theme
import com.riyadm.apkrepacker.utils.ViewDeviceUtils

/**
 * Base of every screen: applies the selected Material 3 Expressive theme (recreating the
 * activity when it changes), goes edge-to-edge and applies the screen preferences.
 */
@SuppressLint("Registered")
open class BaseActivity : AppCompatActivity() {

    private lateinit var appliedTheme: Theme.ThemeDescriptor

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        appliedTheme = Theme.apply(this)
        if (appliedTheme.isDynamic) DynamicColors.applyToActivityIfAvailable(this)
        Theme.observe(this, this) { theme ->
            if (theme != appliedTheme) recreate()
        }

        super.onCreate(savedInstanceState)
        enableExpressiveTransitions()
        EdgeToEdgeUtils.enable(this)
        ViewDeviceUtils.setFullScreenMode(this, isFullScreenMode())
        ViewDeviceUtils.toggledScreenOn(this, PreferenceHelper.getInstance(this).isKeepScreenOn)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        EdgeToEdgeUtils.applyContentInsets(this)
    }

    /**
     * Runs the default back action (pop the fragment back stack, or finish) past [callback].
     * With predictive back, activities handle back with OnBackPressedCallbacks and use this to
     * fall through to whatever is registered below them.
     */
    protected fun performDefaultBack(callback: OnBackPressedCallback) {
        callback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        callback.isEnabled = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    protected open fun isFullScreenMode(): Boolean =
        EditorPreferences.getInstance(this).isFullScreenMode
}
