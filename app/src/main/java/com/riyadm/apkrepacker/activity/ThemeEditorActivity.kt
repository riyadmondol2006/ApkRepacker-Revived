package com.riyadm.apkrepacker.activity

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import com.jecelyin.editor.v2.EditorPreferences
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ActivityEditorTehemesBinding
import com.riyadm.apkrepacker.fragment.EditorThemeFragment
import com.riyadm.apkrepacker.ide.editor.theme.model.EditorTheme

/** Picks the syntax-highlight theme of the code editor. */
class ThemeEditorActivity : BaseActivity(), EditorThemeFragment.EditorThemeAdapter.OnThemeSelectListener {

    private lateinit var binding: ActivityEditorTehemesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorTehemesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.appBar.setLiftOnScrollTargetViewId(R.id.recyclerView)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, EditorThemeFragment())
                .commit()
        }
    }

    override fun onEditorThemeSelected(theme: EditorTheme) {
        EditorPreferences.getInstance(this).setEditorTheme(theme.fileName)
        setResult(RESULT_OK)
        Snackbar.make(binding.root, getString(R.string.selected_editor_theme, theme.name), Snackbar.LENGTH_SHORT).show()
    }
}
