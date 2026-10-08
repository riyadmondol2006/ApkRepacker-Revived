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

package com.riyadm.apkrepacker.ide.editor.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardCompat(private val mContext: Context) {
    private val mClipboardManager: ClipboardManager? =
        mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

    fun getClipboard(): CharSequence? {
        if (mClipboardManager == null) {
            return null
        }
        // Examines the item on the clipboard. If getText() does not return null,
        // the clip item contains the
        // text. Assumes that this application can only handle one item at a time.
        // Null since API 29 when the app isn't focused, and when the clipboard is empty.
        val clip = mClipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        // Gets the clipboard as text.
        return clip.getItemAt(0).text
    }

    fun setClipboard(content: CharSequence) {
        val clipData = ClipData.newPlainText("", content)
        mClipboardManager?.setPrimaryClip(clipData)
    }
}
