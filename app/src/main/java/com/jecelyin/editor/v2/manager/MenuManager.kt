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
package com.jecelyin.editor.v2.manager

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.color.MaterialColors

/**
 * Tints menu and toolbar icons with the theme's on-surface colors, so they follow light, dark
 * and dynamic color.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
object MenuManager {

    @JvmStatic
    fun makeToolbarNormalIcon(context: Context, @DrawableRes resId: Int): Drawable? =
        tint(context, ContextCompat.getDrawable(context, resId), com.google.android.material.R.attr.colorOnSurface)

    @JvmStatic
    fun makeToolbarDisabledIcon(context: Context, drawable: Drawable?): Drawable? =
        tint(context, drawable, com.google.android.material.R.attr.colorOutline)

    @JvmStatic
    fun makeMenuNormalIcon(context: Context, @DrawableRes resId: Int): Drawable? =
        tint(context, ContextCompat.getDrawable(context, resId), com.google.android.material.R.attr.colorOnSurfaceVariant)

    private fun tint(context: Context, drawable: Drawable?, @AttrRes colorAttr: Int): Drawable? {
        drawable ?: return null
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, MaterialColors.getColor(context, colorAttr, 0))
        return wrapped
    }
}
