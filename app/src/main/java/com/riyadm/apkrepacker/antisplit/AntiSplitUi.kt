package com.riyadm.apkrepacker.antisplit

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogAntisplitSignBinding
import com.riyadm.apkrepacker.databinding.DialogProgressM3Binding
import com.riyadm.apkrepacker.service.AntiSplitService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Dialogs shared by every screen that offers AntiSplit (installed apps, file manager). */
object AntiSplitUi {

    /** What to use for a split app: all splits merged into one APK, or only the base APK. */
    @JvmStatic
    fun askMergeOrBase(fragment: Fragment, label: String?, onAntiSplit: () -> Unit, onBaseOnly: () -> Unit) {
        val context = fragment.requireContext()
        val options = arrayOf(
            option(context, R.string.antisplit_choice_merge, R.string.antisplit_choice_merge_desc),
            option(context, R.string.antisplit_choice_base, R.string.antisplit_choice_base_desc),
        )
        val builder = MaterialAlertDialogBuilder(context)
        // The stock list row has no vertical padding, so two-line choices ran into each other.
        val rowPadding = (12 * context.resources.displayMetrics.density).toInt()
        val adapter = object : ArrayAdapter<CharSequence>(
            builder.context, com.google.android.material.R.layout.mtrl_alert_select_dialog_item, android.R.id.text1, options,
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    setPaddingRelative(paddingStart, rowPadding, paddingEnd, rowPadding)
                }
        }
        builder
            .setTitle(context.getString(R.string.antisplit_choice_title, label.orEmpty()))
            .setAdapter(adapter) { _, which -> if (which == 0) onAntiSplit() else onBaseOnly() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Whether "AntiSplit & save" should sign the merged APK or save it unsigned: two cards, the
     * last choice picked already, then on to the save picker.
     */
    @JvmStatic
    fun askSign(fragment: Fragment, onChosen: (sign: Boolean) -> Unit) {
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val body = DialogAntisplitSignBinding.inflate(LayoutInflater.from(context))
        var sign = prefs.getBoolean(PREF_LAST_SIGN, true)

        val selectedStroke = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        val stroke = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY)
        val density = context.resources.displayMetrics.density
        fun show() {
            for ((card, radio, selected) in listOf(
                Triple(body.signCard, body.signRadio, sign),
                Triple(body.unsignedCard, body.unsignedRadio, !sign),
            )) {
                card.strokeColor = if (selected) selectedStroke else stroke
                card.strokeWidth = ((if (selected) 2 else 1) * density).toInt()
                // Checkable (no check icon, see the layout): a light tint marks the choice, and
                // TalkBack reads each card as checked / not checked.
                card.isChecked = selected
                radio.isChecked = selected
            }
        }
        body.signCard.setOnClickListener { sign = true; show() }
        body.unsignedCard.setOnClickListener { sign = false; show() }
        show()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.antisplit_sign_title)
            .setView(body.root)
            .setPositiveButton(R.string.antisplit_sign_continue) { _, _ ->
                prefs.edit { putBoolean(PREF_LAST_SIGN, sign) }
                onChosen(sign)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private const val PREF_LAST_SIGN = "antisplit_last_sign"

    /** A list choice: its [title] in bold over a smaller, dimmer [description]. */
    private fun option(context: Context, @StringRes title: Int, @StringRes description: Int): CharSequence {
        val strong = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val dim = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        return buildSpannedString {
            // The dialog's body style is dim; the title is the choice itself, so give it full contrast.
            color(strong) { bold { append(context.getString(title)) } }
            append("\n")
            scale(0.875f) { color(dim) { append(context.getString(description)) } }
        }
    }

    /** File name the save picker suggests; an unsigned APK says so, so the two are not mixed up. */
    @JvmStatic
    fun apkFileName(base: String, sign: Boolean): String = base + (if (sign) "" else "_unsigned") + ".apk"

    /**
     * Gets the APK of [source] ready (merged, or the base of an archive with [baseOnly]) and hands
     * its path to [requestKey]'s fragment-result listener on [host]'s child fragment manager. A
     * current cached merge is delivered at once; otherwise a progress dialog follows the job,
     * which keeps running in the background if the dialog is closed.
     */
    @JvmStatic
    fun prepare(host: Fragment, source: AntiSplitService.Source, requestKey: String, label: String?, baseOnly: Boolean = false) {
        if (!baseOnly) {
            AntiSplitService.cachedApk(host.requireContext(), source)?.let { apk ->
                host.childFragmentManager.setFragmentResult(requestKey, result(true, apk.absolutePath, null, label))
                return
            }
        }
        val jobId = AntiSplitService.prepare(host.requireContext(), source, baseOnly)
        ProgressDialog.newInstance(jobId, requestKey, label).show(host.childFragmentManager, ProgressDialog.TAG)
    }

    const val RESULT_OK = "ok"
    const val RESULT_APK = "apk"
    const val RESULT_MESSAGE = "message"
    const val RESULT_LABEL = "label"

    private fun result(ok: Boolean, apk: String?, message: String?, label: String?) =
        bundleOf(RESULT_OK to ok, RESULT_APK to apk, RESULT_MESSAGE to message, RESULT_LABEL to label)

    /** Follows one prepare job: shows its current step, then reports the result and closes. */
    class ProgressDialog : DialogFragment() {

        private var binding: DialogProgressM3Binding? = null
        private var delivered = false

        private val jobId get() = requireArguments().getString(ARG_JOB).orEmpty()

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            isCancelable = false
            val body = DialogProgressM3Binding.inflate(LayoutInflater.from(requireContext()))
            binding = body
            body.progressDeterminate.visibility = View.GONE
            body.progressLoading.visibility = View.VISIBLE
            body.progressMessage.text = getString(R.string.antisplit_merging)
            return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.antisplit_title)
                .setView(body.root)
                // The job goes on; its notification says when it is done.
                .setNegativeButton(R.string.antisplit_run_in_background) { _, _ -> dismissAllowingStateLoss() }
                .create()
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        AntiSplitService.progress.collect { p -> if (p.jobId == jobId) binding?.progressMessage?.text = p.text }
                    }
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        AntiSplitService.results.collect { r -> if (r.jobId == jobId) deliver(r) }
                    }
                    // Subscribed first; now catch a job that finished while this dialog was away.
                    AntiSplitService.resultOf(jobId)?.let(::deliver)
                }
            }
        }

        private fun deliver(r: AntiSplitService.Result) {
            if (delivered) return
            delivered = true
            val args = requireArguments()
            val message = (listOf(r.message) + r.warnings).joinToString("\n")
            parentFragmentManager.setFragmentResult(
                args.getString(ARG_KEY).orEmpty(),
                result(r.success, r.apk?.absolutePath, message, args.getString(ARG_LABEL)),
            )
            dismissAllowingStateLoss()
        }

        override fun onDestroyView() {
            binding = null
            super.onDestroyView()
        }

        companion object {
            const val TAG = "AntiSplitProgress"
            private const val ARG_JOB = "job"
            private const val ARG_KEY = "key"
            private const val ARG_LABEL = "label"

            fun newInstance(jobId: String, requestKey: String, label: String?) = ProgressDialog().apply {
                arguments = bundleOf(ARG_JOB to jobId, ARG_KEY to requestKey, ARG_LABEL to label)
            }
        }
    }
}
