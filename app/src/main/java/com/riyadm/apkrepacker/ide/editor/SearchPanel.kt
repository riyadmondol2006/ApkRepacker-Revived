package com.riyadm.apkrepacker.ide.editor

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import com.jecelyin.editor.v2.EditorPreferences
import com.jecelyin.editor.v2.widget.EditorMessages
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.TextEditorActivity
import com.riyadm.apkrepacker.database.ITabDatabase
import com.riyadm.apkrepacker.database.JsonDatabase
import com.riyadm.apkrepacker.databinding.EditorFindPanelBinding
import com.riyadm.apkrepacker.ui.autocompleteeidttext.CustomAdapter
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.codeeditor.util.FindThread
import com.riyadm.codeeditor.util.ProgressObserver
import com.riyadm.codeeditor.util.ProgressSource
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * The find / replace bar of the code editor. Plain searches run on the editor's [FindThread];
 * with the regex option on, matching is done here with [Pattern].
 */
class SearchPanel(private val mContext: Context) : ProgressObserver {
    private var mActivity: TextEditorActivity? = null
    private var binding: EditorFindPanelBinding? = null
    private var wired = false
    private var syncingChips = false

    private var mFindText: String? = null
    private var mReplaceText: String? = null
    private var mCaseSensitive = false
    private var mWholeWordsOnly = false
    private var mRegex = false
    private var mReplaceMode = false

    private val mEditorPreferences: EditorPreferences = EditorPreferences.getInstance(mContext)
    private val mDatabase: ITabDatabase = JsonDatabase.getInstance(mContext)
    private var mTaskFind: FindThread? = null
    internal var replaceAdapter: CustomAdapter? = null
    internal var searchAdapter: CustomAdapter? = null

    val isShowing: Boolean
        get() = binding?.root?.visibility == android.view.View.VISIBLE

    fun setActivity(activity: TextEditorActivity?) {
        mActivity = activity
    }

    /** Shows the bar (prefilled with the selected text, if any) and focuses the search field. */
    fun initSearchPanel(editorDelegate: EditorDelegate) {
        val activity = mActivity ?: return
        val b = activity.findPanelBinding
        binding = b
        if (!wired) {
            wire(b)
            wired = true
        }

        mRegex = mEditorPreferences.isRegexMode
        mCaseSensitive = mEditorPreferences.isMatchCaseMode
        mWholeWordsOnly = mEditorPreferences.isWholeWordsOnlyMode
        syncingChips = true
        b.chipRegex.isChecked = mRegex
        b.chipMatchCase.isChecked = mCaseSensitive
        b.chipWholeWord.isChecked = mWholeWordsOnly
        b.chipReplace.isChecked = mReplaceMode
        syncingChips = false
        b.replaceRow.visibility = if (mReplaceMode) android.view.View.VISIBLE else android.view.View.GONE

        searchAdapter = CustomAdapter(mContext, mDatabase.getFindKeywords(false))
        replaceAdapter = CustomAdapter(mContext, mDatabase.getFindKeywords(true))
        b.searchText.setAdapter(searchAdapter)
        b.replaceText.setAdapter(replaceAdapter)

        val selected = runCatching { editorDelegate.editText.selectedText?.toString() }.getOrNull()
        if (!selected.isNullOrEmpty() && '\n' !in selected && selected.length <= MAX_PREFILL) {
            mFindText = selected
        }
        b.searchText.setText(mFindText)
        b.searchText.setSelection(b.searchText.length())
        b.replaceText.setText(mReplaceText.orEmpty())

        if (!isShowing) b.root.springIn(fromScale = 0.96f, fromTranslationY = 24f * mContext.resources.displayMetrics.density)
        b.searchText.requestFocus()
        WindowInsetsControllerCompat(activity.window, b.searchText).show(WindowInsetsCompat.Type.ime())
    }

    fun hide() {
        val b = binding ?: return
        val activity = mActivity
        if (activity != null) {
            WindowInsetsControllerCompat(activity.window, b.searchText).hide(WindowInsetsCompat.Type.ime())
        }
        b.root.springOut { b.root.visibility = android.view.View.GONE }
        activity?.currentEditorDelegate?.editText?.requestFocus()
    }

    private fun wire(b: EditorFindPanelBinding) {
        b.searchNextResult.setOnClickListener { findNext() }
        b.searchPrevResult.setOnClickListener { findPrevious() }
        b.searchClose.setOnClickListener { hide() }
        b.searchReplaceOption.setOnClickListener { replaceCurrent() }
        b.allReplaceOption.setOnClickListener { replaceEverywhere() }

        b.searchText.doAfterTextChanged { text ->
            mFindText = text?.toString()
            b.searchTextLayout.error = null
        }
        b.replaceText.doAfterTextChanged { text -> mReplaceText = text?.toString() }
        b.searchText.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_SEARCH).also { if (it) findNext() }
        }

        b.chipReplace.setOnCheckedChangeListener { _, checked ->
            if (syncingChips) return@setOnCheckedChangeListener
            mReplaceMode = checked
            b.replaceRow.visibility = if (checked) android.view.View.VISIBLE else android.view.View.GONE
            if (checked) b.replaceText.requestFocus()
        }
        b.chipMatchCase.setOnCheckedChangeListener { _, checked ->
            if (syncingChips) return@setOnCheckedChangeListener
            mCaseSensitive = checked
            mEditorPreferences.isMatchCaseMode = checked
        }
        b.chipWholeWord.setOnCheckedChangeListener { _, checked ->
            if (syncingChips) return@setOnCheckedChangeListener
            mWholeWordsOnly = checked
            mEditorPreferences.isWholeWordsOnlyMode = checked
        }
        b.chipRegex.setOnCheckedChangeListener { _, checked ->
            if (syncingChips) return@setOnCheckedChangeListener
            mRegex = checked
            mEditorPreferences.isRegexMode = checked
            if (checked) EditorMessages.show(mContext, R.string.use_regex_to_find_tip, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        }
    }

    private val delegate: EditorDelegate?
        get() = mActivity?.currentEditorDelegate

    private fun requireFindText(): String? {
        val text = mFindText
        if (text.isNullOrEmpty()) {
            binding?.searchTextLayout?.error = mContext.getString(R.string.cannot_be_empty)
            return null
        }
        return text
    }

    private fun rememberQueries() {
        searchAdapter?.let {
            it.addValue(mFindText.orEmpty())
            mDatabase.addFindKeyword(it.dataList, false)
        }
        if (mReplaceMode && !mReplaceText.isNullOrEmpty()) {
            replaceAdapter?.let {
                it.addValue(mReplaceText.orEmpty())
                mDatabase.addFindKeyword(it.dataList, true)
            }
        }
    }

    private fun findNext() {
        val what = requireFindText() ?: return
        val delegate = delegate ?: return
        rememberQueries()
        if (mRegex) regexFind(delegate, forward = true) else find(what, delegate, mCaseSensitive, mWholeWordsOnly)
    }

    private fun findPrevious() {
        val what = requireFindText() ?: return
        val delegate = delegate ?: return
        if (mRegex) regexFind(delegate, forward = false) else findBackwards(what, delegate, mCaseSensitive, mWholeWordsOnly)
    }

    /** Replaces the current selection with the replacement text, then moves to the next match. */
    private fun replaceCurrent() {
        val delegate = delegate ?: return
        val view = delegate.editText.editorView ?: return
        val replacement = mReplaceText.orEmpty()
        if (view.isSelectText) {
            val replaced = if (mRegex) regexReplacement(view.selectedText?.toString().orEmpty(), replacement) else replacement
            if (replaced != null) {
                replaceSelection(replaced, delegate)
                findNext()
            }
        } else {
            findNext()
        }
    }

    private fun replaceEverywhere() {
        val what = requireFindText() ?: return
        val delegate = delegate ?: return
        rememberQueries()
        if (mRegex) regexReplaceAll(delegate, mReplaceText.orEmpty())
        else replaceAll(what, mReplaceText.orEmpty(), delegate, mCaseSensitive, mWholeWordsOnly)
    }

    // ---- regex mode -------------------------------------------------------------------------

    private fun buildPattern(): Pattern? {
        val raw = mFindText.orEmpty()
        if (raw.isEmpty()) return null
        var source = if (mRegex) raw else Pattern.quote(raw)
        if (mWholeWordsOnly) source = "\\b(?:$source)\\b"
        val flags = if (mCaseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return try {
            Pattern.compile(source, flags)
        } catch (e: PatternSyntaxException) {
            binding?.searchTextLayout?.error = e.description
            null
        }
    }

    private fun regexFind(delegate: EditorDelegate, forward: Boolean) {
        val view = delegate.editText.editorView ?: return
        val pattern = buildPattern() ?: return
        val matcher = pattern.matcher(view.text)
        val from = if (view.isSelectText) {
            if (forward) view.getSelectionEnd() else view.getSelectionStart()
        } else {
            view.caretPosition
        }
        var start = -1
        var end = -1
        if (forward) {
            val found = matcher.find(from.coerceIn(0, view.text.length)) || matcher.find(0)
            if (found) {
                start = matcher.start()
                end = matcher.end()
            }
        } else {
            while (matcher.find() && matcher.start() < from) {
                start = matcher.start()
                end = matcher.end()
            }
            if (start == -1) {
                matcher.reset()
                while (matcher.find()) {
                    start = matcher.start()
                    end = matcher.end()
                }
            }
        }
        if (start == -1) {
            EditorMessages.show(mContext, R.string.find_not_found)
        } else {
            view.setSelectionRange(start, end - start)
        }
    }

    /** Expands the regex groups ($0..$9) of the first match inside [selected]. */
    private fun regexReplacement(selected: String, replacement: String): String? {
        val pattern = buildPattern() ?: return null
        val matcher: Matcher = pattern.matcher(selected)
        return if (matcher.matches() || matcher.find()) {
            try {
                matcher.replaceFirst(unescape(replacement))
            } catch (e: RuntimeException) {
                EditorMessages.error(mContext, e.message)
                null
            }
        } else {
            replacement
        }
    }

    private fun regexReplaceAll(delegate: EditorDelegate, replacement: String) {
        val view = delegate.editText.editorView ?: return
        val pattern = buildPattern() ?: return
        val matcher = pattern.matcher(view.text)
        val expanded = unescape(replacement)
        val spans = ArrayList<Triple<Int, Int, String>>()
        try {
            var last = 0
            while (matcher.find()) {
                val sb = StringBuffer()
                matcher.appendReplacement(sb, expanded)
                spans += Triple(matcher.start(), matcher.end(), sb.substring(matcher.start() - last))
                last = matcher.end()
            }
        } catch (e: RuntimeException) {
            EditorMessages.error(mContext, e.message)
            return
        }
        if (spans.isEmpty()) {
            EditorMessages.show(mContext, mContext.resources.getQuantityString(R.plurals.x_text_replaced, 0, 0))
            return
        }
        // Apply from the end so the offsets of earlier matches stay valid.
        for ((start, end, text) in spans.asReversed()) {
            view.setSelectionRange(start, end - start)
            view.paste(text)
        }
        view.setEdited(true)
        EditorMessages.show(mContext, mContext.resources.getQuantityString(R.plurals.x_text_replaced, spans.size, spans.size))
    }

    private fun unescape(replacement: String): String =
        replacement.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")

    // ---- plain mode (editor find thread) ------------------------------------------------------

    /** Called from the find thread. */
    override fun onComplete(requestCode: Int, result: Any?) {
        mActivity?.runOnUiThread {
            val view = delegate?.editText?.editorView
            val results = result as? FindThread.FindResults
            if (view == null || results == null) return@runOnUiThread
            if (requestCode == ProgressSource.FIND || requestCode == ProgressSource.FIND_BACKWARDS) {
                if (results.foundOffset != -1) {
                    view.setSelectionRange(results.foundOffset, results.searchTextLength)
                } else {
                    EditorMessages.show(mContext, R.string.find_not_found)
                }
            } else if (requestCode == ProgressSource.REPLACE_ALL) {
                val count = results.replacementCount
                if (count > 0) {
                    view.setEdited(true)
                    view.selectText(false)
                    view.moveCaret(results.newStartPosition)
                    view.respan()
                    view.invalidate()
                }
                EditorMessages.show(mContext, mContext.resources.getQuantityString(R.plurals.x_text_replaced, count, count))
            }
            mTaskFind = null
        }
    }

    fun find(what: String, editorDelegate: EditorDelegate, isCaseSensitive: Boolean, isWholeWord: Boolean) {
        if (what.isEmpty()) return
        val view = editorDelegate.editText.editorView ?: return
        val startingPosition = (if (view.isSelectText) view.getSelectionStart() else view.caretPosition) + 1
        startFind(FindThread.createFindThread(
            view.createDocumentProvider(), what, startingPosition, true,
            isCaseSensitive, isWholeWord, view.getLexTask().getLanguage()
        ))
    }

    fun findBackwards(what: String, editorDelegate: EditorDelegate, isCaseSensitive: Boolean, isWholeWord: Boolean) {
        if (what.isEmpty()) return
        val view = editorDelegate.editText.editorView ?: return
        val startingPosition = (if (view.isSelectText) view.getSelectionStart() else view.caretPosition) - 1
        startFind(FindThread.createFindThread(
            view.createDocumentProvider(), what, startingPosition, false,
            isCaseSensitive, isWholeWord, view.getLexTask().getLanguage()
        ))
    }

    fun replaceSelection(replacementText: String?, editorDelegate: EditorDelegate) {
        val view = editorDelegate.editText.editorView ?: return
        if (view.isSelectText) view.paste(replacementText)
    }

    fun replaceAll(
        what: String, replacementText: String?, editorDelegate: EditorDelegate,
        isCaseSensitive: Boolean, isWholeWord: Boolean
    ) {
        if (what.isEmpty()) return
        val view = editorDelegate.editText.editorView ?: return
        startFind(FindThread.createReplaceAllThread(
            view.createDocumentProvider(), what, replacementText, view.caretPosition,
            isCaseSensitive, isWholeWord, view.getLexTask().getLanguage()
        ))
    }

    private fun startFind(thread: FindThread) {
        mTaskFind = thread
        thread.registerObserver(this)
        thread.start()
    }

    companion object {
        private const val MAX_PREFILL = 200

        @JvmStatic
        fun getInstance(context: Context): SearchPanel = SearchPanel(context)
    }
}
