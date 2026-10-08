package com.riyadm.apkrepacker.activity

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.riyadm.apkrepacker.App
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.update.UpdateUi
import com.riyadm.apkrepacker.databinding.ActivityMainBinding
import com.riyadm.apkrepacker.fragment.AboutFragment
import com.riyadm.apkrepacker.fragment.AppsFragment
import com.riyadm.apkrepacker.fragment.OnBackPressedListener
import com.riyadm.apkrepacker.fragment.ProjectsFragment
import com.riyadm.apkrepacker.fragment.SettingsFragment
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo
import com.riyadm.apkrepacker.ui.preferences.InitPreference
import com.riyadm.apkrepacker.utils.AppExecutor
import com.riyadm.apkrepacker.utils.FragmentNavigator

/**
 * Main shell: four top-level destinations behind a bottom navigation bar (compact widths) or a
 * navigation rail (layout-w600dp). Both layouts use the id `nav_view`, so one code path serves them.
 */
class MainActivity : BaseActivity(), FragmentNavigator.FragmentFactory {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navigator: FragmentNavigator
    private var projectsFragment: ProjectsFragment? = null

    /** Whether the inflated layout is the rail variant; flipping it needs a new layout. */
    private var wideLayout = false

    // `nav_view` is a BottomNavigationView or a NavigationRailView depending on the width.
    @Suppress("USELESS_CAST")
    private val navView: NavigationBarView
        get() = binding.navView as NavigationBarView

    /** The bottom navigation bar (not the rail) for snackbars to sit above; null on wide layouts. */
    val snackbarAnchor: View?
        get() = if (wideLayout || !::binding.isInitialized) null else binding.navView

    private val destinations = mapOf(
        R.id.navigation_home to ProjectsFragment.TAG,
        R.id.navigation_apps to AppsFragment.TAG,
        R.id.navigation_settings to SettingsFragment.TAG,
        R.id.navigation_about to AboutFragment.TAG,
    )

    /**
     * Back handling (onBackPressed() isn't called with predictive back). Registered after
     * super.onCreate, so it runs before the FragmentManager's own back-stack callback.
     */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val listener = supportFragmentManager.fragments.firstOrNull { it is OnBackPressedListener }
            if (listener is OnBackPressedListener) listener.onBackPressed() else performDefaultBack(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wideLayout = resources.configuration.screenWidthDp >= WIDE_MIN_WIDTH_DP
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        App.setContext(this)
        AppExecutor.getInstance().diskIO.execute {
            InitPreference().init(this)
        }

        navigator = FragmentNavigator(savedInstanceState, supportFragmentManager, R.id.container_main, this)
        navigator.setTabOrder(ProjectsFragment.TAG, AppsFragment.TAG, SettingsFragment.TAG, AboutFragment.TAG)
        projectsFragment = navigator.findFragmentByTag(ProjectsFragment.TAG)

        // Select the saved destination before the listener exists, so restoring doesn't re-navigate.
        navView.selectedItemId = savedInstanceState?.getInt(STATE_SELECTED_ITEM, R.id.navigation_home) ?: R.id.navigation_home
        navView.setOnItemSelectedListener { item ->
            val tag = destinations[item.itemId] ?: return@setOnItemSelectedListener false
            navigator.switchTo(tag)
            popItem(item.itemId)
            true
        }
        setUpRailToggle(savedInstanceState?.getBoolean(STATE_RAIL_EXPANDED) ?: false)

        if (savedInstanceState == null) {
            navigator.switchTo(ProjectsFragment.TAG)
            // A fresh open (not a rotation): the channel invite the first time, else the update check.
            UpdateUi.onAppOpen(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest handles orientation / size changes itself; swap bar <-> rail when the width class changes.
        if ((newConfig.screenWidthDp >= WIDE_MIN_WIDTH_DP) != wideLayout) recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigator.writeStateToBundle(outState)
        outState.putInt(STATE_SELECTED_ITEM, navView.selectedItemId)
        (binding.navView as? NavigationRailView)?.let { outState.putBoolean(STATE_RAIL_EXPANDED, it.isExpanded) }
    }

    override fun createFragment(tag: String): Fragment = when (tag) {
        ProjectsFragment.TAG -> projectsFragment ?: ProjectsFragment().also { projectsFragment = it }
        AppsFragment.TAG -> AppsFragment()
        SettingsFragment.TAG -> SettingsFragment()
        AboutFragment.TAG -> AboutFragment()
        else -> throw IllegalArgumentException("Unknown fragment tag: $tag")
    }

    /** The selected destination's item springs up from slightly squished, on top of the built-in indicator motion. */
    private fun popItem(itemId: Int) {
        val item = navView.findViewById<View>(itemId) ?: return
        item.scaleX = POP_FROM_SCALE
        item.scaleY = POP_FROM_SCALE
        item.springTo(DynamicAnimation.SCALE_X, 1f, MotionSpring.FastSpatial)
        item.springTo(DynamicAnimation.SCALE_Y, 1f, MotionSpring.FastSpatial)
    }

    private fun setUpRailToggle(expanded: Boolean) {
        val rail = binding.navView as? NavigationRailView ?: return
        val toggle = rail.headerView?.findViewById<MaterialButton>(R.id.nav_rail_toggle) ?: return
        fun render() {
            toggle.contentDescription = getString(if (rail.isExpanded) R.string.nav_rail_collapse else R.string.nav_rail_expand)
        }
        if (expanded) rail.expand()
        render()
        toggle.setOnClickListener {
            if (rail.isExpanded) rail.collapse() else rail.expand()
            render()
        }
    }

    private companion object {
        const val WIDE_MIN_WIDTH_DP = 600
        const val POP_FROM_SCALE = 0.85f
        const val STATE_SELECTED_ITEM = "main_selected_nav_item"
        const val STATE_RAIL_EXPANDED = "main_rail_expanded"
    }
}
