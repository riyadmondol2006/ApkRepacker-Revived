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

package com.riyadm.apkrepacker.ide.editor.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet

import com.riyadm.apkrepacker.ide.editor.theme.model.EditorTheme
import com.riyadm.apkrepacker.utils.AppExecutor
import com.riyadm.codeeditor.view.ColorScheme

class CodeEditor : HighlightEditorView {
    /**
     * Editor color schemes, include text color, text background and more color attrs
     */
    private var mEditorTheme: EditorTheme? = null

    private var mAppExecutor: AppExecutor? = null

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun init(context: Context) {
        mAppExecutor = AppExecutor.getInstance()
        mAppExecutor!!.diskIO.execute {
            setTheme(mEditorPreferences!!.editorTheme)
        }
    }

    override fun setTheme(editorTheme: EditorTheme) {
        mEditorTheme = editorTheme
        setBackgroundColor(getColor(editorTheme.themeModel.viewBackgroundColor))
        setTextColor(getColor(editorTheme.themeModel.viewDefault))
        setTextHighlightColor(getColor(editorTheme.themeModel.viewSelectionColor))
        setGutterBackgroundColor(getColor(editorTheme.themeModel.viewGutterBackgroundColor))
        setLineNumberTextColor(getColor(editorTheme.themeModel.viewGutterForegroundColor))
        // setLineHighlightColor(editorTheme.getLineHighlightColor());
        setWhiteSpaceColor(getColor(editorTheme.themeModel.viewWhitespaceColor))

        //syntax
        setKeywordColor(getColor(editorTheme.themeModel.viewKeyword))
        setBaseWordColor(getColor(editorTheme.themeModel.viewName))
        setCommentColor(getColor(editorTheme.themeModel.viewComment))
        setLiteralColor(getColor(editorTheme.themeModel.viewLiteral))
        setOperatorColor(getColor(editorTheme.themeModel.viewOperator))
        setTypeColor(getColor(editorTheme.themeModel.viewOperator))
        setSeparatorColor(getColor(editorTheme.themeModel.viewSeparator))
        setPackageColor(getColor(editorTheme.themeModel.viewPackage))
        setErrorColor(getColor(editorTheme.themeModel.viewError))

        postInvalidate()
        //});
    }

    override val editorTheme: EditorTheme?
        get() = mEditorTheme

    private fun getColor(attr: String?): Int {
        val color = Color.parseColor(attr)
        if (Color.alpha(color) == 0 && color != Color.TRANSPARENT) {
            return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
        }
        return color
    }

    fun setGutterBackgroundColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.GUTTER_FOREGROUND, color)
    }

    fun setKeywordColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.KEYWORD, color)
    }

    fun setBaseWordColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.NAME, color)
    }

    fun setLiteralColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.LITERAL, color)
    }

    fun setOperatorColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.OPERATOR, color)
    }

    fun setSeparatorColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.SEPARATOR, color)
    }

    fun setTypeColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.TYPE, color)
    }

    fun setErrorColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.ERROR, color)
    }

    fun setPackageColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.PACKAGE, color)
    }

    fun setCommentColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.COMMENT, color)
    }

    override fun setBackgroundColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.BACKGROUND, color)
    }

    fun setTextColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.TEXT, color)
    }

    fun setLineNumberTextColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.GUTTER_LINENUMBER, color)
    }

    fun setLineHighlightColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.LINE_HIGHLIGHT, color)
    }

    fun setWhiteSpaceColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.NON_PRINTING_GLYPH, color)
    }

    fun setTextHighlightColor(color: Int) {
        colorScheme.setColor(ColorScheme.Colorable.SELECTION_BACKGROUND, color)
    }

    /*
    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        return super.requestFocus(direction, previouslyFocusedRect);
    }

     */
}
