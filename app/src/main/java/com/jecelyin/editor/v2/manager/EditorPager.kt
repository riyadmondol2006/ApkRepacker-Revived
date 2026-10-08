package com.jecelyin.editor.v2.manager

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class EditorPager : ViewPager {

    constructor(context: Context) : super(context) {
        isFocusable = true
    }

    constructor(context: Context, attributes: AttributeSet?) : super(context, attributes) {
        isFocusable = true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }
}
