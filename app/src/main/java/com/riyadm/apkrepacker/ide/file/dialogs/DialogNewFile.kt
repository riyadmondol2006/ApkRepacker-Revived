/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.ide.file.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogNewFileDefaultBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import java.io.File

/**
 * Created by Duy on 30-Apr-18.
 *
 * Material dialog that creates an empty file: name, folder and a default extension.
 */
class DialogNewFile : DialogFragment() {

    private var mListener: OnCreateFileListener? = null
    private var binding: DialogNewFileDefaultBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val views = DialogNewFileDefaultBinding.inflate(layoutInflater)
        binding = views
        val extensions = requireArguments().getStringArray(KEY_FILE_EXTENSIONS).orEmpty()

        views.editPath.setText(requireArguments().getString(KEY_CURRENT_DIR))
        views.spinnerExts.setAdapter(
            ArrayAdapter(requireContext(), com.google.android.material.R.layout.m3_auto_complete_simple_item, extensions)
        )
        views.spinnerExts.setText(extensions.firstOrNull().orEmpty(), false)
        views.btnSelectPath.setOnClickListener { selectPath(views) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_create_new_file)
            .setView(views.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (createNewFile(views)) dismiss()
            }
        }
        return dialog
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun selectPath(views: DialogNewFileDefaultBinding) {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.m3f_choose_folder))
            .setSelectMode(FilePickerDialog.MODE_SINGLE)
            .setSelectType(FilePickerDialog.TYPE_DIR)
            .setRootDir(views.editPath.text?.toString().orEmpty().ifEmpty { "/storage/emulated/0" })
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        filePaths.firstOrNull()?.let { views.editPath.setText(it) }
                    }

                    override fun onCanceled() {}
                })
            .show()
    }

    private fun createNewFile(views: DialogNewFileDefaultBinding): Boolean {
        var name = views.editInput.text?.toString().orEmpty()
        if (name.isEmpty() || !name.matches(Regex("[A-Za-z0-9_./ ]+"))) {
            views.editInputLayout.error = getString(R.string.invalid_name)
            return false
        }
        views.editInputLayout.error = null
        val path = views.editPath.text?.toString().orEmpty()
        if (!name.contains(".")) {
            name += views.spinnerExts.text?.toString().orEmpty()
        }
        val file = File(path, name)
        return try {
            file.parentFile?.mkdirs()
            file.createNewFile()
            mListener?.onFileCreated(file)
            true
        } catch (e: Exception) {
            views.editInputLayout.error = e.message
            false
        }
    }

    fun interface OnCreateFileListener {
        fun onFileCreated(file: File)
    }

    companion object {
        private const val KEY_FILE_EXTENSIONS = "fileExtensions"
        private const val KEY_CURRENT_DIR = "currentDir"

        @JvmStatic
        fun newInstance(
            fileExtensions: Array<String>,
            dir: String,
            onCreateFileListener: OnCreateFileListener?
        ): DialogNewFile = DialogNewFile().apply {
            arguments = bundleOf(KEY_FILE_EXTENSIONS to fileExtensions, KEY_CURRENT_DIR to dir)
            mListener = onCreateFileListener
        }
    }
}
