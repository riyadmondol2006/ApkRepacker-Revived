package com.riyadm.apkrepacker.fragment

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentSettingsBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.DarkLightThemeSelectionDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ThemeSelectionDialogFragment
import com.riyadm.apkrepacker.ui.keydialog.GenKeystoreDialogFragment
import com.riyadm.apkrepacker.ui.keydialog.GenKeystorePreference
import com.riyadm.apkrepacker.ui.keydialog.KeystorePreference
import com.riyadm.apkrepacker.ui.keydialog.KeystorePreferenceFragmentDialog
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys
import com.riyadm.apkrepacker.ui.preferences.RevivedPreferenceFragment
import com.riyadm.apkrepacker.ui.preferences.showBackIfOverlay
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.PermissionsUtils
import com.riyadm.apkrepacker.utils.Theme

/**
 * App settings under a large collapsing app bar. The decompile/build defaults (Decompile and
 * Build categories) are bound directly to the ApktoolOptionsStore keys in res/xml/peference.xml.
 */
class SettingsFragment : RevivedPreferenceFragment(),
    ThemeSelectionDialogFragment.OnThemeDialogDismissListener,
    DarkLightThemeSelectionDialogFragment.OnDarkLightThemesChosenListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var binding: FragmentSettingsBinding? = null
    private val helper by lazy { PreferenceHelper.getInstance(requireContext()) }
    private val themeManager by lazy { Theme.getInstance(requireContext()) }

    private val themePref get() = findPreference<Preference>(PreferenceKeys.KEY_THEME)
    private val autoThemePickerPref get() = findPreference<Preference>(PreferenceKeys.KEY_AUTO_THEME_PICKER)
    private val decodeFolderPref get() = findPreference<Preference>(PreferenceKeys.KEY_DECODING_FOLDER)
    private val allFilesAccessPref get() = findPreference<Preference>(KEY_ALL_FILES_ACCESS)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The auto theme state lives in Theme, not in the KEY_AUTO_THEME preference: mirror it before the switch binds.
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putBoolean(PreferenceKeys.KEY_AUTO_THEME, Theme.getInstance(requireContext()).themeMode == Theme.Mode.AUTO_LIGHT_DARK)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.peference, rootKey)

        val autoMode = themeManager.themeMode == Theme.Mode.AUTO_LIGHT_DARK

        themePref?.apply {
            isVisible = !autoMode
            setOnPreferenceClickListener {
                ThemeSelectionDialogFragment.newInstance(ThemeSelectionDialogFragment.MODE_APPLY)
                    .show(childFragmentManager, TAG_THEME)
                true
            }
        }

        allFilesAccessPref?.setOnPreferenceClickListener {
            // Explains, then opens the system "All files access" page; the summary refreshes in onStart.
            PermissionsUtils.requestStorageAccess(requireContext())
            true
        }

        autoThemePickerPref?.apply {
            isVisible = autoMode
            setOnPreferenceClickListener {
                DarkLightThemeSelectionDialogFragment.newInstance().show(childFragmentManager, TAG_AUTO_THEME)
                true
            }
        }

        findPreference<TwoStatePreference>(PreferenceKeys.KEY_AUTO_THEME)?.setOnPreferenceChangeListener { _, newValue ->
            val auto = newValue as Boolean
            if (auto && !AppUtils.apiIsAtLeast(Build.VERSION_CODES.Q)) {
                view?.let { Snackbar.make(it, R.string.settings_main_auto_theme_pre_q_warning, Snackbar.LENGTH_LONG).show() }
            }
            themeManager.setMode(if (auto) Theme.Mode.AUTO_LIGHT_DARK else Theme.Mode.CONCRETE)
            themePref?.isVisible = !auto
            autoThemePickerPref?.isVisible = auto
            updateThemeSummaries()
            true
        }

        findPreference<KeystorePreference>(PreferenceKeys.KEY_KEYSTORE_FILE)?.isVisible = helper.isCustomSign
        findPreference<TwoStatePreference>(PreferenceKeys.KEY_USE_CUSTOM_SIGN)?.setOnPreferenceChangeListener { _, newValue ->
            findPreference<KeystorePreference>(PreferenceKeys.KEY_KEYSTORE_FILE)?.isVisible = newValue as Boolean
            true
        }

        updateThemeSummaries()
        updateDecodeFolderSummary()
        updateAllFilesAccessSummary()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val preferenceList = super.onCreateView(inflater, container, savedInstanceState)
        val ui = FragmentSettingsBinding.inflate(inflater, container, false)
        ui.listHost.addView(preferenceList)
        binding = ui
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = checkNotNull(binding)
        ui.appBar.setLiftOnScrollTargetView(listView)

        ui.toolbar.showBackIfOverlay(this)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateThemeSummaries()
        updateDecodeFolderSummary()
    }

    override fun onStop() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    private fun updateAllFilesAccessSummary() {
        val granted = PermissionsUtils.hasStorageAccess(requireContext())
        allFilesAccessPref?.summary = getString(
            if (granted) R.string.pref_all_files_access_granted else R.string.pref_all_files_access_denied
        )
    }

    private fun updateThemeSummaries() {
        val context = context ?: return
        themePref?.summary = themeManager.concreteTheme.getName(context)
        autoThemePickerPref?.summary = getString(
            R.string.settings_main_auto_theme_picker_summary,
            themeManager.lightTheme.getName(context),
            themeManager.darkTheme.getName(context),
        )
    }

    private fun updateDecodeFolderSummary() {
        val stored = preferenceManager.sharedPreferences?.getString(PreferenceKeys.KEY_DECODING_FOLDER, null)
        decodeFolderPref?.summary = stored ?: helper.decodingPath
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferenceKeys.KEY_DECODING_FOLDER) updateDecodeFolderSummary()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is KeystorePreference ->
                KeystorePreferenceFragmentDialog.newInstance(preference.key).show(childFragmentManager, TAG_KEYSTORE)
            is GenKeystorePreference -> GenKeystoreDialogFragment.show(childFragmentManager)
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onThemeDialogDismissed(tag: String) {
        updateThemeSummaries()
    }

    override fun onThemesChosen(tag: String?, lightTheme: Theme.ThemeDescriptor?, darkTheme: Theme.ThemeDescriptor?) {
        lightTheme?.let { themeManager.lightTheme = it }
        darkTheme?.let { themeManager.darkTheme = it }
    }

    companion object {
        const val TAG = "SettingsFragment"
        private const val TAG_THEME = "theme"
        private const val TAG_AUTO_THEME = "auto_theme"
        private const val TAG_KEYSTORE = "keystore"
        private const val KEY_ALL_FILES_ACCESS = "pref_all_files_access"
    }
}
