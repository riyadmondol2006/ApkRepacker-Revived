package com.riyadm.apkrepacker.ide.editor.theme.model

import androidx.annotation.Keep

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

@Keep
class ThemeModel {

    @field:SerializedName("theme_name")
    @field:Expose
    var themeName: String? = null
    @field:SerializedName("theme_type")
    @field:Expose
    var themeType: String? = null
    @field:SerializedName("dropdown_background")
    @field:Expose
    var dropdownBackground: String? = null
    @field:SerializedName("dropdown_border")
    @field:Expose
    var dropdownBorder: String? = null
    @field:SerializedName("dropdown_foreground")
    @field:Expose
    var dropdownForeground: String? = null
    @field:SerializedName("view_background_color")
    @field:Expose
    var viewBackgroundColor: String? = null
    @field:SerializedName("view_caret_color")
    @field:Expose
    var viewCaretColor: String? = null
    @field:SerializedName("view_gutter_background_color")
    @field:Expose
    var viewGutterBackgroundColor: String? = null
    @field:SerializedName("view_gutter_foreground_color")
    @field:Expose
    var viewGutterForegroundColor: String? = null
    @field:SerializedName("view_selection_color")
    @field:Expose
    var viewSelectionColor: String? = null
    @field:SerializedName("view_comment")
    @field:Expose
    var viewComment: String? = null
    @field:SerializedName("view_keyword")
    @field:Expose
    var viewKeyword: String? = null
    @field:SerializedName("view_name")
    @field:Expose
    var viewName: String? = null
    @field:SerializedName("view_literal")
    @field:Expose
    var viewLiteral: String? = null
    @field:SerializedName("view_operator")
    @field:Expose
    var viewOperator: String? = null
    @field:SerializedName("view_separator")
    @field:Expose
    var viewSeparator: String? = null
    @field:SerializedName("view_package")
    @field:Expose
    var viewPackage: String? = null
    @field:SerializedName("view_type")
    @field:Expose
    var viewType: String? = null
    @field:SerializedName("view_error")
    @field:Expose
    var viewError: String? = null
    @field:SerializedName("view_string")
    @field:Expose
    var viewString: String? = null
    @field:SerializedName("view_default")
    @field:Expose
    var viewDefault: String? = null
    @field:SerializedName("view_constant")
    @field:Expose
    var viewConstant: String? = null
    @field:SerializedName("view_whitespace_color")
    @field:Expose
    var viewWhitespaceColor: String? = null
}
