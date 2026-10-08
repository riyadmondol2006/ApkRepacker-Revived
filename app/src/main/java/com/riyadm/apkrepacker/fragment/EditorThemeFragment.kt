/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.jecelyin.editor.v2.EditorPreferences
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentEditorThemeBinding
import com.riyadm.apkrepacker.databinding.ListItemThemeBinding
import com.riyadm.apkrepacker.ide.editor.lexer.XmlLexTask
import com.riyadm.apkrepacker.ide.editor.theme.ThemeLoader
import com.riyadm.apkrepacker.ide.editor.theme.model.EditorTheme
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lists the bundled syntax-highlight themes, each previewed on a code sample. */
class EditorThemeFragment : Fragment() {

    private var binding: FragmentEditorThemeBinding? = null
    private val adapter = EditorThemeAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentEditorThemeBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = checkNotNull(binding)
        adapter.onThemeSelectListener = activity as? EditorThemeAdapter.OnThemeSelectListener
        adapter.currentFileName = EditorPreferences.getInstance(requireContext()).editorTheme.fileName

        ui.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        ui.recyclerView.adapter = adapter
        ui.recyclerView.applyExpressiveMotion()

        loadThemes(ui)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun loadThemes(ui: FragmentEditorThemeBinding) {
        val context = requireContext().applicationContext
        ui.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val themes = withContext(Dispatchers.IO) {
                runCatching { ThemeLoader.getAll(context).filterNotNull().sortedBy { it.fileName } }
            }
            ui.progressBar.visibility = View.GONE
            themes.onSuccess { list ->
                adapter.submit(list)
                val current = adapter.positionOfCurrent()
                if (current > 0) ui.recyclerView.scrollToPosition(current)
            }.onFailure {
                Snackbar.make(ui.root, R.string.editor_theme_load_failed, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    class EditorThemeAdapter : RecyclerView.Adapter<EditorThemeAdapter.ViewHolder>() {

        private val themes = ArrayList<EditorTheme>()
        var onThemeSelectListener: OnThemeSelectListener? = null
        var currentFileName: String? = null

        fun submit(list: List<EditorTheme>) {
            themes.clear()
            themes.addAll(list)
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        fun getPosition(editorTheme: EditorTheme?): Int = themes.indexOf(editorTheme)

        fun positionOfCurrent(): Int = themes.indexOfFirst { it.fileName == currentFileName }

        override fun getItemCount(): Int = themes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ListItemThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = themes[position]
            val selected = theme.fileName == currentFileName
            with(holder.binding) {
                txtName.text = "${position + 1}. ${theme.themeModel.themeName}"
                btnSelect.setText(if (selected) R.string.editor_theme_current else R.string.select)
                btnSelect.isEnabled = !selected
                editorView.setTheme(theme)
                editorView.setText(EditorThemeFragment.SAMPLE_CODE)
                editorView.setLexTask(XmlLexTask())
                editorView.setReadOnly(true)
                btnSelect.setOnClickListener {
                    currentFileName = theme.fileName
                    onThemeSelectListener?.onEditorThemeSelected(theme)
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }

        fun interface OnThemeSelectListener {
            fun onEditorThemeSelected(theme: EditorTheme)
        }

        class ViewHolder(val binding: ListItemThemeBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                // The editor view keeps its own scroll / lexer state: never reuse it for another theme.
                setIsRecyclable(false)
                binding.editorView.setReadOnly(true)
            }
        }
    }

    companion object {
        internal const val SAMPLE_CODE = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.app">
    <!-- the main screen -->
    <application android:label="@string/app_name" android:debuggable="false">
        <activity android:name=".MainActivity" android:exported="true" />
    </application>
</manifest>
"""
    }
}
