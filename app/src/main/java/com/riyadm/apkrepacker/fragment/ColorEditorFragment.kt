package com.riyadm.apkrepacker.fragment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.riyadm.apkrepacker.databinding.FragmentColorEditorBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ColorOptionsDialogFragment
import com.riyadm.apkrepacker.ui.colorslist.ColorMeta
import com.riyadm.apkrepacker.ui.colorslist.ColorsAdapter
import com.riyadm.apkrepacker.ui.colorslist.ColorsViewModel
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.resourceeditor.ResourceSearch
import com.riyadm.apkrepacker.ui.resourceeditor.shrinkFabOnScroll
import com.riyadm.apkrepacker.ui.resourceeditor.showSnack
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

/** Colors of one `colors.xml` as a staggered grid of swatch cards. */
class ColorEditorFragment : Fragment(R.layout.fragment_color_editor),
    ColorsAdapter.OnItemInteractionListener,
    ColorOptionsDialogFragment.ItemClickListener {

    private val viewModel: ColorsViewModel by viewModels()

    private var binding: FragmentColorEditorBinding? = null
    private var mainAdapter: ColorsAdapter? = null
    private var searchAdapter: ColorsAdapter? = null
    private var search: ResourceSearch? = null

    private val colorsFile: File
        get() = File(requireArguments().getString(ARG_FILE).orEmpty())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setColorsFile(colorsFile)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = FragmentColorEditorBinding.bind(view)
        binding = views

        val main = ColorsAdapter(viewModel::resolveColor).apply { setInteractionListener(this@ColorEditorFragment) }
        val results = ColorsAdapter(viewModel::resolveColor).apply { setInteractionListener(this@ColorEditorFragment) }
        mainAdapter = main
        searchAdapter = results
        setupGrid(views.colors, main)
        setupGrid(views.searchResults, results)
        FastScrollerBuilder(views.colors).useMd2Style().build()
        views.colors.shrinkFabOnScroll(views.fabAddColor)

        search = ResourceSearch(views.searchBar, views.searchView, viewModel::filter)

        views.fabGoEditor.setOnClickListener {
            startActivity(Intent(activity, CodeEditorActivity::class.java).putExtra("filePath", colorsFile.absolutePath))
        }
        views.fabAddColor.setOnClickListener {
            ColorOptionsDialogFragment.newInstance(null, Color.BLACK, false).show(childFragmentManager, ColorOptionsDialogFragment.TAG)
        }

        var listEmpty = true
        viewModel.colors.observe(viewLifecycleOwner) { colors ->
            main.setData(colors)
            results.setData(colors)
            listEmpty = colors.isEmpty()
            updateEmptyState(listEmpty)
        }
        viewModel.loading.observe(viewLifecycleOwner) { updateEmptyState(listEmpty) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { views.root.showSnack(getString(it), views.fabCluster) }
            }
        }
    }

    private fun setupGrid(list: RecyclerView, adapter: ColorsAdapter) {
        val widthDp = resources.configuration.screenWidthDp
        val cardDp = resources.getDimension(R.dimen.color_card_min_width) / resources.displayMetrics.density
        val spans = (widthDp / cardDp).toInt().coerceAtLeast(2)
        list.layoutManager = StaggeredGridLayoutManager(spans, StaggeredGridLayoutManager.VERTICAL)
        list.adapter = adapter
        list.applyExpressiveMotion()
    }

    private fun updateEmptyState(empty: Boolean) {
        val views = binding ?: return
        val loading = viewModel.loading.value == true
        views.loading.isVisible = loading && empty
        views.emptyText.isVisible = !loading && empty
        views.emptyText.setText(if (search?.query.isNullOrEmpty()) R.string.h_empty_colors else R.string.h_no_results)
    }

    override fun onColorClicked(color: ColorMeta, id: Int) {
        mainAdapter?.setSelectedPosition(id)
        searchAdapter?.setSelectedPosition(id)
        val resolved = viewModel.resolveColor(color.value) ?: Color.BLACK
        ColorOptionsDialogFragment.newInstance(color.label, resolved, true, id, color.value)
            .show(childFragmentManager, ColorOptionsDialogFragment.TAG)
    }

    override fun onColorNameCopied(name: String) {
        binding?.root?.showSnack(getString(R.string.color_name_copied, name), binding?.fabCluster)
    }

    override fun onColorSave(position: Int, name: String, value: String, isChange: Boolean) {
        if (isChange) viewModel.setNewColor(position, name, value) else viewModel.addNewColor(name, value)
        if (!isChange) binding?.colors?.postDelayed({ binding?.colors?.smoothScrollToPosition((mainAdapter?.itemCount ?: 1) - 1) }, SCROLL_DELAY_MS)
    }

    override fun onColorDelete(position: Int) = viewModel.deleteColor(position)

    override fun onColorSheetDismissed() {
        mainAdapter?.setSelectedPosition(null)
        searchAdapter?.setSelectedPosition(null)
    }

    override fun onDestroyView() {
        search = null
        mainAdapter = null
        searchAdapter = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ColorEditorFragment"
        private const val ARG_FILE = "colorsFile"
        private const val SCROLL_DELAY_MS = 200L

        @JvmStatic
        fun newInstance(colors: String?) = ColorEditorFragment().apply {
            arguments = Bundle().apply { putString(ARG_FILE, colors) }
        }
    }
}
