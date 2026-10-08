package com.riyadm.apkrepacker.ui.preferences

object PreferenceKeys {
    const val KEY_SHOW_HIDDEN_FILES = "pref_show_hidden"
    /** No longer used: apktool 3 always uses the bundled aapt2 and its own framework folder. */
    const val KEY_USE_AAPT2 = "pref_use_aapt2"
    const val KEY_SIGN_OUT_APK = "pref_sign_apk"
    const val KEY_DECODING_FOLDER = "pref_decode_folder"
    const val KEY_DECODING_MODE = "pref_decode_mode"
    const val KEY_USE_CUSTOM_SIGN = "pref_use_custom_sign"
    const val KEY_COPY_ORIGINAL_FILES = "pref_copy_original_files"
    const val KEY_DEBUG_MODE = "pref_debug_mode"
    const val KEY_VERBOSE_MODE = "pref_verbose_mode"
    /** No longer used: apktool 3 always uses the bundled aapt2 and its own framework folder. */
    const val KEY_FRAMEWORK_PATH = "pref_framework_path"
    /** No longer used: apktool 3 always uses the bundled aapt2 and its own framework folder. */
    const val KEY_AAPT_PATH = "pref_aapt_path"
    /** No longer used: apktool 3 always uses the bundled aapt2 and its own framework folder. */
    const val KEY_AAPT2_PATH = "pref_aapt2_path"
    const val KEY_TOOLS_INSTALLED = "pref_tools_installed"
    /** Signature scheme switches; v2 keeps its historical key. */
    const val KEY_USE_V2_SIGNATURE = "pref_use_v2_signature"
    const val KEY_SIGN_V1 = "pref_sign_v1"
    const val KEY_SIGN_V3 = "pref_sign_v3"
    const val KEY_SIGN_V4 = "pref_sign_v4"

    const val KEY_KEYSTORE_FILE = "pref_keystore_file"
    const val KEY_STORE_KEY = "pref_key_password"
    const val KEY_PRIVATE_KEY = "pref_private_password"
    const val KEY_KEY_TYPE = "pref_key_type"
    const val KEY_CERT_PATH = "pref_cert_path"
    const val KEY_PRIVATE_KEY_PATH = "pref_private_key_path"

    const val KEY_USE_REGEX = "pref_key_use_regex"
    const val KEY_MATCH_CASE = "pref_key_match_case"
    const val KEY_WHOLE_WORDS_ONLY = "pref_key_whole_words_only"
    const val KEY_RECURSIVELY = "pref_key_recursively"
    const val KEY_FILES_MODE = "pref_key_files_mode"
    const val KEY_EXTENSIONS = "pref_key_extensions"
    const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
    const val KEY_CONFIRM_BUILD = "pref_confirm_build"
    const val KEY_THEME = "pref_ui_theme"
    const val KEY_CURRENT_THEME = "current_theme"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_AUTO_THEME = "pref_auto_theme_mode"
    const val KEY_AUTO_THEME_PICKER = "pref_auto_theme_picker"
    //translation options
    const val KEY_SKIP_TRANSLATED = "pref_skip_translated_lines"
    const val KEY_SKIP_SUPPORT_LINES = "pref_skip_support_lines"
    const val KEY_REVERSE_TRANSLATED = "pref_reverse_translated"
}
