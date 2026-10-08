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

package com.jecelyin.common.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jecelyin.common.R
import java.lang.ref.WeakReference

/**
 * Material 3 dialog and feedback helpers.
 *
 * Dialogs are built with [MaterialAlertDialogBuilder]; feedback goes through a [Snackbar] on the
 * foreground activity (a plain [Toast] is only the last resort when no window exists, e.g. the
 * app is in the background).
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
object UIUtils {

    private const val SNACKBAR_MAX_LINES = 5

    private val mainHandler = Handler(Looper.getMainLooper())
    private var foreground: WeakReference<Activity>? = null
    private var tracking = false

    /**
     * Optional hook so the host app can anchor snackbars above its bottom bar / FAB.
     * Return null to use the activity content view.
     */
    @JvmStatic
    var anchorResolver: ((Activity) -> View?)? = null

    /** Starts tracking the foreground activity. Safe to call more than once. */
    @JvmStatic
    fun install(application: Application) {
        if (tracking) return
        tracking = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                foreground = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (foreground?.get() === activity) foreground = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun usable(activity: Activity?): Activity? =
        activity?.takeUnless { it.isFinishing || it.isDestroyed }

    private fun activityOf(context: Context): Activity? {
        (context.applicationContext as? Application)?.let(::install)
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return usable(current)
            current = current.baseContext
        }
        return usable(foreground?.get())
    }

    // region feedback

    @JvmStatic
    fun toast(context: Context, @StringRes messageResId: Int) = toast(context, context.getString(messageResId))

    @JvmStatic
    fun toast(context: Context, @StringRes messageResId: Int, vararg args: Any?) =
        toast(context, context.getString(messageResId, *args))

    @JvmStatic
    fun toast(context: Context, message: String?) = showMessage(context, message)

    @JvmStatic
    fun toast(context: Context, t: Throwable) {
        DLog.e(t)
        showMessage(context, t.message)
    }

    private fun showMessage(context: Context, message: CharSequence?) {
        if (message.isNullOrEmpty()) return
        mainHandler.post {
            val activity = activityOf(context)
            val anchor = activity?.let { anchorResolver?.invoke(it) }
            val host = activity?.findViewById<View>(android.R.id.content)
            if (host != null) {
                Snackbar.make(host, message, Snackbar.LENGTH_LONG)
                    .setTextMaxLines(SNACKBAR_MAX_LINES)
                    .setAnchorView(anchor)
                    .show()
            } else {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // endregion

    // region alert

    @JvmStatic
    fun alert(context: Context, message: String?) = alert(context, null, message, null)

    @JvmStatic
    fun alert(context: Context, title: String?, message: String?) = alert(context, title, message, null)

    @JvmStatic
    fun alert(context: Context, message: String?, callback: OnClickCallback?) =
        alert(context, null, message, callback)

    @JvmStatic
    fun alert(context: Context, title: String?, message: String?, callback: OnClickCallback?) {
        val host = activityOf(context) ?: return showMessage(context, message)
        MaterialAlertDialogBuilder(host)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> callback?.onOkClick() }
            .apply { if (!title.isNullOrEmpty()) setTitle(title) }
            .show()
    }

    // endregion

    // region input

    @JvmStatic
    fun showInputDialog(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes hintRes: Int,
        value: CharSequence?,
        inputType: Int,
        callback: OnShowInputCallback?
    ) = showInputDialog(
        context,
        if (titleRes != 0) context.getString(titleRes) else null,
        if (hintRes != 0) context.getString(hintRes) else null,
        value,
        inputType,
        callback
    )

    @JvmStatic
    fun showInputDialog(
        context: Context,
        title: CharSequence?,
        hint: CharSequence?,
        value: CharSequence?,
        inputType: Int,
        callback: OnShowInputCallback?
    ) {
        val host = activityOf(context) ?: return
        val content = View.inflate(host, R.layout.common_dialog_input, null)
        val layout = content.findViewById<TextInputLayout>(R.id.common_input_layout)
        val edit = content.findViewById<TextInputEditText>(R.id.common_input_edit)
        layout.hint = hint
        edit.inputType = if (inputType == 0) EditorInfo.TYPE_CLASS_TEXT else inputType
        edit.setText(value)
        edit.setSelection(edit.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(host)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ -> callback?.onConfirm(edit.text?.toString().orEmpty()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
                true
            } else {
                false
            }
        }
        dialog.setOnShowListener {
            edit.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    // endregion

    // region confirm

    @JvmStatic
    fun showConfirmDialog(context: Context, @StringRes messageRes: Int, callback: OnClickCallback?) =
        showConfirmDialog(context, context.getString(messageRes), callback)

    @JvmStatic
    fun showConfirmDialog(context: Context, message: CharSequence?, callback: OnClickCallback?) =
        showConfirmDialog(context, null, message, callback)

    @JvmStatic
    fun showConfirmDialog(context: Context, title: CharSequence?, message: CharSequence?, callback: OnClickCallback?) =
        showConfirmDialog(
            context, title, message, callback,
            context.getString(android.R.string.ok), context.getString(android.R.string.cancel)
        )

    @JvmStatic
    fun showConfirmDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        callback: OnClickCallback?,
        @StringRes positiveRes: Int,
        @StringRes negativeRes: Int
    ) = showConfirmDialog(
        context,
        if (title == 0) null else context.getString(title),
        context.getString(message),
        callback,
        context.getString(positiveRes),
        context.getString(negativeRes)
    )

    @JvmStatic
    fun showConfirmDialog(
        context: Context,
        title: CharSequence?,
        message: CharSequence?,
        callback: OnClickCallback?,
        positiveStr: String?,
        negativeStr: String?
    ) {
        val host = activityOf(context) ?: return
        MaterialAlertDialogBuilder(host)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveStr) { _, _ -> callback?.onOkClick() }
            .setNegativeButton(negativeStr) { _, _ -> callback?.onCancelClick() }
            .show()
            .setCanceledOnTouchOutside(true)
    }

    // endregion

    // region single choice

    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        @StringRes messageRes: Int,
        @ArrayRes items: Int,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?
    ) = showListSingleChoiceDialog(
        context, context.getString(messageRes), context.resources.getTextArray(items),
        selectedIndex, singleChoiceCallback, callback
    )

    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        message: CharSequence?,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?
    ) = showListSingleChoiceDialog(context, null, message, items, selectedIndex, singleChoiceCallback, callback)

    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        title: CharSequence?,
        message: CharSequence?,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?
    ) = showListSingleChoiceDialog(
        context, title, message, items, selectedIndex, singleChoiceCallback, callback,
        context.getString(android.R.string.ok), context.getString(android.R.string.cancel)
    )

    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?
    ) = showListSingleChoiceDialog(
        context,
        if (title == 0) null else context.getString(title),
        if (message == 0) null else context.getString(message),
        items, selectedIndex, singleChoiceCallback, callback, null, null
    )

    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        @ArrayRes items: Int,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?
    ) = showListSingleChoiceDialog(
        context, title, message, context.resources.getTextArray(items),
        selectedIndex, singleChoiceCallback, callback
    )

    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        @ArrayRes items: Int,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?,
        @StringRes positiveRes: Int,
        @StringRes negativeRes: Int
    ) = showListSingleChoiceDialog(
        context,
        if (title == 0) null else context.getString(title),
        if (message == 0) null else context.getString(message),
        context.resources.getTextArray(items),
        selectedIndex, singleChoiceCallback, callback,
        context.getString(positiveRes), context.getString(negativeRes)
    )

    /**
     * Without a positive button the choice is delivered (and the dialog closed) as soon as an item is tapped;
     * with one, the selection is delivered when it is pressed.
     */
    @JvmStatic
    fun showListSingleChoiceDialog(
        context: Context,
        title: CharSequence?,
        message: CharSequence?,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        singleChoiceCallback: OnSingleChoiceCallback?,
        callback: OnClickCallback?,
        positiveStr: String?,
        negativeStr: String?
    ) {
        val host = activityOf(context) ?: return
        var checked = selectedIndex
        val immediate = positiveStr == null
        val builder = MaterialAlertDialogBuilder(host)
            .setTitle(title)
            .setMessage(message)
            .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                checked = which
                if (immediate) {
                    dialog.dismiss()
                    singleChoiceCallback?.onSelect(dialog as AlertDialog, which)
                }
            }
        if (!immediate) {
            builder.setPositiveButton(positiveStr) { dialog, _ ->
                if (checked >= 0) singleChoiceCallback?.onSelect(dialog as AlertDialog, checked)
                callback?.onOkClick()
            }
        }
        negativeStr?.let { builder.setNegativeButton(it) { _, _ -> callback?.onCancelClick() } }
        builder.show()
    }

    // endregion

    // region list

    @JvmStatic
    fun showListDialog(
        context: Context,
        @StringRes messageRes: Int,
        @ArrayRes items: Int,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?
    ) = showListDialog(
        context, context.getString(messageRes), context.resources.getTextArray(items),
        selectedIndex, listCallback, callback
    )

    @JvmStatic
    fun showListDialog(
        context: Context,
        message: CharSequence?,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?
    ) = showListDialog(context, null, message, items, selectedIndex, listCallback, callback)

    @JvmStatic
    fun showListDialog(
        context: Context,
        title: CharSequence?,
        message: CharSequence?,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?
    ) = showListDialog(
        context, title, message, items, selectedIndex, listCallback, callback,
        context.getString(android.R.string.ok), context.getString(android.R.string.cancel)
    )

    @JvmStatic
    fun showListDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?
    ) = showListDialog(
        context,
        if (title == 0) null else context.getString(title),
        if (message == 0) null else context.getString(message),
        items, selectedIndex, listCallback, callback, null, null
    )

    @JvmStatic
    fun showListDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        @ArrayRes items: Int,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?
    ) = showListDialog(
        context, title, message, context.resources.getTextArray(items),
        selectedIndex, listCallback, callback
    )

    @JvmStatic
    fun showListDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        @ArrayRes items: Int,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?,
        @StringRes positiveRes: Int,
        @StringRes negativeRes: Int
    ) = showListDialog(
        context,
        if (title == 0) null else context.getString(title),
        if (message == 0) null else context.getString(message),
        context.resources.getTextArray(items),
        selectedIndex, listCallback, callback,
        context.getString(positiveRes), context.getString(negativeRes)
    )

    @JvmStatic
    fun showListDialog(
        context: Context,
        title: CharSequence?,
        message: CharSequence?,
        items: Array<out CharSequence>,
        selectedIndex: Int,
        listCallback: OnListCallback?,
        callback: OnClickCallback?,
        positiveStr: String?,
        negativeStr: String?
    ) {
        val host = activityOf(context) ?: return
        MaterialAlertDialogBuilder(host)
            .setTitle(title)
            .setMessage(message)
            .setItems(items) { dialog, which -> listCallback?.onSelect(dialog as AlertDialog, which) }
            .apply {
                positiveStr?.let { setPositiveButton(it) { _, _ -> callback?.onOkClick() } }
                negativeStr?.let { setNegativeButton(it) { _, _ -> callback?.onCancelClick() } }
            }
            .show()
    }

    // endregion

    @JvmStatic
    fun showIconInPopup(popupMenu: PopupMenu) = popupMenu.setForceShowIcon(true)

    abstract class OnClickCallback {
        abstract fun onOkClick()
        open fun onCancelClick() = Unit
    }

    abstract class OnListCallback {
        abstract fun onSelect(dialog: AlertDialog, which: Int)
    }

    abstract class OnSingleChoiceCallback {
        abstract fun onSelect(dialog: AlertDialog, which: Int)
    }

    abstract class OnShowInputCallback {
        abstract fun onConfirm(input: CharSequence?)
    }
}
