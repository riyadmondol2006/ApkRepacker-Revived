package com.riyadm.apkrepacker.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.BuildConfig
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.CardAboutChannelBinding
import com.riyadm.apkrepacker.databinding.CardAboutHeaderBinding
import com.riyadm.apkrepacker.databinding.FragmentAboutBinding
import com.riyadm.apkrepacker.databinding.ItemAboutLinkBinding
import com.riyadm.apkrepacker.fragment.dialogs.LicensesDialogFragment
import com.riyadm.apkrepacker.ui.motion.pressSpring
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.preferences.showBackIfOverlay
import com.riyadm.apkrepacker.update.UpdateUi
import com.riyadm.apkrepacker.utils.AppLogo
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.Constant

/** App identity, developer profile and contact links, credits and the open source licenses, under a large collapsing app bar. */
class AboutFragment : Fragment() {

    private var binding: FragmentAboutBinding? = null

    private class Link(
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val subtitle: Int? = null,
        val subtitleText: CharSequence? = null,
        val onClick: () -> Unit,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentAboutBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = checkNotNull(binding)
        ui.toolbar.showBackIfOverlay(this)
        ui.appBar.setLiftOnScrollTargetView(ui.scrollAbout)

        bindHeader(ui.aboutHeader)
        bindChannel(ui.aboutChannel)

        fill(
            ui.groupApp,
            listOf(
                Link(
                    R.drawable.ic_update,
                    R.string.about_update_check,
                    subtitleText = getString(R.string.about_sub_update_check, BuildConfig.VERSION_NAME),
                ) { UpdateUi.checkNow(this) },
            ),
        )

        ui.aboutDeveloper.btnDevTelegram.setOnClickListener { openSite(Constant.DEV_TELEGRAM) }
        ui.aboutDeveloper.btnDevEmail.setOnClickListener { sendEmail() }
        ui.aboutDeveloper.root.springIn(fromScale = 0.94f)

        fill(
            ui.groupLinks,
            listOf(
                Link(R.drawable.ic_telegram, R.string.about_telegram_dev, R.string.about_sub_telegram) { openSite(Constant.DEV_TELEGRAM) },
                Link(R.drawable.ic_phone_android, R.string.about_whatsapp, R.string.about_sub_whatsapp) { openSite(Constant.DEV_WHATSAPP) },
                Link(R.drawable.ic_email, R.string.about_email, R.string.about_sub_email, onClick = ::sendEmail),
                Link(R.drawable.ic_link, R.string.about_website, R.string.about_sub_website) { openSite(Constant.MY_WEBSITE) },
            ),
        )
        fill(
            ui.groupSocial,
            listOf(
                Link(R.drawable.ic_github, R.string.about_project_repo, R.string.about_sub_project_repo) { openSite(Constant.PROJECT_REPO) },
                Link(R.drawable.ic_m3_patch, R.string.about_patch_doc, R.string.about_sub_patch_doc) { openSite(Constant.PATCH_DOC) },
                Link(R.drawable.ic_github, R.string.about_git_hub, R.string.about_sub_github) { openSite(Constant.GIT_HUB) },
                Link(R.drawable.ic_link, R.string.about_linkedin, R.string.about_sub_linkedin) { openSite(Constant.DEV_LINKEDIN) },
                Link(R.drawable.ic_video, R.string.about_youtube, R.string.about_sub_youtube) { openSite(Constant.DEV_YOUTUBE) },
                Link(R.drawable.ic_link, R.string.about_x, R.string.about_sub_x) { openSite(Constant.DEV_X) },
                Link(R.drawable.ic_link, R.string.about_reversesio, R.string.about_sub_reversesio) { openSite(Constant.DEV_REVERSESIO) },
            ),
        )
        fill(
            ui.groupTeam,
            listOf(
                Link(R.drawable.ic_github, R.string.about_original_repo, R.string.about_sub_original_repo) { openSite(Constant.ORIGINAL_REPO) },
            ),
        )
        fill(
            ui.groupLegal,
            listOf(Link(R.drawable.ic_copyring, R.string.about_source_licenses) { LicensesDialogFragment.show(this) }),
        )
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun bindHeader(header: CardAboutHeaderBinding) {
        val context = requireContext()
        // The logo fills the whole rounded square (this header is shared with the project page, whose
        // icons keep the tinted backing, so it is adjusted here rather than in the layout).
        with(header.tvAboutAppIcon) {
            background = null
            scaleType = ImageView.ScaleType.FIT_XY
            setImageDrawable(
                AppLogo.drawable(context, resources.getDimensionPixelSize(R.dimen.about_icon_size))
                    ?: context.packageManager.getApplicationIcon(context.applicationInfo),
            )
        }
        header.tvAboutVersion.text = AppUtils.getVersionName(context)
        header.root.springIn(fromScale = 0.92f)
    }

    /** The app's Telegram channel card: the whole card and its Join button open the channel. */
    private fun bindChannel(channel: CardAboutChannelBinding) {
        channel.root.setOnClickListener { openSite(Constant.TELEGRAM_CHANNEL) }
        channel.btnChannelJoin.setOnClickListener { openSite(Constant.TELEGRAM_CHANNEL) }
        channel.root.pressSpring()
        channel.root.springIn(delayMs = 60L, fromScale = 0.94f)
    }

    /** Fills [group] with one tile per link; outer tiles get large corners, inner ones small (segmented list). */
    private fun fill(group: ViewGroup, links: List<Link>) {
        val inflater = LayoutInflater.from(group.context)
        links.forEachIndexed { index, link ->
            val row = ItemAboutLinkBinding.inflate(inflater, group, false)
            row.aboutRowIcon.setImageResource(link.icon)
            row.aboutRowTitle.setText(link.title)
            (link.subtitleText ?: link.subtitle?.let(::getString))?.let {
                row.aboutRowSubtitle.text = it
                row.aboutRowSubtitle.visibility = View.VISIBLE
            }
            row.aboutRow.shapeAppearanceModel = rowShape(first = index == 0, last = index == links.lastIndex)
            row.aboutRow.setOnClickListener { link.onClick() }
            row.aboutRow.pressSpring()
            group.addView(row.root)
        }
    }

    private fun rowShape(first: Boolean, last: Boolean): ShapeAppearanceModel {
        val large = resources.getDimension(R.dimen.shape_corner_extra_large)
        val small = resources.getDimension(R.dimen.shape_corner_extra_small)
        return ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, if (first) large else small)
            .setTopRightCorner(CornerFamily.ROUNDED, if (first) large else small)
            .setBottomLeftCorner(CornerFamily.ROUNDED, if (last) large else small)
            .setBottomRightCorner(CornerFamily.ROUNDED, if (last) large else small)
            .build()
    }

    private fun sendEmail() {
        val subject = Uri.encode(getString(R.string.about_email_intent))
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("${Constant.EMAIL}?subject=$subject"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            binding?.let { Snackbar.make(it.root, R.string.about_not_found_email, Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun openSite(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            binding?.let { Snackbar.make(it.root, url, Snackbar.LENGTH_SHORT).show() }
        }
    }

    companion object {
        const val TAG = "AboutFragment"
    }
}
