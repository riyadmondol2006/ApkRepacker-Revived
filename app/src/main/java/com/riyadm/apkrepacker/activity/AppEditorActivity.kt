package com.riyadm.apkrepacker.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.adapter.FragmentAdapter
import com.riyadm.apkrepacker.databinding.ActivityEditorApkBinding
import com.riyadm.apkrepacker.fragment.CompileFragment
import com.riyadm.apkrepacker.fragment.FilesFragment
import com.riyadm.apkrepacker.fragment.FindFragment
import com.riyadm.apkrepacker.fragment.OnBackPressedListener
import com.riyadm.apkrepacker.fragment.PatcherFragment
import com.riyadm.apkrepacker.fragment.StringsFragment
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.stringlist.DirectoryScanner
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.StringUtils
import java.io.File
import java.lang.ref.WeakReference

/**
 * Project workspace: a header with the app identity and the patch / build actions, and a tabbed pager
 * with the project's strings (when it has any), its files and the project-wide search.
 */
class AppEditorActivity : BaseActivity() {

    internal val stringsFragment: Fragment = StringsFragment()
    internal val filesFragment: Fragment = FilesFragment()
    internal val findFragment = FindFragment()

    /** Read by [FilesFragment] to switch tabs. */
    @JvmField
    var mViewPager: ViewPager2? = null

    private lateinit var binding: ActivityEditorApkBinding
    private var projectPath: String? = null
    private var tabFragments: List<Fragment> = emptyList()
    private var apkIconBase64: String? = null
    private var apkFileName: String? = null
    private var apkPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extras can be missing when the activity is recreated without its launch intent
        // (e.g. reopened from Recents); fall back to the saved state and bail out if the project is gone.
        projectPath = intent.getStringExtra(EXTRA_PROJECT_PATH) ?: savedInstanceState?.getString(EXTRA_PROJECT_PATH)
        apkIconBase64 = intent.getStringExtra(EXTRA_ICON) ?: savedInstanceState?.getString(EXTRA_ICON)
        apkFileName = intent.getStringExtra(EXTRA_NAME) ?: savedInstanceState?.getString(EXTRA_NAME)
        apkPackageName = intent.getStringExtra(EXTRA_PACKAGE) ?: savedInstanceState?.getString(EXTRA_PACKAGE)

        val path = projectPath
        if (path.isNullOrEmpty() || !File(path).isDirectory) {
            Log.w(TAG, "Project path missing or no longer exists: $path")
            finish()
            return
        }
        instanceRef = WeakReference(this)

        binding = ActivityEditorApkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, backCallback)

        ProjectUtils.setProjectPath(path)
        setUpHeader()
        setUpTabs(path)
    }

    private fun setUpHeader() = with(binding.editorHeader) {
        appName.text = apkFileName
        appPkg.text = apkPackageName
        appPkg.visibility = if (apkPackageName == null) View.GONE else View.VISIBLE
        appIcon.setImageDrawable(ProjectUtils.getProjectIconDrawable(apkIconBase64, this@AppEditorActivity))
        appIcon.springIn(fromScale = 0.6f)

        buildApp.setOnClickListener { confirmAndBuild() }
        patchApp.setOnClickListener { openPatcher() }
    }

    private fun setUpTabs(path: String) {
        val args = Bundle().apply { putString("prjPatch", path) }
        val titles = mutableListOf<String>()
        val pages = mutableListOf<Fragment>()

        if (hasStringFiles(path)) {
            titles += getString(R.string.menu_string)
            stringsFragment.arguments = args
            pages += stringsFragment
        }
        titles += getString(R.string.menu_files)
        filesFragment.arguments = args
        pages += filesFragment

        titles += getString(R.string.menu_find)
        pages += findFragment
        tabFragments = pages

        val pageAdapter = FragmentAdapter(this, pages, titles)
        val pager = binding.tabPager
        mViewPager = pager
        pager.offscreenPageLimit = 2
        pager.adapter = pageAdapter

        TabLayoutMediator(binding.tabs, pager) { tab, position -> tab.text = pageAdapter.getPageTitle(position) }.attach()
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position >= 1) StringUtils.hideKeyboard(this@AppEditorActivity)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    /** The strings tab only appears when the project actually has string resources. */
    private fun hasStringFiles(path: String): Boolean {
        // A project with resources.arsc but no res dir was never decoded, so there is nothing to scan.
        if (File(path, "resources.arsc").exists() && !File(path, "res").exists()) return false
        return DirectoryScanner().findStringFiles(path).isNotEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_PROJECT_PATH, projectPath)
        outState.putString(EXTRA_ICON, apkIconBase64)
        outState.putString(EXTRA_NAME, apkFileName)
        outState.putString(EXTRA_PACKAGE, apkPackageName)
    }

    override fun onDestroy() {
        if (instanceRef?.get() === this) instanceRef = null
        super.onDestroy()
    }

    private fun openPatcher() {
        StringUtils.hideKeyboard(this)
        if (supportFragmentManager.findFragmentByTag(PatcherFragment.TAG) != null) return
        FragmentUtils.add(PatcherFragment.newInstance(), supportFragmentManager, android.R.id.content, PatcherFragment.TAG)
    }

    private fun confirmAndBuild() {
        StringUtils.hideKeyboard(this)
        if (PreferenceHelper.getInstance(this).isConfirmBuild) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_build_title)
                .setPositiveButton(R.string.ok) { _, _ -> showCompileFragment() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            showCompileFragment()
        }
    }

    private fun showCompileFragment() {
        FragmentUtils.replace(CompileFragment.newInstance(projectPath), this, android.R.id.content)
    }

    /** Back is routed by hand because the tabs, the patcher and the compile screen all live in one activity. */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val patcher = supportFragmentManager.findFragmentByTag(PatcherFragment.TAG)
            when {
                patcher != null -> FragmentUtils.remove(patcher)
                supportFragmentManager.backStackEntryCount > 0 -> performDefaultBack(this)
                else -> {
                    // After recreation the pager reuses fragments restored by the FragmentManager, so the
                    // activity fields are not the attached instances: look the current page up by its tag.
                    val current = mViewPager?.currentItem ?: -1
                    val page = if (current < 0) null else
                        supportFragmentManager.findFragmentByTag("f$current")?.takeIf { it.isAdded }
                    when {
                        page is OnBackPressedListener -> page.onBackPressed()
                        page != null && page.childFragmentManager.backStackEntryCount > 0 ->
                            page.childFragmentManager.popBackStack()
                        else -> performDefaultBack(this)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AppEditorActivity"
        private const val EXTRA_PROJECT_PATH = "projectPatch"
        private const val EXTRA_ICON = "apkFileIcon"
        private const val EXTRA_NAME = "apkFileName"
        private const val EXTRA_PACKAGE = "apkFilePackageName"

        private var instanceRef: WeakReference<AppEditorActivity>? = null

        @JvmStatic
        fun getInstance(): AppEditorActivity? = instanceRef?.get()
    }
}
