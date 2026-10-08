package com.riyadm.apkrepacker.task

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/** Result messages of background tasks are shown as Snackbars on the screen that started them. */
internal fun Fragment.showTaskMessage(@StringRes message: Int, vararg args: Any) {
    val anchor = view ?: activity?.findViewById(android.R.id.content) ?: return
    Snackbar.make(anchor, getString(message, *args), Snackbar.LENGTH_LONG).show()
}

internal fun Activity.showTaskMessage(@StringRes message: Int, vararg args: Any) {
    val anchor: View = findViewById(android.R.id.content) ?: return
    Snackbar.make(anchor, getString(message, *args), Snackbar.LENGTH_LONG).show()
}
