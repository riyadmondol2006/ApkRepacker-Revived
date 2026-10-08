package com.jecelyin.editor.v2.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R

/**
 * Short feedback for the code editor: a Snackbar in the editor's coordinator, lifted above the
 * find bar and symbol toolbar when they are on screen. Replaces the toasts and alert dialogs the
 * editor used to show.
 */
object EditorMessages {

    @JvmStatic
    fun show(context: Context, @StringRes message: Int, duration: Int = Snackbar.LENGTH_SHORT) {
        show(context, context.getString(message), duration)
    }

    @JvmStatic
    fun show(context: Context, message: CharSequence?, duration: Int = Snackbar.LENGTH_SHORT) {
        if (message.isNullOrEmpty()) return
        val activity = context.findActivity() ?: return
        val root = activity.findViewById<View>(R.id.editor_coordinator)
            ?: activity.findViewById<View>(android.R.id.content)
            ?: return
        root.post {
            val snackbar = Snackbar.make(root, message, duration)
            activity.findViewById<View>(R.id.editor_bottom)
                ?.takeIf { it.isShown && it.height > 0 }
                ?.let { snackbar.setAnchorView(it) }
            snackbar.show()
        }
    }

    /** Errors stay a little longer. */
    @JvmStatic
    fun error(context: Context, message: CharSequence?) = show(context, message, Snackbar.LENGTH_LONG)
}

/** Unwraps themed/wrapped contexts down to the hosting [Activity]. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
