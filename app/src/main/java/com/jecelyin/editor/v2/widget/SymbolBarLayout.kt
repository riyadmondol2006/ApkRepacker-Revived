/*
 * Copyright 2018 Mr Duy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.jecelyin.editor.v2.EditorPreferences
import com.riyadm.apkrepacker.R

/**
 * The row of symbol keys inside the editor's floating toolbar. The keys come from the
 * `pref_symbol` preference (one per line); `\t` and `\n` stand for the control characters.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class SymbolBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener {

    private var onSymbolCharClickListener: OnSymbolCharClickListener? = null
    private val symbols: List<String>

    init {
        orientation = HORIZONTAL
        val symbol = if (isInEditMode) EditorPreferences.VALUE_SYMBOL else EditorPreferences.getInstance(context).symbol
        symbols = TextUtils.split(symbol.orEmpty(), "\n").filter { it.isNotEmpty() }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val inflater = LayoutInflater.from(context)
        for (symbol in symbols) {
            val key = inflater.inflate(R.layout.list_item_symbol, this, false) as TextView
            key.text = symbol
            key.setOnClickListener(this)
            addView(key)
        }
    }

    override fun onClick(v: View) {
        val listener = onSymbolCharClickListener ?: return
        val text = when (val label = (v as TextView).text.toString()) {
            "\\t" -> "\t"
            "\\n" -> "\n"
            else -> label
        }
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener.onClick(v, text)
    }

    fun setOnSymbolCharClickListener(listener: OnSymbolCharClickListener?) {
        onSymbolCharClickListener = listener
    }

    fun interface OnSymbolCharClickListener {
        fun onClick(v: View?, text: String?)
    }
}
