package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetAddNewLanguageBinding
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.resourceeditor.findListener
import com.riyadm.apkrepacker.utils.translation.Languages
import java.util.Locale

/**
 * Pick a language (dropdown or typed code) to add it, auto-translate into it, or translate a single string.
 * Arguments: [TRANSLATE_MODE] translate instead of just adding, [SINGLE_TRANSLATE_MODE] only one string.
 */
class AddLanguageDialogFragment : BottomSheetDialogFragment() {

    private var binding: BottomSheetAddNewLanguageBinding? = null

    private val langNames: Array<String> = Languages.languages
    private val langCodes: Array<String> = Languages.codes

    private val translateMode: Boolean
        get() = arguments?.getBoolean(TRANSLATE_MODE, false) ?: false
    private val singleMode: Boolean
        get() = arguments?.getBoolean(SINGLE_TRANSLATE_MODE, false) ?: false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetAddNewLanguageBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        val helper = PreferenceHelper.getInstance(requireContext())

        views.titleTextView.setText(if (translateMode) R.string.action_auto_translate_lang else R.string.action_add_new_lang)
        val showSwitches = translateMode && !singleMode
        views.swSkipTranslated.isVisible = showSwitches
        views.swSkipSupportLines.isVisible = showSwitches
        views.swSkipTranslated.isChecked = helper.isSkipTranslated
        views.swSkipSupportLines.isChecked = helper.isSkipSupportLines

        views.etLang.setAdapter(
            ArrayAdapter(requireContext(), com.google.android.material.R.layout.m3_auto_complete_simple_item, langNames),
        )
        views.etLang.setOnItemClickListener { parent, _, position, _ ->
            val index = langNames.indexOf(parent.getItemAtPosition(position) as? String)
            if (index >= 0) setCode(index)
        }
        if (savedInstanceState == null) {
            val device = langCodes.indexOfFirst { it.startsWith("-" + Locale.getDefault().language) }
            if (device >= 0) setCode(device)
        }
        views.languageCode.doAfterTextChanged { views.languageCodeLayout.error = null }

        views.btnAddLangOk.setOnClickListener {
            val typed = views.languageCode.text?.toString()?.trim().orEmpty()
            if (typed.isEmpty()) {
                views.languageCodeLayout.error = getString(R.string.cannot_be_empty)
                return@setOnClickListener
            }
            // Folders and translator codes are written with a leading dash ("-ru", "-zh-rCN").
            val code = if (typed.startsWith("-")) typed else "-$typed"
            val skipTranslated = views.swSkipTranslated.isChecked
            val skipSupport = views.swSkipSupportLines.isChecked
            helper.isSkipTranslated = skipTranslated
            helper.isSkipSupportLines = skipSupport
            findListener<ItemClickListener>()?.let { listener ->
                if (singleMode) {
                    listener.onTranslateSting(code)
                } else {
                    listener.onAddLangClick(code, translateMode, skipTranslated, skipSupport)
                }
            }
            dismiss()
        }
    }

    fun setCode(index: Int) {
        val views = binding ?: return
        views.etLang.setText(langNames[index], false)
        views.languageCode.setText(langCodes[index])
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    interface ItemClickListener {
        fun onAddLangClick(code: String, autotranslate: Boolean, skipTranslated: Boolean, skipSupport: Boolean)

        fun onTranslateSting(code: String)
    }

    companion object {
        const val TAG = "AddLanguageDialogFragment"
        private const val TRANSLATE_MODE = "is_translate_mode"
        private const val SINGLE_TRANSLATE_MODE = "is_single_translate_mode"

        @JvmStatic
        fun newInstance(translate: Boolean, singleLine: Boolean) = AddLanguageDialogFragment().apply {
            arguments = Bundle().apply {
                putBoolean(TRANSLATE_MODE, translate)
                putBoolean(SINGLE_TRANSLATE_MODE, singleLine)
            }
        }
    }
}
