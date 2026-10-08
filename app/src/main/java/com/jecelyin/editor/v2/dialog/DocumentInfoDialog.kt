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
import android.view.LayoutInflater
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogDocumentInfoBinding
import com.riyadm.apkrepacker.ide.editor.Document
import com.riyadm.apkrepacker.ide.editor.view.IEditAreaView

/**
 * Path, encoding, line / word / character counts of the open document.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class DocumentInfoDialog(context: Context) : AbstractDialog(context) {
    private var path: CharSequence? = null
    private var editAreaView: IEditAreaView? = null
    private var document: Document? = null

    fun setPath(path: CharSequence?) {
        this.path = path
    }

    fun setEditAreaView(editAreaView: IEditAreaView?) {
        this.editAreaView = editAreaView
    }

    fun setDocument(document: Document?) {
        this.document = document
    }

    override fun show() {
        val text = editAreaView?.text ?: return
        val document = document ?: return
        val wordCount = WORD.findAll(text).count()

        val binding = DialogDocumentInfoBinding.inflate(LayoutInflater.from(context))
        binding.pathTextView.text = context.getString(R.string.path_x, (path ?: ""))
        binding.encodingTextView.text = context.getString(R.string.encoding_x, document.encoding)
        binding.lineCountTextView.text = context.getString(R.string.line_number_x, document.lineCount)
        binding.wordCountTextView.text = context.getString(R.string.word_x, wordCount)
        binding.charCountTextView.text = context.getString(R.string.char_x, text.length)

        getBuilder()
            .setTitle(R.string.document_info)
            .setIcon(R.drawable.ic_info)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private companion object {
        val WORD = Regex("[a-zA-Z]+")
    }
}
