package com.riyadm.apkrepacker.ui.filemanager

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import java.lang.ref.WeakReference

/**
 * Snackbar feedback for file operations. The currently visible file screen registers its root view
 * (and an optional anchor such as the FAB) so operations that only hold an application context can
 * still report their result without a Toast.
 */
object FileFeedback {
    private var host: WeakReference<View>? = null
    private var anchor: (() -> View?)? = null

    /** [anchorProvider] returns the view the Snackbar should sit above right now (FAB, action bar, ...). */
    fun attach(root: View, anchorProvider: (() -> View?)? = null) {
        host = WeakReference(root)
        anchor = anchorProvider
    }

    fun detach(root: View) {
        if (host?.get() === root) {
            host = null
            anchor = null
        }
    }

    /** Shows [message]; safe to call from any thread. Returns the Snackbar when shown synchronously. */
    fun show(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT): Snackbar? {
        val root = host?.get()?.takeIf { it.isAttachedToWindow } ?: return null
        if (Looper.myLooper() != Looper.getMainLooper()) {
            root.post { show(message, duration) }
            return null
        }
        return Snackbar.make(root, message, duration).also { bar ->
            anchor?.invoke()?.takeIf { it.visibility == View.VISIBLE }?.let { bar.setAnchorView(it) }
            bar.show()
        }
    }

    fun show(context: Context, @StringRes message: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar? =
        show(context.getString(message), duration)
}
