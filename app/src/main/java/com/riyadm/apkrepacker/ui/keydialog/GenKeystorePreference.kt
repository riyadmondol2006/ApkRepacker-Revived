package com.riyadm.apkrepacker.ui.keydialog

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import com.riyadm.apkrepacker.R

/**
 * Preference that opens [GenKeystoreDialogFragment] (the hosting preference fragment shows it
 * from onDisplayPreferenceDialog). Declared in res/xml/peference.xml when key creation is offered.
 */
class GenKeystorePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {
    init {
        dialogLayoutResource = R.layout.dialog_generate_key
    }
}
