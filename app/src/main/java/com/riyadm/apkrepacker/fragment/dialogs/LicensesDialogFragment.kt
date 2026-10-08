package com.riyadm.apkrepacker.fragment.dialogs

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogLicensesBinding
import com.riyadm.apkrepacker.databinding.ItemLicenseBinding
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.NoticesXmlParser
import de.psdev.licensesdialog.model.Notice
import de.psdev.licensesdialog.model.Notices

/** Open source notices of the libraries the app bundles, one expandable card each. */
class LicensesDialogFragment : DialogFragment() {

    private lateinit var notices: Notices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notices = savedInstanceState?.let { BundleCompat.getParcelable(it, STATE_NOTICES, Notices::class.java) }
            ?: requireContext().resources.openRawResource(R.raw.licenses).use(NoticesXmlParser::parse)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_NOTICES, notices)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ui = DialogLicensesBinding.inflate(layoutInflater)
        val all = notices.notices + LicensesDialog.LICENSES_DIALOG_NOTICE
        ui.recyclerLicenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = LicenseAdapter(all)
            applyExpressiveMotion()
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about_source_licenses)
            .setView(ui.root)
            .setPositiveButton(R.string.close, null)
            .create()
    }

    private class LicenseAdapter(private val items: List<Notice>) : RecyclerView.Adapter<LicenseAdapter.ViewHolder>() {

        private val expanded = HashSet<Int>()

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemLicenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notice = items[position]
            val context = holder.itemView.context
            with(holder.binding) {
                licenseName.text = notice.name
                licenseUrl.text = notice.url
                licenseCopyright.text = notice.copyright
                licenseChip.text = notice.license?.name
                licenseChip.visibility = if (notice.license == null) View.GONE else View.VISIBLE
                licenseUrl.setOnClickListener { openLink(context, notice.url) }

                licenseSummary.text = notice.license?.let { summaryOf(it, context) }
                licenseSummary.visibility = if (position in expanded) View.VISIBLE else View.GONE
                root.setOnClickListener {
                    val adapterPosition = holder.bindingAdapterPosition
                    if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    if (!expanded.remove(adapterPosition)) expanded.add(adapterPosition)
                    notifyItemChanged(adapterPosition)
                }
            }
        }

        private fun summaryOf(license: de.psdev.licensesdialog.licenses.License, context: Context): String? =
            runCatching { license.getSummaryText(context) }.getOrNull()

        private fun openLink(context: Context, url: String?) {
            if (url.isNullOrEmpty()) return
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No browser installed: nothing sensible to do.
            }
        }

        class ViewHolder(val binding: ItemLicenseBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        private val KEY_PREFIX = LicensesDialogFragment::class.java.name + '.'
        private val STATE_NOTICES = KEY_PREFIX + "NOTICES"

        @JvmStatic
        fun newInstance(): LicensesDialogFragment = LicensesDialogFragment()

        @JvmStatic
        fun show(activity: AppCompatActivity) {
            newInstance().show(activity.supportFragmentManager, null)
        }

        @JvmStatic
        fun show(fragment: Fragment) {
            newInstance().show(fragment.childFragmentManager, null)
        }
    }
}
