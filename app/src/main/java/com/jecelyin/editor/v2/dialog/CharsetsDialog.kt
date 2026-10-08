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

import android.content.Context
import com.jecelyin.editor.v2.common.Command
import com.riyadm.apkrepacker.R
import java.nio.charset.Charset

/**
 * Pick the encoding to reopen the current file with; the file's present encoding is preselected.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class CharsetsDialog(context: Context) : AbstractDialog(context) {
    private val names: Array<String> = Charset.availableCharsets().keys.toTypedArray()

    override fun show() {
        val current = getMainActivity().currentEditorDelegate?.encoding
        val checked = names.indexOfFirst { it.equals(current, ignoreCase = true) }
        getBuilder()
            .setTitle(R.string.reopen_with_encoding)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val command = Command(Command.CommandEnum.RELOAD_WITH_ENCODING)
                command.`object` = names[which]
                getMainActivity().doCommand(command)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
