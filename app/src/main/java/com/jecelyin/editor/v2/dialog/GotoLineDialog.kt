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
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.jecelyin.editor.v2.common.Command
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogInputBinding
import com.riyadm.apkrepacker.ide.editor.EditorDelegate

/**
 * Asks for a line number and sends a GOTO_INDEX command to the current editor.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class GotoLineDialog(context: Context, @Suppress("unused") private val editorDelegate: EditorDelegate?) : AbstractDialog(context) {

    override fun show() {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(context))
        binding.hint.hint = context.getString(R.string.m3f_line_hint)

        val dialog = getBuilder()
            .setTitle(R.string.goto_line)
            .setView(binding.root)
            .setPositiveButton(R.string.m3f_goto, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun confirm() {
            val line = binding.editInput.text?.toString()?.trim()?.toIntOrNull()
            if (line == null) {
                binding.hint.error = context.getString(R.string.m3f_invalid_line)
                return
            }
            val command = Command(Command.CommandEnum.GOTO_INDEX)
            command.args.putInt("line", line)
            getMainActivity().doCommand(command)
            dialog.dismiss()
        }

        binding.editInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) confirm() }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { confirm() }
            binding.editInput.requestFocus()
        }
        dialog.show()
    }
}
