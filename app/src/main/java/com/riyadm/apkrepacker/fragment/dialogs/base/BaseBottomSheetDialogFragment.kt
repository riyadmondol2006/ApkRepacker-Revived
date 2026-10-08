package com.riyadm.apkrepacker.fragment.dialogs.base

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetDialogBaseBinding
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo

/**
 * Material 3 modal bottom sheet shared by the app's option / selection sheets: drag handle, title,
 * a content slot filled by [onCreateContentView] and a cancel / ok action row.
 * The tonal container, 28dp top corners and edge-to-edge handling come from the sheet theme.
 */
open class BaseBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var sheetBinding: BottomSheetDialogBaseBinding? = null
    private var sheetDialog: BottomSheetDialog? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        sheetDialog = dialog

        val inflater = LayoutInflater.from(dialog.context)
        val binding = BottomSheetDialogBaseBinding.inflate(inflater)
        sheetBinding = binding
        dialog.setContentView(binding.root)

        onCreateContentView(inflater, binding.containerBottomSheetDialogBaseContent, savedInstanceState)
            ?.let { content ->
                onContentViewCreated(content, savedInstanceState)
                binding.containerBottomSheetDialogBaseContent.addView(content)
            }

        springContentIn(binding.root)
        return dialog
    }

    /** The sheet itself is animated by the Material behavior; the content settles in with a spring. */
    private fun springContentIn(root: View) {
        root.alpha = 0f
        root.translationY = root.resources.getDimension(R.dimen.space_6)
        root.post {
            root.springTo(DynamicAnimation.ALPHA, 1f, MotionSpring.DefaultEffects)
            root.springTo(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial)
        }
    }

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    protected open fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return null
    }

    protected open fun onContentViewCreated(view: View, savedInstanceState: Bundle?) {
    }

    protected val positiveButton: Button
        get() = requireNotNull(sheetBinding).buttonBottomSheetDialogBaseOk

    protected val negativeButton: Button
        get() = requireNotNull(sheetBinding).buttonBottomSheetDialogBaseCancel

    protected val title: TextView
        get() = requireNotNull(sheetBinding).tvBottomSheetDialogBaseTitle

    fun setTitle(@StringRes title: Int) {
        setTitle(getString(title))
    }

    fun setTitle(title: CharSequence?) {
        this.title.apply {
            text = title
            visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** Hides the title when [hide] is true. */
    fun hideTitle(hide: Boolean) {
        title.visibility = if (hide) View.GONE else View.VISIBLE
    }

    /** Hides the whole cancel / ok row, for sheets whose content acts on tap. */
    protected fun hideActionRow(hide: Boolean) {
        requireNotNull(sheetBinding).containerBottomSheetDialogBaseButtons.visibility =
            if (hide) View.GONE else View.VISIBLE
    }

    protected fun revealBottomSheet() {
        sheetDialog?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        val owner: Any = parentFragment ?: requireActivity()
        val dialogTag = tag
        if (owner is OnDismissListener && dialogTag != null) {
            owner.onDialogDismissed(dialogTag)
        }
    }

    fun interface OnDismissListener {

        fun onDialogDismissed(dialogTag: String)
    }
}
