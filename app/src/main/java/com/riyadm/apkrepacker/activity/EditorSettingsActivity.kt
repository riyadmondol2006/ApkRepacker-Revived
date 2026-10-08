package com.riyadm.apkrepacker.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ActivityEditorSettingBinding
import com.riyadm.apkrepacker.fragment.SettingsFragmentEditor

/** Code editor preferences under a large collapsing app bar. */
class EditorSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityEditorSettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appBar.setLiftOnScrollTargetViewId(androidx.preference.R.id.recycler_view)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, SettingsFragmentEditor())
                .commit()
        }
    }

    companion object {
        @JvmStatic
        fun open(activity: Activity, requestCode: Int) {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(Intent(activity, EditorSettingsActivity::class.java), requestCode)
        }
    }
}
