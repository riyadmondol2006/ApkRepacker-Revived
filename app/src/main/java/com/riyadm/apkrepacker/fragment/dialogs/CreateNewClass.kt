package com.riyadm.apkrepacker.fragment.dialogs

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jecelyin.common.utils.IOUtils
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogCreateClassBinding
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.ProjectUtils
import java.io.File

/**
 * Dialog that creates an empty smali class: asks for the package and class name, writes the
 * `.smali` file into [mCurrFolder] and reports it to [listener].
 */
class CreateNewClass(
    private val context: Context?,
    private val mCurrFolder: File?,
    private val listener: OnFileCreatedListener?
) {

    fun show(): MaterialAlertDialogBuilder? {
        val context = context ?: return null
        val binding = DialogCreateClassBinding.inflate(LayoutInflater.from(context))
        binding.classPackage.setText(initialPackage())

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_create_class)
            .setView(binding.root)
            .setPositiveButton(R.string.m3f_create, null)
            .setNegativeButton(R.string.cancel, null)
        val dialog = builder.create()

        fun confirm() {
            val name = binding.className.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.classNameLayout.error = context.getString(R.string.cannot_be_empty)
                return
            }
            createNewClass(name, binding.classPackage.text?.toString().orEmpty().trim())
            dialog.dismiss()
        }
        binding.className.setOnEditorActionListener { _, _, _ ->
            confirm()
            true
        }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { confirm() }
            binding.className.requestFocus()
        }
        dialog.show()
        return builder
    }

    private fun initialPackage(): String? =
        mCurrFolder?.let { FileUtil.findPackage(File(ProjectUtils.getProjectPath()), it) }

    private fun createNewClass(fileName: String, packageName: String) {
        val smaliFile = File(mCurrFolder, "$fileName.smali")
        try {
            smaliFile.parentFile?.mkdirs()
            val path = packageName.replace(".", File.separator)
            val content = ".class public L$path${File.separator}$fileName;\n.super Ljava/lang/Object;\n"
            IOUtils.writeFile(smaliFile, content)
            listener?.onCreateSuccess(smaliFile)
        } catch (e: Exception) {
            listener?.onCreateFailed(smaliFile, e)
        }
    }

    interface OnFileCreatedListener {
        /** The file did not exist before. */
        fun onCreateSuccess(deleted: File)

        fun onCreateFailed(deleted: File, e: Exception)
    }
}
