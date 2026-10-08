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
package com.jecelyin.editor.v2.dialog

import android.content.Context
import com.jecelyin.editor.v2.common.Command
import com.riyadm.apkrepacker.R

/**
 * Pick the language used to highlight the current file.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class LangListDialog(context: Context) : AbstractDialog(context) {
    private val langList: Array<String> =
        arrayOf("C++", "Java", "Smali", "Html", "Json", "Xml", "Css", "None")
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toTypedArray()

    override fun show() {
        val current = getMainActivity().currentLang
        getBuilder()
            .setTitle(R.string.select_lang_to_highlight)
            .setSingleChoiceItems(langList, langList.indexOf(current.orEmpty())) { dialog, which ->
                val command = Command(Command.CommandEnum.HIGHLIGHT)
                command.`object` = langList[which]
                getMainActivity().doCommand(command)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
