package com.riyadm.apkrepacker.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.jecelyin.editor.v2.common.OnVisibilityChangedListener
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ide.editor.view.CodeEditor

/** One editor tab: the code editor plus a loading indicator (`progress_view`) shown while the file opens. */
open class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var editText: CodeEditor? = null
    private var progressView: View? = null
    private var removed = false
    private var visibilityChangedListener: OnVisibilityChangedListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        editText = findViewById(R.id.edit_text)
        progressView = findViewById<View>(R.id.progress_view)?.also { progress ->
            // A layout that doesn't place the indicator gets it centered.
            (progress.layoutParams as? FrameLayout.LayoutParams)
                ?.takeIf { it.gravity == FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY }
                ?.gravity = Gravity.CENTER
        }
    }

    fun getEditText(): CodeEditor? = editText

    fun setLoading(loading: Boolean) {
        editText?.visibility = if (loading) GONE else VISIBLE
        progressView?.visibility = if (loading) VISIBLE else GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removed = true
    }

    fun isRemoved(): Boolean = removed

    fun setRemoved() {
        removed = true
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        visibilityChangedListener?.onVisibilityChanged(visibility)
    }

    fun setVisibilityChangedListener(listener: OnVisibilityChangedListener?) {
        visibilityChangedListener = listener
    }
}
