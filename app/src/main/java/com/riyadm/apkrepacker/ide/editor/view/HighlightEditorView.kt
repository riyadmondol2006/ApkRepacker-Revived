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

package com.riyadm.apkrepacker.ide.editor.view

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Parcelable
import android.util.AttributeSet

import com.jecelyin.editor.v2.EditorPreferences
import com.riyadm.apkrepacker.utils.ViewUtils
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.codeeditor.util.Document
import com.riyadm.codeeditor.util.DocumentProvider
import com.riyadm.codeeditor.view.FreeScrollingTextField
import com.riyadm.codeeditor.view.YoyoNavigationMethod

abstract class HighlightEditorView : FreeScrollingTextField, IEditAreaView,
    SharedPreferences.OnSharedPreferenceChangeListener {
    protected var mEditorPreferences: EditorPreferences? = null
    private val _inputtingDoc: Document? = null
    private var _isWordWrap = false
    private var mContext: Context? = null
    private var editable = true
    private val _lastSelectFile: String? = null
    private var _index = 0
    private var listener: OnEditStateChangedListener? = null
    private val mIsAutoIndent = true
    private var mIsAutoPair = false
    internal var view: HighlightEditorView? = null

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun init(context: Context) {
        view = this
        mContext = context
        if (isInEditMode) {
            return
        }
        //avoid crash with large data
        setSaveEnabled(false)

        //setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        //  setInputType(InputType.TYPE_CLASS_TEXT
        //     | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        //       | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        //    | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE);


        mEditorPreferences = EditorPreferences.getInstance(getContext())
        mEditorPreferences!!.registerOnSharedPreferenceChangeListener(this)

        //  TextPaint gutterForegroundPaint = new TextPaint(getPaint());
        // gutterForegroundPaint.setTextSize(getTextSize() * LayoutContext.LINE_NUMBER_FACTOR);


        //  setVerticalScrollBarEnabled(true);
        setTypeface(Typeface.MONOSPACE)
        //  DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        //设置字体大小
        //float size = TypedValue.applyDimension(2, BASE_TEXT_SIZE_PIXELS, dm);
        // setTextSize((int) ViewUtils.dpToPx(BASE_TEXT_SIZE_PIXELS,mContext));
        //setShowLineNumbers(true);
        //setAutoCompete(true);
        setHighlightCurrentRow(true)
        //setWordWrap(true);
        setAutoComplete(false)
        // setAutoIndent(true);
        setUseGboard(true)
        setAutoIndentWidth(2)
        // setLexTask(new SmaliLexTask());
        setNavigationMethod(YoyoNavigationMethod(this))
        // int textColor = Color.BLACK;// 默认文字颜色
        //  int selectionText = Color.argb(255, 0, 120, 215);//选择文字颜色
        //setTextColor(textColor);
        // setTextHighlightColor(selectionText);
        //  setTextSize(getTextSize());


        onSharedPreferenceChanged(null, EditorPreferences.KEY_FONT_SIZE)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_SHOW_LINE_NUMBER)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_WORD_WRAP)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_SHOW_WHITESPACE)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_TAB_SIZE)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_AUTO_INDENT)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_AUTO_PAIR)
        onSharedPreferenceChanged(null, EditorPreferences.KEY_AUTO_CAPITALIZE)

        //setDefaultFilters();
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // TODO: Implement this method
        super.onLayout(changed, left, top, right, bottom)
        if (_index != 0 && right > 0) {
            moveCaret(_index)
            _index = 0
        }
    }

    /*
    private void setDefaultFilters() {
        //indent filters
        final InputFilter indentFilter = (source, start, end, dest, dstart, dend) -> {
            if (mIsAutoIndent) {
                if (!(source.length() == 1 && source.charAt(0) == '\n')) {
                    return null;
                }
                int startIndex = dstart - 1;
                if (startIndex < 0 || startIndex >= dest.length())
                    return null;

                char ch;
                for (; startIndex >= 0; startIndex--) {
                    ch = dest.charAt(startIndex);
                    if (ch != '\r')
                        break;
                }

                StringBuilder indent = new StringBuilder();
                for (int i = startIndex; i >= 0; i--) {
                    ch = dest.charAt(i);
                    if (ch == '\n' || ch == '\r') {
                        break;
                    } else if (ch == ' ' || ch == '\t') {
                        indent.append(ch);
                    } else {
                        indent.setLength(0);
                    }
                }
                indent.reverse();

                //bad code
                //common support java,c and c++
                // TODO: 08-Jun-18 dynamic change
                if (dend < dest.length() && dest.charAt(dend) == '}'
                        && dstart - 1 >= 0 && dest.charAt(dstart - 1) == '{') {
                    int mstart = dstart - 2;
                    while (mstart >= 0 && dest.charAt(mstart) != '\n') {
                        mstart--;
                    }
                    String closeIndent = "";
                    if (mstart >= 0) {
                        mstart++;
                        int zstart = mstart;
                        while (zstart < dest.length() && dest.charAt(zstart) == ' ') {
                            zstart++;
                        }
                        closeIndent = dest.toString().substring(mstart, zstart);
                    }
                    return source +
                            (indent.toString() + "  ") +
                            CURSOR + "\n" + closeIndent;
                }

                return "\n" + indent.toString();
            }
            return null;
        };

        //end line filter, only support \n
        InputFilter newLineFilter = (source, start, end, dest, dstart, dend) -> {
            final String s = source.toString();
            if (s.contains("\r")) {
                return s.replace("\r", "");
            }
            return null;
        };

        //bracket filter, auto add close bracket if auto pair is enable
        final InputFilter bracketFilter = (source, start, end, dest, dstart, dend) -> {
            if (mIsAutoPair) {
                if (end - start == 1 && start < source.length() && dstart < dest.length()) {
                    char c = source.charAt(start);
                    if (c == '(' || c == '{' || c == '[' || c == '"' || c == '\'') {
                        return addBracket(source, start);
                    }
                }
            }
            return null;
        };

        //setFilters(new InputFilter[]{indentFilter, newLineFilter, bracketFilter});

        //auto add bracket
        addTextChangedListener(new TextWatcher() {
            private int start;
            private int count;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                this.start = start;
                this.count = count;
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > start && count > 1) {
                    for (int i = start; i < start + count; i++) {
                        if (editable.charAt(i) == CURSOR) {
                            editable.delete(i, i + 1);
                            setSelection(start);
                            break;
                        }
                    }
                }
            }
        });
    }

     */
    private fun addBracket(source: CharSequence, start: Int): CharSequence? {
        when (source[start]) {
            '"' -> return "\"" + CURSOR + "\""
            '\'' -> return "'" + CURSOR + "'"
            '(' -> return "(" + CURSOR + ")"
            '{' -> return "{" + CURSOR + "}"
            '[' -> return "[" + CURSOR + "]"
        }
        return null
    }

    override fun setWordWrap(enable: Boolean) {
        // TODO: Implement this method
        _isWordWrap = enable
        super.setWordWrap(enable)
    }

    override val selectedText: CharSequence?
        get() {
            // TODO: Implement this method
            return hDoc.subSequence(getSelectionStart(), getSelectionEnd() - getSelectionStart()).toString()
        }

    override fun gotoLine(line: Int) {
        var line = line
        DLog.i("goto line called")
        if (line > hDoc.rowCount) {
            line = hDoc.rowCount

        }
        val i = text.getLineOffset(line - 1)
        setSelection(i)
    }

    fun setSelection(index: Int) {
        selectText(false)
        if (!hasLayout())
            moveCaret(index)
        else
            _index = index
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            EditorPreferences.KEY_FONT_SIZE -> setTextSize(ViewUtils.dpToPx(mEditorPreferences!!.fontSize.toFloat(), mContext!!).toInt())
            EditorPreferences.KEY_SHOW_LINE_NUMBER -> setShowLineNumbers(mEditorPreferences!!.isShowLineNumber)
            EditorPreferences.KEY_WORD_WRAP -> setWordWrap(mEditorPreferences!!.isWordWrap)
            EditorPreferences.KEY_SHOW_WHITESPACE -> setNonPrintingCharVisibility(mEditorPreferences!!.isShowWhiteSpace)
            EditorPreferences.KEY_TAB_SIZE -> {
                //updateTabChar();
            }
            EditorPreferences.KEY_AUTO_INDENT -> setAutoIndent(mEditorPreferences!!.isAutoIndent)
            EditorPreferences.KEY_AUTO_PAIR -> mIsAutoPair = mEditorPreferences!!.isAutoPair
            EditorPreferences.KEY_AUTO_CAPITALIZE -> if (!mEditorPreferences!!.isAutoCapitalize) {
                //   setInputType(getInputType() & ~EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
            } else {
                //    setInputType(getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
            }
        }
    }

    override val text: DocumentProvider
        get() = createDocumentProvider()

    override fun setText(spannable: CharSequence?) {
        val doc = Document(this)
        doc.setWordWrap(_isWordWrap)
        doc.setText(spannable)
        setDocumentProvider(DocumentProvider(doc))
    }

    var isEditable: Boolean
        get() = editable
        set(editable) {
            this.editable = editable
            super.showIME(editable)
        }

    override val isChanged: Boolean
        get() = isEdited()

    override fun setReadOnly(readOnly: Boolean) {
        isEditable = !readOnly
    }

    override fun showIME(show: Boolean) {
        var show = show
        if (!editable)
            show = false
        super.showIME(show)
        parent.requestLayout()
    }

    public override fun onRestoreInstanceState(editorState: Parcelable?) {
        super.onRestoreInstanceState(editorState)
    }

    override fun setEdited(set: Boolean) {
        super.setEdited(set)
        if (listener != null)
            listener!!.onEditStateChanged()
    }

    override fun setOnEditStateChangedListener(listener: OnEditStateChangedListener?) {
        this.listener = listener
    }

    fun interface OnEditStateChangedListener {
        fun onEditStateChanged()
    }

    override fun hasSelection(): Boolean {
        return mFieldController.isSelectText()
    }

    override fun gotoTop() {
        gotoLine(1)
    }

    override fun gotoEnd() {
        gotoLine(getLineNum())
    }

    override fun length(): Int {
        return hDoc.length
    }

    override fun setFreezesText(b: Boolean) {

    }

    public override fun onSaveInstanceState(): Parcelable? {
        return super.onSaveInstanceState()
        // return null;
    }

    override fun undo() {
        val doc = createDocumentProvider()
        val newPosition = doc.undo()

        if (newPosition >= 0) {
            //TODO editor.setEdited(false);
            // if reached original condition of file
            setEdited(true)
            respan()
            selectText(false)
            moveCaret(newPosition)
            invalidate()
        }

    }

    override fun redo() {
        val doc = createDocumentProvider()
        val newPosition = doc.redo()

        if (newPosition >= 0) {
            setEdited(true)

            respan()
            selectText(false)
            moveCaret(newPosition)
            invalidate()
        }
    }

    override fun doCut(): Boolean {
        return false
    }

    override fun doCopy(): Boolean {
        return false
    }

    override fun doPaste(): Boolean {
        return false
    }


    override fun doCanUndo(): Boolean {
        val doc = createDocumentProvider()
        return doc.canUndo()
    }

    override fun doCanRedo(): Boolean {
        val doc = createDocumentProvider()
        return doc.canRedo()
    }

    override fun insert(text: CharSequence) {
        selectText(false)
        // moveCaret(_index);
        paste(text.toString())
    }

    override val editorView: HighlightEditorView?
        get() = view

    override val lang: String?
        get() = getLexTask().getLanguageType()

    companion object {
        const val CURSOR = '☢'
        private const val TAG = "EditAreaView2"
    }
}
