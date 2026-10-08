package com.riyadm.apkrepacker.fragment.dialogs

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Base for small helper classes that build and show a Material 3 dialog from a plain [Context]. */
abstract class BaseDialog(private val context: Context?) {

    /** Builds and shows the dialog; the returned object is the builder that was used. */
    abstract fun show(): Any?

    protected fun getBuilder(): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(requireNotNull(context) { "BaseDialog needs a Context" })
    }

    protected fun getLayoutInflater(): LayoutInflater {
        return LayoutInflater.from(context)
    }

    protected fun getContext(): Context? {
        return context
    }

    protected fun getString(id: Int): CharSequence {
        return requireNotNull(context).getString(id)
    }
}
