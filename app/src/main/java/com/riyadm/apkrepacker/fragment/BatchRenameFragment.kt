package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentBatchRenameBinding
import com.riyadm.apkrepacker.ui.filemanager.batch.BatchRenameProcessor
import com.riyadm.apkrepacker.ui.filemanager.batch.VariableConfig
import com.riyadm.apkrepacker.ui.filemanager.batch.VariableMatcher

/** Batch rename form: a filename pattern with insertable variables, find & replace and numbering. */
class BatchRenameFragment : Fragment() {

    private var binding: FragmentBatchRenameBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBatchRenameBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.buttonPreview.setOnClickListener { showPreview() }
        b.buttonRename.setOnClickListener { rename() }
        addVariableChips()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun addVariableChips() {
        val b = binding ?: return
        val config = VariableConfig.builder()
            .setContext(requireContext())
            .withNumberingStartAt(numberingStart())
            .build()
        for (variable in VariableMatcher(config).getAll()) {
            val chip = layoutInflater.inflate(R.layout.item_variable_chip_e, b.patternHelp, false) as Chip
            chip.text = variable.describe()
            val token = variable.pattern()
            chip.setOnClickListener { insertIntoPattern(token) }
            b.patternHelp.addView(chip)
        }
    }

    private fun insertIntoPattern(token: String) {
        val field = binding?.pattern ?: return
        val text = field.text ?: return
        val at = field.selectionStart.let { if (it < 0) text.length else it }
        text.insert(at, token)
    }

    private fun numberingStart(): Int = binding?.numberingStart?.text?.toString()?.toIntOrNull() ?: 0

    fun createProcessor(): BatchRenameProcessor? {
        val b = binding ?: return null
        val pattern = b.pattern.text?.toString().orEmpty()
        val replaceText = b.replaceText.text?.toString().orEmpty()
        if (pattern.isEmpty() && replaceText.isEmpty()) return null

        val replaceWith = b.replaceWith.text?.toString().orEmpty()
        val processor = BatchRenameProcessor(
            pattern,
            VariableConfig.builder().withNumberingStartAt(numberingStart()).build()
        )
        if (replaceText.isNotEmpty()) {
            processor.replaceText(replaceText, replaceWith, b.cbRegex.isChecked)
        }
        return processor
    }

    private fun showPreview() {
    }

    private fun rename() {
    }
}
