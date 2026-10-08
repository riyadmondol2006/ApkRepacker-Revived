package com.riyadm.apkrepacker.utils

/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.view.View
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import me.zhanghai.android.fastscroll.FastScroller

/**
 * Keeps a scrolling view's own padding and adds the system bar insets to it, mirroring them on a
 * [FastScroller]. EdgeToEdgeUtils already pads the activity content view and consumes the insets,
 * so this only has an effect where insets reach a view directly.
 */
class ScrollingViewOnApplyWindowInsetsListener(
    view: View?,
    private val fastScroller: FastScroller?,
) : View.OnApplyWindowInsetsListener {

    private val basePadding: Insets = view
        ?.let { Insets.of(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
        ?: Insets.NONE

    init {
        // Prevent FastScroller from using view padding even if no window insets is dispatched.
        fastScroller?.setPadding(0, 0, 0, 0)
    }

    constructor() : this(null, null)

    override fun onApplyWindowInsets(view: View, insets: WindowInsets): WindowInsets {
        val bars = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
            .getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(basePadding.left + bars.left, basePadding.top, basePadding.right + bars.right, basePadding.bottom + bars.bottom)
        fastScroller?.setPadding(bars.left, 0, bars.right, bars.bottom)
        return insets
    }
}
