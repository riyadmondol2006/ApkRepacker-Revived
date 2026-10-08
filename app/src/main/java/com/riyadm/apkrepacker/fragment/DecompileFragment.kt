package com.riyadm.apkrepacker.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.AppEditorActivity
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.DecodeOptionsArgs
import com.riyadm.apkrepacker.apktool.ui.copyToClipboard
import com.riyadm.apkrepacker.apktool.ui.springCardColor
import com.riyadm.apkrepacker.databinding.FragmentDecompileBinding
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.service.DecompileService
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.viewmodel.DecompileViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import com.google.android.material.R as MaterialR

/**
 * Decompile screen: a status card (loading indicator -> tonal result), the live apktool log in a
 * monospace card and the actions. The run itself lives in [DecompileViewModel].
 */
class DecompileFragment : Fragment() {

    private val viewModel: DecompileViewModel by viewModels()
    private var binding: FragmentDecompileBinding? = null
    private var logAdapter: LogLineAdapter? = null

    private lateinit var selectedApk: File
    private var projectName: String? = null
    private lateinit var options: DecodeOptions

    /** Identifies this run; kept in the arguments so a recreated screen re-attaches to the same decompile. */
    private lateinit var runId: String

    /** The whole log so far, one line per entry. */
    val text: CharSequence
        get() = viewModel.logText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        selectedApk = File(args.getString(ARG_SELECTED).orEmpty())
        projectName = args.getString(ARG_NAME)
        options = resolveOptions(args)
        runId = args.getString(ARG_RUN_ID) ?: UUID.randomUUID().toString().also { args.putString(ARG_RUN_ID, it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentDecompileBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.progressTip.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        b.decompileSubtitle.text = projectName?.takeIf { it.isNotBlank() } ?: selectedApk.name

        // Restored after process death: the run is gone with the process (the session map is
        // empty and the ViewModel is new). Don't silently start a second decompile; an explicit
        // new one always arrives with savedInstanceState == null.
        if (savedInstanceState != null && !viewModel.isStarted && DecompileService.session(runId) == null) {
            FragmentUtils.remove(this)
            return
        }

        // Start (or re-attach to) the run first: the log adapter reads the session's lines.
        viewModel.start(selectedApk, options, runId)
        val adapter = LogLineAdapter(viewModel.lines)
        logAdapter = adapter
        b.log.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            applyExpressiveMotion()
        }
        b.btnClose.setOnClickListener { FragmentUtils.remove(this) }
        b.btnCopy.setOnClickListener { it.copyToClipboard(text) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.lineCount.collect { count -> adapter.onLinesAdded(count, b.log) }
                }
                launch {
                    var first = true
                    viewModel.state.collect { state ->
                        render(b, state, animate = !first)
                        first = false
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Closed for good (not just rotated): drop the finished run's log from memory.
        if (isRemoving || requireActivity().isFinishing) DecompileService.forget(runId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.log?.adapter = null
        binding = null
        logAdapter = null
    }

    /**
     * Options passed to [newInstance] win; otherwise the saved defaults are used, with the mode
     * taken from the legacy "decMode" action int when one was given.
     */
    private fun resolveOptions(args: Bundle): DecodeOptions {
        DecodeOptionsArgs.fromBundle(args.getBundle(ARG_OPTIONS))?.let { return it }
        val stored = ApktoolOptionsStore.loadDecodeOptions(requireContext())
        if (args.containsKey(ARG_LEGACY_MODE)) {
            val action = args.getInt(ARG_LEGACY_MODE, -1)
            if (action >= 0) return stored.copy(mode = DecodeOptionsArgs.modeFromLegacyAction(action))
        }
        return stored
    }

    private fun render(b: FragmentDecompileBinding, state: DecompileViewModel.State, animate: Boolean) {
        when (state) {
            DecompileViewModel.State.Running -> Unit
            is DecompileViewModel.State.Done -> showResult(
                b, success = true, animate = animate,
                title = R.string.decompile_finished,
                subtitle = getString(R.string.m3d_decompile_saved_to, state.project.path),
            )
            DecompileViewModel.State.Failed -> showResult(
                b, success = false, animate = animate,
                title = R.string.error_decompilation_failed,
                subtitle = getString(R.string.m3d_decompile_failed_hint),
            )
        }
        if (state is DecompileViewModel.State.Done) {
            b.btnOpenProject.setOnClickListener { openProject(state.project) }
        }
    }

    private fun showResult(
        b: FragmentDecompileBinding,
        success: Boolean,
        animate: Boolean,
        @androidx.annotation.StringRes title: Int,
        subtitle: String,
    ) {
        val container = themeColor(b.root, if (success) MaterialR.attr.colorPrimaryContainer else MaterialR.attr.colorErrorContainer)
        val onContainer = themeColor(b.root, if (success) MaterialR.attr.colorOnPrimaryContainer else MaterialR.attr.colorOnErrorContainer)

        if (animate) b.decompileStatusCard.springCardColor(container) else b.decompileStatusCard.setCardBackgroundColor(container)
        b.progressTip.setText(title)
        b.progressTip.setTextColor(onContainer)
        b.decompileSubtitle.text = subtitle
        b.decompileSubtitle.setTextColor(onContainer)

        b.decompileResultIcon.setImageResource(if (success) R.drawable.ic_m3_check_circle else R.drawable.ic_m3_error_circle)
        b.decompileResultIcon.imageTintList = ColorStateList.valueOf(onContainer)
        b.decompileResultIcon.contentDescription = getString(if (success) R.string.m3d_status_done else R.string.m3d_status_failed)
        b.decompileResultIcon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        val action = if (success) b.btnOpenProject else b.btnCopy
        if (animate) {
            b.decompileLoading.springOut { b.decompileLoading.visibility = View.GONE }
            b.decompileProgress.springOut { b.decompileProgress.visibility = View.GONE }
            b.decompileResultIcon.springIn(delayMs = 60)
            action.springIn(delayMs = 120)
        } else {
            b.decompileLoading.visibility = View.GONE
            b.decompileProgress.visibility = View.GONE
            b.decompileResultIcon.visibility = View.VISIBLE
            action.visibility = View.VISIBLE
        }
    }

    private fun openProject(result: File) {
        val dataFile = File(result.absolutePath, "apktool.json")
        val apkFileName = ProjectUtils.readJson(dataFile, "apkFileName")
        val intent = Intent(requireContext(), AppEditorActivity::class.java)
            .putExtra("projectPatch", result.absolutePath)
            .putExtra("apkFileIcon", ProjectUtils.readJson(dataFile, "apkFileIcon"))
            .putExtra("apkFileName", apkFileName ?: result.name)
            .putExtra("apkFilePackageName", ProjectUtils.readJson(dataFile, "apkFilePackageName"))
        startActivity(intent)
    }

    @ColorInt
    private fun themeColor(view: View, @AttrRes attr: Int): Int = MaterialColors.getColor(view, attr)

    /** Monospace log lines, tinted by apktool's level letter. */
    private class LogLineAdapter(private val lines: List<String>) : RecyclerView.Adapter<LogLineAdapter.Holder>() {

        private var shown = 0
        private var colors: IntArray? = null

        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun getItemCount(): Int = shown

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            if (colors == null) {
                colors = intArrayOf(
                    MaterialColors.getColor(parent, MaterialR.attr.colorOnSurfaceVariant),
                    MaterialColors.getColor(parent, MaterialR.attr.colorOnSurface),
                    MaterialColors.getColor(parent, R.attr.colorError),
                    MaterialColors.getColor(parent, MaterialR.attr.colorTertiary),
                    MaterialColors.getColor(parent, R.attr.colorPrimary),
                    MaterialColors.getColor(parent, MaterialR.attr.colorSecondary),
                )
            }
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_apktool_log_line, parent, false)
            return Holder(view as TextView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val line = lines[position]
            holder.text.text = line
            colors?.let { holder.text.setTextColor(it[colorIndex(line)]) }
            holder.text.setOnClickListener { it.copyToClipboard(line) }
        }

        private fun colorIndex(line: String): Int = when (line.firstOrNull()) {
            'V' -> 1
            'E', 'S' -> 2
            'W' -> 3
            'D' -> 4
            'A' -> 5
            else -> 0
        }

        /** The ViewModel now holds [count] lines; show the new ones and follow the tail. */
        fun onLinesAdded(count: Int, list: RecyclerView) {
            if (count <= shown) return
            val follow = shown == 0 || !list.canScrollVertically(1)
            val previous = shown
            shown = count
            if (previous == 0) notifyDataSetChanged() else notifyItemRangeInserted(previous, count - previous)
            if (follow) list.scrollToPosition(count - 1)
        }
    }

    companion object {

        const val TAG = "DecopmileFragment"
        private const val ARG_OPTIONS = "decodeOptions"
        private const val ARG_LEGACY_MODE = "decMode"
        private const val ARG_NAME = "name"
        private const val ARG_SELECTED = "selected"
        private const val ARG_APK_MODE = "mode"
        private const val ARG_RUN_ID = "runId"

        private fun create(build: Bundle.() -> Unit): DecompileFragment =
            DecompileFragment().apply { arguments = Bundle().apply(build) }

        @JvmStatic
        fun newInstance(name: String?, selected: String?, f: Boolean): DecompileFragment = create {
            putString(ARG_NAME, name)
            putString(ARG_SELECTED, selected)
            putBoolean(ARG_APK_MODE, f)
        }

        /**
         * @param mode legacy DecodeTask action: 3 = resources + smali, 2 = resources only,
         * 1 = smali only, 0 = nothing. The other options come from the saved defaults.
         */
        @JvmStatic
        fun newInstance(name: String?, selected: String?, f: Boolean, mode: Int): DecompileFragment = create {
            putString(ARG_NAME, name)
            putString(ARG_SELECTED, selected)
            putBoolean(ARG_APK_MODE, f)
            putInt(ARG_LEGACY_MODE, mode)
        }

        @JvmStatic
        fun newInstance(name: String?, selected: String?, f: Boolean, options: DecodeOptions): DecompileFragment = create {
            putString(ARG_NAME, name)
            putString(ARG_SELECTED, selected)
            putBoolean(ARG_APK_MODE, f)
            putBundle(ARG_OPTIONS, DecodeOptionsArgs.toBundle(options))
        }

        @JvmStatic
        fun newInstance(selected: String?, options: DecodeOptions): DecompileFragment = create {
            putString(ARG_SELECTED, selected)
            putBundle(ARG_OPTIONS, DecodeOptionsArgs.toBundle(options))
        }

        /** @param mode legacy DecodeTask action, see [newInstance]. */
        @JvmStatic
        fun newInstance(selected: String?, mode: Int): DecompileFragment = create {
            putString(ARG_SELECTED, selected)
            putInt(ARG_LEGACY_MODE, mode)
        }

        @JvmStatic
        fun newInstance(selected: String?): DecompileFragment = create {
            putString(ARG_SELECTED, selected)
        }
    }
}
