package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import com.jecelyin.editor.v2.EditorPreferences
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.preferences.RevivedPreferenceFragment

/**
 * Code editor settings (res/xml/preference_editor.xml). Each preference shows its current value in
 * its summary and keeps it up to date when changed.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class SettingsFragmentEditor : RevivedPreferenceFragment() {

    private val editorPrefs by lazy { EditorPreferences.getInstance(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_editor, rootKey)
        bindAll(preferenceScreen)
        configureNumberFields()
    }

    private fun bindAll(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            if (preference is PreferenceGroup) {
                bindAll(preference)
                continue
            }
            // Plain Preference rows (no value) have nothing to bind.
            if (preference.javaClass == Preference::class.java) continue

            val value = editorPrefs.getValue(preference.key)
            when (preference) {
                is EditTextPreference -> preference.text = value.toString()
                is TwoStatePreference -> preference.isChecked = value.toString().toBoolean()
            }
            if (preference.key != EditorPreferences.KEY_SYMBOL) {
                preference.onPreferenceChangeListener = summaryUpdater
                summaryUpdater.onPreferenceChange(preference, value)
            }
        }
    }

    /** Tab size and the highlight size limit only make sense as numbers. */
    private fun configureNumberFields() {
        val tabSizeKey = getString(R.string.pref_tab_size)
        val highlightLimitKey = getString(R.string.pref_highlight_file_size_limit)

        findPreference<EditTextPreference>(tabSizeKey)?.setOnBindEditTextListener { edit ->
            edit.inputType = InputType.TYPE_CLASS_NUMBER
            edit.filters = arrayOf(InputFilter.LengthFilter(1))
            edit.setSingleLine()
        }
        findPreference<EditTextPreference>(highlightLimitKey)?.setOnBindEditTextListener { edit ->
            edit.inputType = InputType.TYPE_CLASS_NUMBER
            edit.setSingleLine()
        }
    }

    /** Mirrors a preference's value into its summary. */
    private val summaryUpdater = Preference.OnPreferenceChangeListener { preference, value ->
        if (value != null) {
            val text = value.toString()
            when {
                preference is ListPreference -> {
                    val index = preference.findIndexOfValue(text)
                    preference.summary = if (index >= 0) preference.entries[index] else null
                }
                preference is TwoStatePreference -> Unit
                preference.key == getString(R.string.pref_highlight_file_size_limit) -> preference.summary = "$text KB"
                else -> preference.summary = text
            }
        }
        true
    }
}
