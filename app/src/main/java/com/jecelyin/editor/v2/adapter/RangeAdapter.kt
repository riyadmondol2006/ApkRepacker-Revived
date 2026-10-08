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
package com.jecelyin.editor.v2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
open class RangeAdapter(
    @JvmField protected val minValue: Int,
    @JvmField protected val maxValue: Int,
    format: String?
) : BaseAdapter() {
    private val items: Array<CharSequence>
    private val values: Array<CharSequence>

    init {
        val count = count
        items = Array(count) { "" }
        values = Array(count) { "" }

        for (i in 0 until count) {
            val value = getValue(i)
            values[i] = value.toString()
            items[i] = if (format != null) String.format(format, value) else value.toString()
        }
    }

    open fun getItems(): Array<CharSequence> {
        return items
    }

    open fun getValues(): Array<CharSequence> {
        return values
    }

    override fun getCount(): Int {
        return maxValue - minValue + 1
    }

    open fun getValue(position: Int): Int {
        return minValue + position
    }

    override fun getItem(position: Int): CharSequence {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        val tv: TextView
        if (view == null) {
            view = LayoutInflater.from(parent!!.context).inflate(getLayoutResId(), parent, false)
            tv = view.findViewById(getTextResId())
            view.tag = tv
        } else {
            tv = view.tag as TextView
        }

        tv.text = getItem(position)
        setupTextView(tv, position)

        return view!!
    }

    protected open fun getLayoutResId(): Int {
        return android.R.layout.simple_list_item_1
    }

    protected open fun getTextResId(): Int {
        return android.R.id.text1
    }

    protected open fun setupTextView(tv: TextView, position: Int) {
    }
}
