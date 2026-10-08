package com.jaredrummler.android.colorpicker

import androidx.annotation.IntDef

/** Base shape of a colour swatch. Swatches morph towards the opposite shape while pressed or checked. */
object ColorShape {
    const val SQUARE = 0
    const val CIRCLE = 1

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(SQUARE, CIRCLE)
    annotation class Type
}
