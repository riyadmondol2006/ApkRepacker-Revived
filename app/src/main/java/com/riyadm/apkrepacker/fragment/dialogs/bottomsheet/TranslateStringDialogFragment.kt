package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import com.riyadm.apkrepacker.autotranslator.translator.Translator
import com.riyadm.apkrepacker.databinding.BottomSheetTranslateStringBinding
import com.riyadm.apkrepacker.databinding.ItemChipActionBinding
import com.riyadm.apkrepacker.ui.publicxml.PublicXmlParser
import com.riyadm.apkrepacker.ui.resourceeditor.findListener
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Bottom sheet for one string: edit the translation, paste/copy/clear, auto-translate or delete it. */
class TranslateStringDialogFragment : BottomSheetDialogFragment(), AddLanguageDialogFragment.ItemClickListener {

    private var binding: BottomSheetTranslateStringBinding? = null

    private val key: String get() = arguments?.getString(ARG_KEY).orEmpty()
    private val originValue: String get() = arguments?.getString(ARG_ORIGIN).orEmpty()
    private val position: Int get() = arguments?.getInt(ARG_POSITION, -1) ?: -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetTranslateStringBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        views.tvStringKey.text = key
        views.oldValue.setText(originValue)
        if (savedInstanceState == null) views.newValue.setText(arguments?.getString(ARG_TRANSLATED))
        buildActionChips(views)
        views.btnAddLangOk.setOnClickListener {
            findListener<ItemClickListener>()?.onTranslateClicked(views.newValue.text?.toString().orEmpty(), position)
            dismiss()
        }
    }

    /** The action menu doubles as the chip row; its items keep their ids, titles and icons. */
    private fun buildActionChips(views: BottomSheetTranslateStringBinding) {
        val menu = PopupMenu(requireContext(), views.root).menu
        requireActivity().menuInflater.inflate(R.menu.string_item_menu, menu)
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            val chip = ItemChipActionBinding.inflate(layoutInflater, views.popupMenu, false).root
            chip.text = item.title
            chip.chipIcon = item.icon
            if (item.itemId == R.id.action_delete) {
                val error = MaterialColors.getColor(chip, R.attr.colorError)
                chip.setTextColor(error)
                chip.chipIconTint = android.content.res.ColorStateList.valueOf(error)
            }
            chip.setOnClickListener { onMenuItemClick(item) }
            views.popupMenu.addView(chip)
        }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        val views = binding ?: return false
        when (item.itemId) {
            R.id.action_paste ->
                StringUtils.getClipboard(requireContext())?.let { views.newValue.setText(it) }
            R.id.action_copy_original_value -> {
                StringUtils.setClipboard(requireContext(), originValue, false)
                snack(getString(R.string.toast_copy_to_clipboard))
            }
            R.id.action_copy_id -> copyResourceId()
            R.id.action_clear -> views.newValue.setText("")
            R.id.action_auto_translate ->
                AddLanguageDialogFragment.newInstance(true, true).show(childFragmentManager, AddLanguageDialogFragment.TAG)
            R.id.action_delete -> confirmDelete()
            else -> return false
        }
        return true
    }

    private fun copyResourceId() {
        viewLifecycleOwner.lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { PublicXmlParser(File(ProjectUtils.getProjectPath() + "/res/values/public.xml")).getIdByName(key) }.getOrNull()
            }
            StringUtils.setClipboard(requireContext(), id, false)
            snack(getString(R.string.string_id_copied, id))
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete_strings)
            .setMessage(key)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                findListener<ItemClickListener>()?.onDeleteString(position)
                dismiss()
            }
            .show()
    }

    override fun onAddLangClick(code: String, autotranslate: Boolean, skipTranslated: Boolean, skipSupport: Boolean) = Unit

    override fun onTranslateSting(code: String) {
        val views = binding ?: return
        val source = originValue
        views.translateProgress.isInvisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val translated = withContext(Dispatchers.IO) {
                runCatching { Translator(StringUtils.getGoogleLangCode(code)).translate(source) }.getOrNull()
            }
            binding?.let {
                it.translateProgress.isInvisible = true
                if (translated.isNullOrEmpty()) snack(getString(R.string.h_translate_failed)) else it.newValue.setText(translated)
            }
        }
    }

    private fun snack(message: String) {
        binding?.root?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    interface ItemClickListener {
        /** The translation typed in the sheet for the row at [key]. */
        fun onTranslateClicked(value: String, key: Int)

        fun onDeleteString(key: Int)
    }

    companion object {
        const val TAG = "TranslateStringDialogFragment"
        private const val ARG_KEY = "string_key"
        private const val ARG_ORIGIN = "string_origin"
        private const val ARG_TRANSLATED = "string_translated"
        private const val ARG_POSITION = "string_position"

        @JvmStatic
        fun newInstance(item: TranslateItem, position: Int) = TranslateStringDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_KEY, item.name)
                putString(ARG_ORIGIN, item.originValue)
                putString(ARG_TRANSLATED, item.translatedValue)
                putInt(ARG_POSITION, position)
            }
        }
    }
}
