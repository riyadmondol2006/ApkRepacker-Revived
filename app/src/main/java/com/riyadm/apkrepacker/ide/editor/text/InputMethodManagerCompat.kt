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

package com.riyadm.apkrepacker.ide.editor.text

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager


object InputMethodManagerCompat {
    @JvmStatic
    fun peekInstance(context: Context): InputMethodManager? {
        return context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    }

    @JvmStatic
    fun hideSoftInput(view: View) {
        val imm = peekInstance(view.context)
        imm?.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }


    @JvmStatic
    fun showSoftInput(view: View) {
        val imm = peekInstance(view.context)
        imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }
}
