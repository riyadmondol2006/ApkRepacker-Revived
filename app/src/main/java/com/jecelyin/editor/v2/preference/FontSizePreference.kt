/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.preference

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import com.jecelyin.editor.v2.EditorPreferences
import com.jecelyin.editor.v2.adapter.RangeAdapter

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class FontSizePreference : JecListPreference {

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    fun init() {
        val adapter = ItemAdapter(EditorPreferences.DEF_MIN_FONT_SIZE, EditorPreferences.DEF_MAX_FONT_SIZE, "%d sp")
        setEntries(adapter.getItems())
        setEntryValues(adapter.getValues())
        setAdapter(adapter)
    }

    internal class ItemAdapter(min: Int, max: Int, format: String?) : RangeAdapter(min, max, format) {

        public override fun setupTextView(tv: TextView, position: Int) {
            val fontSize = getValue(position)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        }
    }
}
