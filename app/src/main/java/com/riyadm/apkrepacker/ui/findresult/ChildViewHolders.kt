package com.riyadm.apkrepacker.ui.findresult

import android.view.View
import android.widget.TextView
import com.riyadm.apkrepacker.R
import com.thoughtbot.expandablerecyclerview.viewholders.ChildViewHolder

class ChildViewHolders(itemView: View) : ChildViewHolder(itemView) {

    private val matchLine: TextView = itemView.findViewById(R.id.match_line_text_view)

    val view: View get() = itemView

    fun setChildText(name: String?) {
        matchLine.text = name
    }

    fun setText(text: CharSequence?, type: TextView.BufferType?) {
        matchLine.setText(text, type)
    }
}
