package com.riyadm.apkrepacker.ui.keydialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.KeystoreBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper

/**
 * Edits the custom signing key (type, key file, alias / certificate, passwords).
 * Closing the dialog any way other than OK forgets the passwords, as before.
 */
class KeystorePreferenceFragmentDialog : DialogFragment() {

    private var binding: KeystoreBinding? = null
    private var confirmed = false
    private val prefs by lazy { PreferenceHelper.getInstance(requireContext()) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ui = KeystoreBinding.inflate(layoutInflater)
        binding = ui
        bindFields(ui)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.signature_file)
            .setView(ui.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                confirmed = true
                save(ui)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!confirmed) {
            prefs.storeKey = ""
            prefs.privateKey = ""
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun bindFields(ui: KeystoreBinding) {
        val types = resources.getStringArray(R.array.key_format)
        val type = (prefs.keyType ?: 0).coerceIn(types.indices)

        ui.etType.setAdapter(ArrayAdapter(requireContext(), R.layout.m3_auto_complete_simple_item, types))
        ui.etType.setText(types[type], false)
        ui.etType.setOnItemClickListener { _, _, position, _ ->
            prefs.keyType = position
            applyType(ui, position)
        }
        applyType(ui, type)

        ui.keyPath.setText(prefs.privateKeyPath)
        ui.alias.setText(prefs.certPath)
        ui.keyPasswords.storePass.setText(prefs.storeKey)
        ui.keyPasswords.keyPass.setText(prefs.privateKey)

        ui.buttonSelectKey.setOnClickListener { pickFile { ui.keyPath.setText(it) } }
        ui.buttonSelectCert.setOnClickListener { pickFile { ui.alias.setText(it) } }
    }

    /** pk8 keys carry no passwords; their second field is the certificate file instead of an alias. */
    private fun applyType(ui: KeystoreBinding, type: Int) {
        val pk8 = type == KeyGenerator.TYPE_PK8
        ui.buttonSelectCert.visibility = if (pk8) View.VISIBLE else View.GONE
        ui.keyPasswords.root.visibility = if (pk8) View.GONE else View.VISIBLE
        ui.cert.hint = getString(if (pk8) R.string.key_field_cert_path else R.string.key_field_alias)
    }

    private fun pickFile(onPicked: (String) -> Unit) {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.select_key))
            .setSelectMode(FilePickerDialog.MODE_SINGLE)
            .setSelectType(FilePickerDialog.TYPE_FILE)
            .setRootDir(Environment.getExternalStorageDirectory().absolutePath)
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        filePaths.lastOrNull()?.let(onPicked)
                    }

                    override fun onCanceled() = Unit
                },
            )
            .show()
    }

    private fun save(ui: KeystoreBinding) {
        prefs.privateKeyPath = ui.keyPath.text.toString()
        prefs.certPath = ui.alias.text.toString()
        prefs.storeKey = ui.keyPasswords.storePass.text.toString()
        prefs.privateKey = ui.keyPasswords.keyPass.text.toString()
    }

    companion object {
        private const val ARG_KEY = "key"

        @JvmStatic
        fun newInstance(key: String?): KeystorePreferenceFragmentDialog =
            KeystorePreferenceFragmentDialog().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
