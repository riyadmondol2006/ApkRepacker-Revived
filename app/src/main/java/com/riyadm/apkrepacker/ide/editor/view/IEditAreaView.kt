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
import android.os.Parcelable

import com.riyadm.codeeditor.util.DocumentProvider
import com.riyadm.codeeditor.util.LexTask

/**
 * Members that the implementing views inherit from [android.view.View] or
 * [com.riyadm.codeeditor.view.FreeScrollingTextField] (Java) are declared as plain functions so
 * that the inherited Java methods implement them.
 */
interface IEditAreaView : IEditActionSupport, IdeEditor {

    val selectedText: CharSequence?

    val text: DocumentProvider

    val editorView: HighlightEditorView?

    val isChanged: Boolean

    fun setText(spannable: CharSequence?)

    fun setEnabled(enable: Boolean)

    fun post(runnable: Runnable?): Boolean

    fun setLexTask(lexer: LexTask?)

    fun setSelection(start: Int, end: Int)

    fun setReadOnly(readOnly: Boolean)

    val lang: String?

    fun setOnEditStateChangedListener(listener: HighlightEditorView.OnEditStateChangedListener?)


    fun onRestoreInstanceState(editorState: Parcelable?)

    //void addTextChangedListener(TextWatcher textWatcher);

    // void removeTextChangedListener(TextWatcher textWatcher);

    fun hasSelection(): Boolean

    fun getSelectionStart(): Int

    fun getSelectionEnd(): Int

    fun gotoLine(line: Int)

    fun gotoTop()

    fun gotoEnd()

    fun requestFocus(): Boolean

    fun length(): Int

    fun setFreezesText(b: Boolean)

    fun onSaveInstanceState(): Parcelable?

    fun getContext(): Context

    fun clearFocus()

}
