package com.riyadm.apkrepacker.fragment.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.databinding.DialogProgressM3Binding

/**
 * Non-dismissable wait dialog (Material 3). Arguments: [TITLE], [MESSAGE], [MAX], [CANCELABLE].
 * Without a [MAX] it shows a LoadingIndicator; with one, [updateProgress] drives a wavy progress ring.
 */
class ProgressDialogFragment : DialogFragment() {

    fun interface ProgressDialogFragmentListener {
        fun onProgressCancelled()
    }

    private var binding: DialogProgressM3Binding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        isCancelable = args.getBoolean(CANCELABLE, false)

        val body = DialogProgressM3Binding.inflate(LayoutInflater.from(requireContext()))
        binding = body
        body.progressMessage.text = args.getString(MESSAGE)

        val max = args.getInt(MAX, 0)
        if (max > 0) {
            body.progressLoading.isVisible = false
            body.progressDeterminate.apply {
                isVisible = true
                isIndeterminate = false
                this.max = max
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(args.getString(TITLE))
            .setView(body.root)
            .create()
    }

    fun updateProgress(value: Int) {
        binding?.progressDeterminate?.setProgressCompat(value, true)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        getProgressDialogFragmentListener()?.onProgressCancelled()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    fun getProgressDialogFragmentListener(): ProgressDialogFragmentListener? {
        return (parentFragment as? ProgressDialogFragmentListener)
            ?: (activity as? ProgressDialogFragmentListener)
    }

    companion object {
        const val TITLE = "title"
        const val MESSAGE = "message"
        const val MAX = "max"
        const val CANCELABLE = "cancelable"
        const val TAG = "ProgressDialogFragment"

        @JvmStatic
        fun newInstance(): ProgressDialogFragment {
            return ProgressDialogFragment()
        }
    }
}
