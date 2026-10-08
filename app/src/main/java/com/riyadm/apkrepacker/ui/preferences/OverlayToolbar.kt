package com.riyadm.apkrepacker.ui.preferences

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.riyadm.apkrepacker.R

/**
 * Settings and About are bottom-navigation tabs, but "Other" also opens them full screen over the
 * activity content. In that case the toolbar gets a back arrow; as a tab it stays plain.
 */
fun MaterialToolbar.showBackIfOverlay(fragment: Fragment) {
    val hostsFullScreen = ((fragment.view?.parent as? View)?.id == android.R.id.content)
    if (!hostsFullScreen) return
    setNavigationIcon(R.drawable.ic_back)
    setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description)
    setNavigationOnClickListener { fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
}
