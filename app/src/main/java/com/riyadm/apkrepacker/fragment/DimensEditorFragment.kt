package com.riyadm.apkrepacker.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.riyadm.apkrepacker.databinding.DialogSingleInputBinding
import com.riyadm.apkrepacker.databinding.FragmentDimenEditorBinding
import com.riyadm.apkrepacker.ui.dimenslist.DimensAdapter
import com.riyadm.apkrepacker.ui.dimenslist.DimensMeta
import com.riyadm.apkrepacker.ui.dimenslist.DimensViewModel
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.resourceeditor.ResourceSearch
import com.riyadm.apkrepacker.ui.resourceeditor.shrinkFabOnScroll
import com.riyadm.apkrepacker.ui.resourceeditor.showSnack
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

/** Dimensions of one `dimens.xml`: search, edit a value, or jump to the code editor. */
class DimensEditorFragment : Fragment(R.layout.fragment_dimen_editor), DimensAdapter.OnItemInteractionListener {

    private val viewModel: DimensViewModel by viewModels()

    private var binding: FragmentDimenEditorBinding? = null
    private var mainAdapter: DimensAdapter? = null
    private var search: ResourceSearch? = null

    private val dimensFile: File
        get() = File(requireArguments().getString(ARG_FILE).orEmpty())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setDimensFile(dimensFile)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = FragmentDimenEditorBinding.bind(view)
        binding = views

        val main = DimensAdapter().apply { setInteractionListener(this@DimensEditorFragment) }
        val results = DimensAdapter().apply { setInteractionListener(this@DimensEditorFragment) }
        mainAdapter = main
        setupList(views.dimens, main)
        setupList(views.searchResults, results)
        FastScrollerBuilder(views.dimens).useMd2Style().build()
        views.dimens.shrinkFabOnScroll(views.fabGoEditor)

        search = ResourceSearch(views.searchBar, views.searchView, viewModel::filter)

        views.fabGoEditor.setOnClickListener {
            startActivity(Intent(activity, CodeEditorActivity::class.java).putExtra("filePath", dimensFile.absolutePath))
        }

        var listEmpty = true
        viewModel.dimens.observe(viewLifecycleOwner) { dimens ->
            main.setData(dimens)
            results.setData(dimens)
            listEmpty = dimens.isEmpty()
            updateEmptyState(listEmpty)
        }
        viewModel.loading.observe(viewLifecycleOwner) { updateEmptyState(listEmpty) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { views.root.showSnack(getString(it), views.fabGoEditor) }
            }
        }
    }

    private fun setupList(list: RecyclerView, adapter: DimensAdapter) {
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        list.applyExpressiveMotion()
    }

    private fun updateEmptyState(empty: Boolean) {
        val views = binding ?: return
        val loading = viewModel.loading.value == true
        views.loading.isVisible = loading && empty
        views.emptyText.isVisible = !loading && empty
        views.emptyText.setText(if (search?.query.isNullOrEmpty()) R.string.h_empty_dimens else R.string.h_no_results)
    }

    override fun onDimensClicked(dimens: DimensMeta, id: Int) {
        val name = dimens.label.orEmpty()
        val input = DialogSingleInputBinding.inflate(layoutInflater)
        input.inputLayout.hint = getString(R.string.h_dimen_value)
        input.inputLayout.helperText = getString(R.string.h_dimen_value_hint)
        input.inputEdit.setText(dimens.value)
        input.inputEdit.setSelection(input.inputEdit.length())
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.h_dimen_edit_title)
            .setMessage(name)
            .setView(input.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.inputEdit.text?.toString()?.trim().orEmpty()
            if (VALID_DIMEN.matches(value)) {
                viewModel.setNewDimens(id, value, name)
                dialog.dismiss()
            } else {
                input.inputLayout.error = getString(R.string.h_dimen_invalid)
            }
        }
    }

    override fun onDestroyView() {
        search = null
        mainAdapter = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "DimensEditorFragment"
        private const val ARG_FILE = "dimensFile"
        private val VALID_DIMEN = Regex("""(-?\d+(\.\d+)?(dp|dip|sp|px|pt|mm|in))|(@(android:)?dimen/[A-Za-z0-9_.]+)""")

        @JvmStatic
        fun newInstance(dimens: String?) = DimensEditorFragment().apply {
            arguments = Bundle().apply { putString(ARG_FILE, dimens) }
        }
    }
}
