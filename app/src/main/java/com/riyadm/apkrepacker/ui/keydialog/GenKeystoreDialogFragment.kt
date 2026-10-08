package com.riyadm.apkrepacker.ui.keydialog

import android.app.Dialog
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogGenerateKeyBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creates a self-signed signing key. "Create" only writes the files; "Create and use" also makes it
 * the app's custom signing key.
 */
class GenKeystoreDialogFragment : DialogFragment() {

    private var binding: DialogGenerateKeyBinding? = null
    private var selectedType = KeyGenerator.TYPE_JKS
    private var syncingAlias = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ui = DialogGenerateKeyBinding.inflate(layoutInflater)
        binding = ui
        selectedType = savedInstanceState?.getInt(STATE_TYPE) ?: KeyGenerator.TYPE_JKS
        setUpFields(ui)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.gen_key_title)
            .setView(ui.root)
            // Real handlers are attached in onStart so a failed attempt doesn't dismiss the dialog.
            .setPositiveButton(R.string.create_and_use, null)
            .setNeutralButton(R.string.key_action_create, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? AlertDialog ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { generate(useKey = true) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { generate(useKey = false) }
        updateButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TYPE, selectedType)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun setUpFields(ui: DialogGenerateKeyBinding) {
        val types = resources.getStringArray(R.array.key_format)
        ui.format.setAdapter(ArrayAdapter(requireContext(), R.layout.m3_auto_complete_simple_item, types))
        ui.format.setText(types[selectedType], false)
        ui.format.setOnItemClickListener { _, _, position, _ -> onTypeSelected(ui, position) }
        applyType(ui)

        ui.buttonSelectKey.setOnClickListener { pickFile { ui.path.setText(it) } }
        ui.buttonSelectCert.setOnClickListener { pickFile { ui.alias.setText(it) } }

        listOf(ui.path, ui.alias, ui.storePass, ui.date).forEach { field ->
            field.doAfterTextChanged {
                updateButtons()
                if (selectedType == KeyGenerator.TYPE_PK8 && field === ui.path) syncCertPath(ui)
            }
        }
    }

    private fun onTypeSelected(ui: DialogGenerateKeyBinding, type: Int) {
        selectedType = type
        if (type == KeyGenerator.TYPE_PK8) {
            ui.keyPass.setText("")
            ui.storePass.setText("")
            syncCertPath(ui)
        } else if (ui.alias.text.toString().startsWith("/")) {
            ui.alias.setText("")
        }
        applyType(ui)
        updateButtons()
    }

    /** pk8 keys have no passwords or alias: the second field becomes the certificate file path. */
    private fun applyType(ui: DialogGenerateKeyBinding) {
        val pk8 = selectedType == KeyGenerator.TYPE_PK8
        ui.password.visibility = if (pk8) View.GONE else View.VISIBLE
        ui.buttonSelectCert.visibility = if (pk8) View.VISIBLE else View.GONE
        ui.cert.hint = getString(if (pk8) R.string.key_field_cert_path else R.string.key_field_alias)
    }

    /** Derives the certificate file name from the key file name (testkey.pk8 -> testkey.x509.pem). */
    private fun syncCertPath(ui: DialogGenerateKeyBinding) {
        if (syncingAlias) return
        syncingAlias = true
        val path = ui.path.text.toString()
        ui.alias.setText(
            when {
                path.isEmpty() -> ""
                path.endsWith(".pk8") -> path.dropLast(3) + "x509.pem"
                else -> "$path.x509.pem"
            },
        )
        syncingAlias = false
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

    private fun updateButtons() {
        val ui = binding ?: return
        val dialog = dialog as? AlertDialog ?: return
        val complete = !ui.path.isBlank() && !ui.alias.isBlank() && !ui.date.isBlank() &&
            (selectedType == KeyGenerator.TYPE_PK8 || !ui.storePass.isBlank())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = complete
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = complete
    }

    private fun EditText.isBlank() = text.isNullOrEmpty()

    private fun generate(useKey: Boolean) {
        val ui = binding ?: return
        val params = readParams(ui) ?: return
        setBusy(ui, true)

        lifecycleScope.launch {
            val failure = withContext(Dispatchers.Default) { runCatching { KeyGenerator.generate(params) }.exceptionOrNull() }
            val activity = activity ?: return@launch
            if (failure == null) {
                if (useKey) useAsSigningKey(params)
                dismissAllowingStateLoss()
                Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    if (useKey) R.string.key_saved_in_use else R.string.key_created,
                    Snackbar.LENGTH_LONG,
                ).show()
            } else {
                setBusy(ui, false)
                showError(failure)
            }
        }
    }

    private fun readParams(ui: DialogGenerateKeyBinding): KeyParams? {
        val years = ui.date.text.toString().toLongOrNull()
        if (years == null) {
            ui.date.error = getString(R.string.error)
            return null
        }
        return KeyParams(
            type = selectedType,
            keyPath = ui.path.text.toString(),
            certOrAlias = ui.alias.text.toString(),
            storePass = ui.storePass.text.toString(),
            keyPass = ui.keyPass.text.toString(),
            keySize = ui.keySize.text.toString().toIntOrNull() ?: DEFAULT_KEY_SIZE,
            years = years,
            commonName = ui.name.text.toString(),
            organizationUnit = ui.organizationUnit.text.toString(),
            organizationName = ui.organizationName.text.toString(),
            localityName = ui.localityName.text.toString(),
            stateName = ui.stateName.text.toString(),
            country = ui.country.text.toString(),
        )
    }

    private fun useAsSigningKey(params: KeyParams) {
        val context = requireContext()
        val helper = PreferenceHelper.getInstance(context)
        helper.keyType = params.type
        helper.privateKeyPath = params.keyPath
        helper.certPath = params.certOrAlias
        helper.storeKey = params.storePass
        helper.privateKey = params.keyPass
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PreferenceKeys.KEY_USE_CUSTOM_SIGN, true)
        }
    }

    private fun setBusy(ui: DialogGenerateKeyBinding, busy: Boolean) {
        ui.generating.visibility = if (busy) View.VISIBLE else View.GONE
        isCancelable = !busy
        val dialog = dialog as? AlertDialog ?: return
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE).forEach {
            dialog.getButton(it).isEnabled = !busy
        }
        if (!busy) updateButtons()
    }

    private fun showError(error: Throwable) {
        val details = buildString {
            append(error.message ?: error.toString())
            error.stackTrace.take(MAX_TRACE_LINES).forEach { append('\n').append(it) }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.key_create_failed)
            .setMessage(details)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    companion object {
        private const val TAG = "GenKeystoreDialog"
        private const val STATE_TYPE = "type"
        private const val DEFAULT_KEY_SIZE = 2048
        private const val MAX_TRACE_LINES = 8

        @JvmStatic
        fun show(fragmentManager: FragmentManager) {
            GenKeystoreDialogFragment().show(fragmentManager, TAG)
        }
    }
}
