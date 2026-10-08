package com.riyadm.apkrepacker.ui.filemanager

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.riyadm.apkrepacker.R

/** Material 3 dialogs used by the file screens (replaces the old MaterialDialog based helpers). */
object FileDialogs {

    /**
     * Asks for a single line of text (a file or folder name). [validate] may return an error
     * message, which keeps the dialog open and shows it under the field.
     */
    fun promptText(
        context: Context,
        @StringRes title: Int,
        initial: CharSequence? = null,
        @StringRes hint: Int = R.string.files_input_name_hint,
        @StringRes confirmLabel: Int = R.string.ok,
        validate: ((String) -> String?)? = null,
        onConfirm: (String) -> Unit,
    ): AlertDialog {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_files_input, null)
        val layout: TextInputLayout = content.findViewById(R.id.files_input_layout)
        val input: TextInputEditText = content.findViewById(R.id.files_input)
        layout.hint = context.getString(hint)
        input.setText(initial)
        input.doAfterTextChanged { layout.error = null }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(confirmLabel, null)
            .create()

        fun submit() {
            val value = input.text?.toString()?.trim().orEmpty()
            val error = when {
                value.isEmpty() -> context.getString(R.string.files_error_name_empty)
                '/' in value -> context.getString(R.string.files_error_name_invalid)
                else -> validate?.invoke(value)
            }
            if (error != null) {
                layout.error = error
                return
            }
            dialog.dismiss()
            onConfirm(value)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            input.setOnEditorActionListener { _, _, _ -> submit(); true }
            // Select the name without its extension so typing replaces just the base name.
            val text = input.text?.toString().orEmpty()
            val dot = text.lastIndexOf('.')
            input.requestFocus()
            if (dot > 0) input.setSelection(0, dot) else input.selectAll()
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        return dialog
    }

    /** Destructive-or-not confirmation. */
    fun confirm(
        context: Context,
        @StringRes message: Int,
        @StringRes confirmLabel: Int = R.string.ok,
        onConfirm: () -> Unit,
    ): AlertDialog = MaterialAlertDialogBuilder(context)
        .setMessage(message)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
        .show()

    /** Single choice list; picking an entry confirms immediately. */
    fun chooseOne(
        context: Context,
        @StringRes title: Int,
        items: Array<CharSequence>,
        checked: Int,
        onChosen: (Int) -> Unit,
    ): AlertDialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setSingleChoiceItems(items, checked) { dialog, which ->
            dialog.dismiss()
            onChosen(which)
        }
        .setNegativeButton(R.string.cancel, null)
        .show()

    /** Read-only information (title + monospace-free body). */
    fun info(context: Context, title: CharSequence, message: CharSequence): AlertDialog =
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
}
