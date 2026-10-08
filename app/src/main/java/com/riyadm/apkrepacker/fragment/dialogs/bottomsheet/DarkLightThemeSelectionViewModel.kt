package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.riyadm.apkrepacker.utils.Theme

/** Holds the light / dark theme choices of the auto-theme sheet until they are applied. */
class DarkLightThemeSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val themeManager = Theme.getInstance(application)

    private val lightTheme = MutableLiveData(themeManager.lightTheme)
    private val darkTheme = MutableLiveData(themeManager.darkTheme)

    fun getLightTheme(): LiveData<Theme.ThemeDescriptor> = lightTheme

    fun setLightTheme(theme: Theme.ThemeDescriptor?) {
        lightTheme.value = theme ?: return
    }

    fun getDarkTheme(): LiveData<Theme.ThemeDescriptor> = darkTheme

    fun setDarkTheme(theme: Theme.ThemeDescriptor?) {
        darkTheme.value = theme ?: return
    }
}
