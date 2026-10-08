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
package com.jecelyin.editor.v2

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.text.TextUtils
import androidx.annotation.IntDef
import androidx.preference.PreferenceManager
import com.jecelyin.common.utils.DLog
import com.jecelyin.common.utils.StringUtils
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ide.editor.theme.ThemeLoader
import com.riyadm.apkrepacker.ide.editor.theme.model.EditorTheme
import java.util.WeakHashMap

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class EditorPreferences(private val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences: SharedPreferences

    private val map: MutableMap<String, Any?>
    private val mListeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()
    private val toolbarIconSet: Set<String>?

    init {
        PreferenceManager.setDefaultValues(context, R.xml.preference_editor, false)
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)

        map = HashMap()
        map[KEY_FONT_SIZE] = 13
        map[KEY_TOUCH_TO_ADJUST_TEXT_SIZE] = true
        map[KEY_WORD_WRAP] = true
        map[KEY_SHOW_LINE_NUMBER] = true
        map[KEY_SHOW_WHITESPACE] = true

        map[context.getString(R.string.pref_auto_complete)] = true
        map[KEY_AUTO_INDENT] = true
        map[KEY_AUTO_PAIR] = true
        map[context.getString(R.string.pref_auto_save)] = true

        map[context.getString(R.string.pref_insert_space_for_tab)] = true
        map[KEY_TAB_SIZE] = 4
        map[KEY_SYMBOL] = VALUE_SYMBOL
        map[KEY_AUTO_CAPITALIZE] = false
        map[context.getString(R.string.pref_volume_move)] = true

        map[KEY_ENABLE_HIGHLIGHT] = true
        map[KEY_HIGHLIGHT_FILE_SIZE_LIMIT] = 500
        map[KEY_REMEMBER_LAST_OPENED_FILES] = true

        map[KEY_SCREEN_ORIENTATION] = "auto"
        map[KEY_KEEP_SCREEN_ON] = false
        map[KEY_CONFIRM_EXIT] = true

        toolbarIconSet = preferences.getStringSet(KEY_TOOLBAR_ICONS, null)
        map[KEY_LAST_OPEN_PATH] = Environment.getExternalStorageDirectory().path
        map[KEY_READ_ONLY] = false
        map[KEY_SHOW_HIDDEN_FILES] = false
        map[KEY_FILE_SORT_TYPE] = 0
        map[KEY_FULL_SCREEN] = false
        map[KEY_LAST_TAB] = 0
        map[KEY_HIDE_SYMBOL_PANEL] = false
        map[KEY_USE_REGEX] = false
        map[KEY_WHOLE_WORDS_ONLY] = false
        map[KEY_MATCH_CASE] = false

        val values = preferences.all
        for (key in map.keys) {
            updateValue(key, values)
        }
    }

    val maxEditor: Int
        get() = 3

    private fun updateValue(key: String?, values: Map<String, *>) {
        if (key == null)
            return
        var value = map[key] ?: return
        val cls: Class<*> = value.javaClass

        try {
            if (cls == Int::class.javaPrimitiveType || cls == Int::class.javaObjectType) {
                val `in` = values[key]
                if (`in` != null)
                    value = if (`in` is Int) `in` else StringUtils.toInt(`in`.toString())
            } else if (cls == Boolean::class.javaPrimitiveType || cls == Boolean::class.javaObjectType) {
                val b = values[key] as Boolean?
                value = b ?: value as Boolean
            } else {
                val str = values[key] as String?
                value = str ?: value as String
            }
        } catch (e: Exception) {
            DLog.e("key = $key", e)
            return
        }
        map[key] = value
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) {
            mListeners.put(listener, mContent)
        }
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        synchronized(this) {
            mListeners.remove(listener)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        updateValue(key, sharedPreferences.all)
        val listeners = mListeners.keys
        for (listener in listeners) {
            listener?.onSharedPreferenceChanged(sharedPreferences, key)
        }
    }

    val isShowLineNumber: Boolean
        get() = map[KEY_SHOW_LINE_NUMBER] as Boolean

    val isShowWhiteSpace: Boolean
        get() = map[KEY_SHOW_WHITESPACE] as Boolean

    val isHighlight: Boolean
        get() = map[KEY_ENABLE_HIGHLIGHT] as Boolean

    val highlightSizeLimit: Int
        get() = 1024 * map[KEY_HIGHLIGHT_FILE_SIZE_LIMIT] as Int

    //auto save is default
    val isAutoSave: Boolean
        get() = map[context.getString(R.string.pref_auto_save)] as Boolean

    fun getBoolean(key: String?, def: Boolean): Boolean {
        return try {
            preferences.getBoolean(key, def)
        } catch (e: ClassCastException) {
            java.lang.Boolean.parseBoolean(preferences.getString(key, def.toString()))
        }
    }

    private fun getInt(key: String?, def: Int): Int {
        return try {
            preferences.getInt(key, def)
        } catch (e: ClassCastException) {
            Integer.parseInt(preferences.getString(key, def.toString())!!)
        }
    }

    private fun getString(key: String?, def: String?): String? {
        return try {
            preferences.getString(key, def)
        } catch (e: ClassCastException) {
            def
        }
    }

    val isKeepScreenOn: Boolean
        get() = map[KEY_KEEP_SCREEN_ON] as Boolean

    val toolbarIcons: Array<Int>?
        get() {
            if (toolbarIconSet == null)
                return null
            val list = arrayOfNulls<Int>(toolbarIconSet.size)
            var i = 0
            for (id in toolbarIconSet) {
                list[i++] = Integer.valueOf(id)
            }
            @Suppress("UNCHECKED_CAST")
            return list as Array<Int>
        }

    fun getValue(key: String?): Any? {
        return map[key]
    }

    var lastOpenPath: String?
        get() = map[KEY_LAST_OPEN_PATH] as String?
        set(path) {
            preferences.edit().putString(KEY_LAST_OPEN_PATH, path).apply()
            map[KEY_LAST_OPEN_PATH] = path
        }

    val fontSize: Int
        get() = map[KEY_FONT_SIZE] as Int

    var isReadOnly: Boolean
        get() = map[KEY_READ_ONLY] as Boolean
        //   return false;
        set(b) {
            preferences.edit().putBoolean(KEY_READ_ONLY, b).apply()
            map[KEY_READ_ONLY] = b
        }

    var isHidePanel: Boolean
        get() = map[KEY_HIDE_SYMBOL_PANEL] as Boolean
        set(b) {
            preferences.edit().putBoolean(KEY_HIDE_SYMBOL_PANEL, b).apply()
            map[KEY_HIDE_SYMBOL_PANEL] = b
        }

    val isAutoIndent: Boolean
        get() = map[KEY_AUTO_INDENT] as Boolean

    val isAutoPair: Boolean
        get() = map[KEY_AUTO_PAIR] as Boolean

    val isWordWrap: Boolean
        get() = map[KEY_WORD_WRAP] as Boolean

    val isTouchScaleTextSize: Boolean
        get() = map[KEY_TOUCH_TO_ADJUST_TEXT_SIZE] as Boolean

    val isAutoCapitalize: Boolean
        get() = map[KEY_AUTO_CAPITALIZE] as Boolean

    val isOpenLastFiles: Boolean
        get() = getBoolean(KEY_REMEMBER_LAST_OPENED_FILES, true)

    val tabSize: Int
        get() = map[KEY_TAB_SIZE] as Int

    @get:ScreenOrientation
    val screenOrientation: Int
        get() {
            val ori = map[KEY_SCREEN_ORIENTATION] as String?
            return if ("landscape" == ori) {
                SCREEN_ORIENTATION_LANDSCAPE
            } else if ("portrait" == ori) {
                SCREEN_ORIENTATION_PORTRAIT
            } else {
                SCREEN_ORIENTATION_AUTO
            }
        }

    val symbol: String?
        get() = map[KEY_SYMBOL] as String?

    var isShowHiddenFiles: Boolean
        get() = map[KEY_SHOW_HIDDEN_FILES] as Boolean
        set(b) {
            preferences.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, b).apply()
            map[KEY_SHOW_HIDDEN_FILES] = b
        }

    var fileSortType: Int
        get() = map[KEY_FILE_SORT_TYPE] as Int
        set(type) {
            preferences.edit().putInt(KEY_FILE_SORT_TYPE, type).apply()
            map[KEY_FILE_SORT_TYPE] = type
        }

    var isFullScreenMode: Boolean
        get() = map[KEY_FULL_SCREEN] as Boolean
        set(b) {
            preferences.edit().putBoolean(KEY_FULL_SCREEN, b).apply()
            map[KEY_FULL_SCREEN] = b
        }

    var isRegexMode: Boolean
        get() = map[KEY_USE_REGEX] as Boolean
        set(b) {
            preferences.edit().putBoolean(KEY_USE_REGEX, b).apply()
            map[KEY_USE_REGEX] = b
        }

    var isMatchCaseMode: Boolean
        get() = map[KEY_MATCH_CASE] as Boolean
        set(b) {
            preferences.edit().putBoolean(KEY_MATCH_CASE, b).apply()
            map[KEY_MATCH_CASE] = b
        }

    var isWholeWordsOnlyMode: Boolean
        get() = map[KEY_WHOLE_WORDS_ONLY] as Boolean
        set(b) {
            preferences.edit().putBoolean(KEY_WHOLE_WORDS_ONLY, b).apply()
            map[KEY_WHOLE_WORDS_ONLY] = b
        }

    var lastTab: Int
        get() = map[KEY_LAST_TAB] as Int
        set(index) {
            preferences.edit().putInt(KEY_LAST_TAB, index).apply()
            map[KEY_LAST_TAB] = index
        }

    val isConfirmExit: Boolean
        get() = map[KEY_CONFIRM_EXIT] as Boolean

    /**
     * Theme and fonts
     */
    val editorTheme: EditorTheme
        get() {
            val fileName = getString(context.getString(R.string.pref_theme_editor_theme), "")
            val theme = ThemeLoader.getTheme(context, fileName)
            if (theme != null) {
                return theme
            }
            return ThemeLoader.loadDefault(context)
        }

    fun setEditorTheme(fileName: String?) {
        preferences.edit().putString(context.getString(R.string.pref_theme_editor_theme), fileName).apply()
    }

    fun setTerminalTheme(index: Int) {
        preferences.edit().putString("pref_terminal_color", index.toString()).apply()
    }

    val isUseVolumeToMove: Boolean
        get() = preferences.getBoolean(context.getString(R.string.pref_volume_move), true)

    val isUseAutoComplete: Boolean
        get() = getBoolean(context.getString(R.string.pref_auto_complete), true)

    val isInsertSpaceForTab: Boolean
        get() = getBoolean(context.getString(R.string.pref_insert_space_for_tab), true)

    @IntDef(SCREEN_ORIENTATION_AUTO, SCREEN_ORIENTATION_LANDSCAPE, SCREEN_ORIENTATION_PORTRAIT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ScreenOrientation

    companion object {
        const val KEY_FONT_SIZE = "pref_font_size"
        const val KEY_WORD_WRAP = "pref_word_wrap"
        const val KEY_ENABLE_HIGHLIGHT = "pref_enable_highlight"
        const val KEY_SHOW_LINE_NUMBER = "pref_show_linenumber"
        const val KEY_SHOW_WHITESPACE = "pref_show_whitespace"
        const val KEY_AUTO_INDENT = "pref_auto_indent"
        const val KEY_TAB_SIZE = "pref_tab_size"
        const val KEY_SYMBOL = "pref_symbol"
        const val KEY_AUTO_CAPITALIZE = "pref_auto_capitalize"
        const val KEY_SCREEN_ORIENTATION = "pref_screen_orientation"
        const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        const val KEY_READ_ONLY = "readonly_mode"
        const val KEY_HIDE_SYMBOL_PANEL = "pref_hide_symbol_panel"
        const val KEY_CONFIRM_EXIT = "pref_confirm_exit"

        const val DEF_MIN_FONT_SIZE = 6
        const val DEF_MAX_FONT_SIZE = 32

        const val SCREEN_ORIENTATION_AUTO = 0
        const val SCREEN_ORIENTATION_LANDSCAPE = 1
        const val SCREEN_ORIENTATION_PORTRAIT = 2

        @JvmField
        val VALUE_SYMBOL: String = TextUtils.join(
            "\n", arrayOf(
                "{", "}", "<", ">", ",", ";", "'", "\"", "(", ")", "/", "\\", "%", "[", "]", "|", "#", "=", "$", ":",
                "&", "?", "!", "@", "^", "+", "*", "-", "_", "`", "\\t", "\\n"
            )
        )
        const val KEY_AUTO_PAIR = "pref_auto_pair"
        private const val KEY_TOUCH_TO_ADJUST_TEXT_SIZE = "pref_touch_to_adjust_text_size"
        private const val KEY_HIGHLIGHT_FILE_SIZE_LIMIT = "pref_highlight_file_size_limit"
        private const val KEY_REMEMBER_LAST_OPENED_FILES = "pref_remember_last_opened_files"
        private const val KEY_TOOLBAR_ICONS = "pref_toolbar_icons"
        private const val KEY_LAST_OPEN_PATH = "last_open_path"
        private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        private const val KEY_FILE_SORT_TYPE = "show_file_sort"
        private const val KEY_FULL_SCREEN = "fullscreen_mode"
        private const val KEY_LAST_TAB = "last_tab"

        //Search panel checkbox
        const val KEY_USE_REGEX = "pref_use_regex"
        const val KEY_WHOLE_WORDS_ONLY = "pref_whole_words_only"
        const val KEY_MATCH_CASE = "pref_match_case"
        private val mContent = Any()

        // private static final int[] THEMES = new int[]{R.style.LightTheme, R.style.DarkTheme};
        private var instance: EditorPreferences? = null

        @JvmStatic
        fun getInstance(context: Context): EditorPreferences {
            if (instance == null) {
                instance = EditorPreferences(context.applicationContext)
            }
            return instance!!
        }
    }
}
