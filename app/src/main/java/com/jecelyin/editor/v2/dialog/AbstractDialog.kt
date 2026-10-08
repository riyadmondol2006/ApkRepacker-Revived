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
package com.jecelyin.editor.v2.dialog

import android.app.Dialog
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jecelyin.editor.v2.widget.findActivity
import com.riyadm.apkrepacker.activity.TextEditorActivity

/**
 * Base of the editor's dialogs. They are Material 3 alert dialogs built from [getBuilder]; the
 * code editor activity is the only host.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
abstract class AbstractDialog(@JvmField protected val context: Context) {

    /** Dialogs that would lose typed input keep ignoring outside taps. */
    protected open fun handleDialog(dlg: Dialog) {
        dlg.setCanceledOnTouchOutside(false)
        dlg.setCancelable(true)
    }

    protected open fun getBuilder(): MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

    protected open fun getMainActivity(): TextEditorActivity =
        checkNotNull(context.findActivity() as? TextEditorActivity) { "Editor dialogs need the code editor activity" }

    abstract fun show()
}
