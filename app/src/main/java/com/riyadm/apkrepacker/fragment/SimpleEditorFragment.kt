package com.riyadm.apkrepacker.fragment

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentSimpleEditorBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.task.SimpleEditTask
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.FileProvider
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.QickEditParams
import com.riyadm.apkrepacker.utils.SignUtil
import com.riyadm.apkrepacker.utils.qickedit.AppInfo
import java.io.File

/**
 * Quick editor for an installed APK: app name, package, version, install location and SDK
 * levels, plus a new icon. Save rebuilds and signs the APK ([SimpleEditTask]).
 */
class SimpleEditorFragment : Fragment() {

    private var binding: FragmentSimpleEditorBinding? = null
    private lateinit var selected: File
    private var outputFile: File? = null
    private var oldPackage: String? = null
    private var installLocation = 0
    private var appInfo: AppInfo? = null
    private var newIcon: Bitmap? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = File(requireArguments().getString(ARG_SELECTED).orEmpty())
        // The build task reports back to this instance, so it has to outlive a configuration change.
        @Suppress("DEPRECATION")
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSimpleEditorBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        views.toolbar.setNavigationOnClickListener { FragmentUtils.remove(this) }
        views.saveExFab.setOnClickListener { buildApp() }
        // A save still running when the view was recreated.
        showSaving(views)
        try {
            val info = AppInfo(view.context, selected)
            if (!info.isValid()) throw IllegalStateException("AppInfo failed")
            appInfo = info
            val apk = checkNotNull(AppUtils.getApkInfo(view.context, selected.absolutePath))
            installLocation = apk[7] as Int
            newIcon = null

            with(views.form) {
                appIconEdit.setImageDrawable(previewIcon(apk[0] as Drawable?))
                appName.setText(info.label())
                appPackage.setText(info.pname())
                appVersionName.setText(info.version())
                appVersionCode.setText(apk[4].toString())
                appMinimumSdk.setText(apk[5].toString())
                appTargetSdk.setText(apk[6].toString())
                // No icon resource of its own (or none that resolves): nothing to replace.
                val hasIcon = info.iconValue() != null
                appIconChange.isVisible = hasIcon
                if (hasIcon) {
                    appIconEdit.setOnClickListener { selectIcon() }
                    appIconChange.setOnClickListener { selectIcon() }
                }
                appPackage.addTextChangedListener(packageWatcher)
            }
            bindInstallLocation(views)
        } catch (ex: Exception) {
            ex.printStackTrace()
            appInfo = null
            snack(getString(R.string.toast_error_cant_parse_apk))
        }
    }

    private fun bindInstallLocation(views: FragmentSimpleEditorBinding) {
        QickEditParams.setOldName(views.form.appName.text.toString())
        oldPackage = views.form.appPackage.text.toString()
        QickEditParams.setOldPackage(oldPackage)

        /* -1 default(none), 0 auto, 1 internal, 2 external */
        val locations = resources.getStringArray(R.array.install_location)
        views.form.etInstallLocation.setAdapter(
            ArrayAdapter(requireContext(), com.google.android.material.R.layout.m3_auto_complete_simple_item, locations)
        )
        if (installLocation >= -1 && installLocation < 3) {
            views.form.etInstallLocation.setText(locations[installLocation + 1], false)
        }
        views.form.etInstallLocation.setOnItemClickListener { _, _, position, _ ->
            QickEditParams.setInstallLocation(position)
        }
    }

    private val packageWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            // Renaming the package is not offered yet; the clone options stay hidden.
            if (oldPackage != null && s.toString() == oldPackage) binding?.form?.optionClone?.visibility = View.GONE
        }
    }

    private fun selectIcon() {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.select_icon))
            .setSelectMode(FilePickerDialog.MODE_SINGLE)
            .setSelectType(FilePickerDialog.TYPE_FILE)
            .setExtensions(arrayOf("gif", "png", "jpg", "jpeg", "bmp", "webp"))
            .setRootDir(Environment.getExternalStorageDirectory().absolutePath)
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        filePaths.firstOrNull()?.let(::createNewIcon)
                    }

                    override fun onCanceled() {}
                })
            .show()
    }

    private fun createNewIcon(file: String) {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeFile(file, options) ?: return
        newIcon = bitmap
        binding?.form?.appIconEdit?.setImageDrawable(BitmapDrawable(resources, bitmap))
    }

    /**
     * An adaptive icon draws itself through the system's mask shape, which never lines up with the
     * circular frame. Its two layers are flattened unmasked instead (each is 1.5x the visible area,
     * centred) so the frame alone does the clipping.
     */
    private fun previewIcon(icon: Drawable?): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || icon !is AdaptiveIconDrawable) return icon
        val size = binding?.form?.appIconEdit?.layoutParams?.width?.takeIf { it > 0 } ?: return icon
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = size / 4
        for (layer in listOf(icon.background, icon.foreground)) {
            layer ?: continue
            layer.setBounds(-inset, -inset, size + inset, size + inset)
            layer.draw(canvas)
        }
        return BitmapDrawable(resources, bitmap)
    }

    private fun buildApp() {
        val views = binding?.form ?: return
        val info = appInfo
        if (info == null) {
            // The APK couldn't be read when the editor opened: say so instead of doing nothing.
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error)
                .setMessage(R.string.toast_error_cant_parse_apk)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val minSdk = views.appMinimumSdk.text.toString().toIntOrNull()
        val targetSdk = views.appTargetSdk.text.toString().toIntOrNull()
        if (minSdk == null || targetSdk == null) {
            snack(getString(R.string.cannot_be_empty))
            return
        }
        val iconPath = info.iconValue()?.split(":")?.getOrNull(1).orEmpty()
        QickEditParams.setNewname(views.appName.text.toString())
        QickEditParams.setNewPackage(views.appPackage.text.toString())
        QickEditParams.setVersionCode(views.appVersionCode.text.toString())
        QickEditParams.setVersionName(views.appVersionName.text.toString())
        QickEditParams.setMinimumSdk(minSdk)
        QickEditParams.setTargetSdk(targetSdk)
        QickEditParams.setInRes(views.inResourcesCb.isChecked)
        QickEditParams.setInDex(views.inDexCb.isChecked)
        QickEditParams.setIconName(iconPath)
        QickEditParams.setIconFiles(info.iconFiles())
        QickEditParams.setBitmap(newIcon)
        val context = requireContext()
        SignUtil.loadKey(context) { signTool -> SimpleEditTask(context, this, signTool).execute(selected) }
    }

    // ---- build progress (called by SimpleEditTask) -------------------------------------------

    /** Save in progress: the bar under the toolbar runs and the Save button is disabled. */
    fun showProgress() {
        saving = true
        binding?.let { showSaving(it) }
    }

    private fun showSaving(views: FragmentSimpleEditorBinding) {
        views.saveProgress.isVisible = saving
        views.saveExFab.isEnabled = !saving
        views.saveExFab.setText(if (saving) R.string.simple_edit_saving else R.string.save)
    }

    /** Where the APK went, with Install (signed only) and Share; a Snackbar when it failed. */
    fun hideProgress(result: Boolean, signed: Boolean) {
        saving = false
        binding?.let { showSaving(it) }
        val context = context ?: return
        val apk = outputFile
        if (!result || apk == null) {
            snack(getString(R.string.simple_edit_failed))
            return
        }
        val message = if (signed) R.string.simple_edit_saved_to else R.string.simple_edit_saved_unsigned
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.simple_edit_saved_title)
            .setMessage(getString(message, apk.absolutePath))
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.m3d_share) { _, _ -> shareApk(apk) }
            .apply { if (signed) setPositiveButton(R.string.install_app) { _, _ -> AppUtils.installApk(context, apk) } }
            .show()
    }

    private fun shareApk(apk: File) {
        val context = context ?: return
        val uri = FileProvider.getUriForFile(context, apk)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(apk.name, uri)
        startActivity(Intent.createChooser(send, getString(R.string.m3d_share_apk_title)))
    }

    fun setOutputFile(outputFile: File?) {
        this.outputFile = outputFile
    }

    private fun snack(message: CharSequence) {
        val root = binding?.root ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).setAnchorView(binding?.saveExFab).show()
    }

    companion object {
        const val TAG = "SimpleEditorFragment"
        private const val ARG_SELECTED = "selected"

        @JvmStatic
        fun newInstance(selected: String?): SimpleEditorFragment =
            SimpleEditorFragment().apply { arguments = bundleOf(ARG_SELECTED to selected) }
    }
}
