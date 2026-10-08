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

package com.riyadm.apkrepacker.ide.editor.theme

import android.content.Context
import android.content.res.AssetManager

import com.riyadm.apkrepacker.ide.editor.theme.model.EditorTheme
import com.riyadm.codeeditor.util.DLog

import org.apache.commons.io.IOUtils

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap

object ThemeLoader {
    const val ASSET_PATH = "themes"
    private const val DEFAULT_EDITOR_THEME_LIGHT = "idea.json"
    private val CACHED = HashMap<String?, EditorTheme?>()
    private const val TAG = "ThemeLoader"

    @JvmStatic
    fun init(context: Context) {
        try {
            val themes = context.assets.list(ASSET_PATH)
            for (theme in themes!!) {
                loadTheme(context, theme)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun getTheme(context: Context, fileName: String?): EditorTheme? {
        return loadTheme(context, fileName)
    }

    @JvmStatic
    fun getAll(context: Context): ArrayList<EditorTheme> {
        val themes = ArrayList<EditorTheme?>()
        try {
            val names = context.assets.list(ASSET_PATH)
            for (name in names!!) {
                val theme = loadTheme(context, name)
                themes.add(theme)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        // Like the Java original, the list may hold null entries for themes that failed to load.
        @Suppress("UNCHECKED_CAST")
        return themes as ArrayList<EditorTheme>
    }

    @JvmStatic
    fun loadDefault(context: Context): EditorTheme {
        // the default theme is bundled in assets/themes, so it always loads
        return loadTheme(context, DEFAULT_EDITOR_THEME_LIGHT)!!
    }

    private fun loadTheme(context: Context, fileName: String?): EditorTheme? {
        if (CACHED[fileName] != null) {
            return CACHED[fileName]
        }
        val editorTheme = loadFromAsset(context.assets, fileName)
        CACHED[fileName] = editorTheme
        return editorTheme
    }

    private fun loadFromAsset(assets: AssetManager, fileName: String?): EditorTheme? {
        try {
            val input = assets.open("$ASSET_PATH/$fileName")
            val content = IOUtils.toString(input, StandardCharsets.UTF_8)
            input.close()

            val editorTheme = loadTheme(content)
            editorTheme.fileName = fileName
            return editorTheme
        } catch (e: IOException) {
            if (DLog.DEBUG) DLog.w(TAG, "loadFromAsset: Can not load theme $fileName")
        }

        return null
    }

    private fun loadTheme(json: String): EditorTheme {
        val editorTheme = EditorTheme()
        editorTheme.load(json)
        return editorTheme
    }

}
