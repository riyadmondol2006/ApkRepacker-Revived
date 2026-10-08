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

package com.riyadm.apkrepacker.ide.editor.theme.model

import com.google.gson.GsonBuilder

open class EditorTheme : ColorScheme() {

    /**
     * File name in assets
     */
    var fileName: String? = null

    var name: String? = null
        set(name) {
            if (name!!.isEmpty()) {
                return
            }
            val builder = StringBuilder()
            builder.append(name.replace("-", " "))
            builder.setCharAt(0, Character.toUpperCase(builder[0]))
            for (i in 0 until builder.length) {
                if (builder[i] == ' ' && i + 1 < builder.length) {
                    builder.setCharAt(i + 1, Character.toUpperCase(builder[i + 1]))
                }
            }
            field = builder.toString()
        }
    private var mThemeModel: ThemeModel? = null

    val themeModel: ThemeModel
        get() = mThemeModel!!

    override fun load(json: String?) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        mThemeModel = gson.fromJson(json, ThemeModel::class.java)
    }

    companion object {
        private const val TAG = "EditorTheme"
    }

}
