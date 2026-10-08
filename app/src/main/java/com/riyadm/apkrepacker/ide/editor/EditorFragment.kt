/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.ide.editor

import android.os.Bundle
import androidx.core.os.BundleCompat
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import com.riyadm.apkrepacker.R

import com.riyadm.apkrepacker.ide.editor.view.CodeEditor

import java.io.File

/**
 * Created by Duy on 25-Apr-18.
 */

class EditorFragment : Fragment() {
    private var mEditorDelegate: EditorDelegate? = null

    /* public static EditorFragment newInstance(EditorPageDescriptor desc) {
         return newInstance(desc.getFile(), desc.getCursorOffset(), desc.getEncoding());
     }*/

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        onRestoreState(savedInstanceState)
        if (mEditorDelegate == null) {
            val arguments = requireArguments()
            val encoding = arguments.getString(KEY_ENCODING)
            val offset = arguments.getInt(KEY_OFFSET)
            val file = BundleCompat.getSerializable(arguments, KEY_FILE, File::class.java)
            mEditorDelegate = EditorDelegate(file, offset, encoding)
        }

        return inflater.inflate(R.layout.fragment_code_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // FastScrollScrollView fastScrollScrollView = view.findViewById(R.id.fast_scroll);
        val codeEditor = view.findViewById<CodeEditor>(R.id.edit_text)
        //  view.runOnUi(() -> {
        // new FastScrollerBuilder(fastScrollScrollView).useMd2Style().build();
        //   });
        // mEditorDelegate.onCreate(codeEditor);
        /*
        if (getActivity() instanceof IEditorStateListener) {
            IEditorStateListener listener = (IEditorStateListener) getActivity();
            listener.onEditorViewCreated(mEditorDelegate);
        }

         */
    }

    override fun onDestroyView() {
        if (mEditorDelegate != null) {
            mEditorDelegate!!.onDestroy()
        }
        /*
        if (getActivity() instanceof IEditorStateListener) {
            IEditorStateListener listener = (IEditorStateListener) getActivity();
            listener.onEditorViewDestroyed(mEditorDelegate);
        }

         */
        super.onDestroyView()
    }

    private fun onRestoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            return
        }
        val parcelable = BundleCompat.getParcelable(savedInstanceState, KEY_SAVE_STATE, EditorDelegate.SavedState::class.java)
        if (parcelable is EditorDelegate.SavedState) {
            mEditorDelegate = EditorDelegate(parcelable)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mEditorDelegate != null) {
            val value = mEditorDelegate!!.onSaveInstanceState()
            outState.putParcelable(KEY_SAVE_STATE, value)
        }
    }

    val editorDelegate: EditorDelegate?
        get() = mEditorDelegate

    companion object {
        private const val KEY_SAVE_STATE = "save_state"
        private const val KEY_FILE = "KEY_FILE"
        private const val KEY_OFFSET = "KEY_OFFSET"
        private const val KEY_ENCODING = "KEY_ENCODING"
        private const val TAG = "EditorFragment"

        @JvmStatic
        fun newInstance(file: File, offset: Int, encoding: String?): EditorFragment {
            val args = Bundle()
            args.putSerializable(KEY_FILE, file)
            args.putInt(KEY_OFFSET, offset)
            args.putString(KEY_ENCODING, encoding)
            val fragment = EditorFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
