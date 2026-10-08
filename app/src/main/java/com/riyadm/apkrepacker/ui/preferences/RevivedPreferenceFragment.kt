package com.riyadm.apkrepacker.ui.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion

/**
 * Base of the settings screens. Renders preferences the Material 3 Expressive way: every category
 * becomes a rounded section whose rows are stacked tiles (large outer corners, small inner ones).
 *
 * The look comes from `PreferenceThemeOverlay.Revived` (res/layout/preference_m3*.xml); this class
 * makes sure it is in effect and picks each row's background from its place inside the section.
 */
abstract class RevivedPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // PreferenceFragmentCompat reads `preferenceTheme` from the activity theme in onCreate.
        requireActivity().theme.applyStyle(R.style.ThemeOverlay_Revived_Preferences, true)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ): RecyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState).apply {
        clipToPadding = false
        setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimensionPixelSize(R.dimen.space_8))
        applyExpressiveMotion()
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> =
        SectionAdapter(preferenceScreen)

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)
    }

    private class SectionAdapter(screen: PreferenceGroup) : PreferenceGroupAdapter(screen) {

        override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val item = getItem(position)
            if (item == null || item is PreferenceGroup) return

            val startsSection = position == 0 || getItem(position - 1) is PreferenceGroup
            val endsSection = position == itemCount - 1 || getItem(position + 1) is PreferenceGroup
            val background = when {
                startsSection && endsSection -> R.drawable.bg_pref_m3_single
                startsSection -> R.drawable.bg_pref_m3_top
                endsSection -> R.drawable.bg_pref_m3_bottom
                else -> R.drawable.bg_pref_m3_middle
            }
            holder.itemView.background = ContextCompat.getDrawable(holder.itemView.context, background)
        }
    }
}
