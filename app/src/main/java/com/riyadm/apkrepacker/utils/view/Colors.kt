package com.riyadm.apkrepacker.utils.view

import android.graphics.Color

/**
 * Fixed palette for content colors that carry meaning on their own (log levels, swatches); UI
 * chrome takes its colors from the theme instead.
 */
object Colors {
    @JvmField val VERY_LIGHT_RED = Color.rgb(255, 102, 102)
    @JvmField val LIGHT_RED = Color.rgb(255, 51, 51)
    @JvmField val RED = Color.rgb(255, 0, 0)
    @JvmField val DARK_RED = Color.rgb(204, 0, 0)
    @JvmField val VERY_DARK_RED = Color.rgb(153, 0, 0)
    @JvmField val VERY_LIGHT_BLUE = Color.rgb(51, 204, 255)
    @JvmField val LIGHT_BLUE = Color.rgb(51, 153, 255)
    @JvmField val BLUE = Color.rgb(0, 0, 255)
    @JvmField val DARK_BLUE = Color.rgb(0, 0, 204)
    @JvmField val VERY_DARK_BLUE = Color.rgb(0, 0, 153)
    @JvmField val VERY_LIGHT_GREEN = Color.rgb(102, 255, 102)
    @JvmField val LIGHT_GREEN = Color.rgb(0, 255, 51)
    @JvmField val GREEN = Color.rgb(0, 204, 0)
    @JvmField val DARK_GREEN = Color.rgb(0, 153, 0)
    @JvmField val VERY_DARK_GREEN = Color.rgb(0, 102, 0)
    @JvmField val VERY_LIGHT_YELLOW = Color.rgb(255, 255, 204)
    @JvmField val LIGHT_YELLOW = Color.rgb(255, 255, 153)
    @JvmField val YELLOW = Color.rgb(255, 255, 0)
    @JvmField val DARK_YELLOW = Color.rgb(255, 204, 0)
    @JvmField val LIGHT_ORANGE = Color.rgb(255, 153, 0)
    @JvmField val ORANGE = Color.rgb(255, 102, 0)
    @JvmField val GOLD = Color.rgb(255, 204, 51)
    @JvmField val LIGHT_GREY = Color.rgb(204, 204, 204)
    @JvmField val GREY = Color.rgb(153, 153, 153)
    @JvmField val DARK_GREY = Color.rgb(102, 102, 102)
    @JvmField val VERY_DARK_GREY = Color.rgb(51, 51, 51)
    @JvmField val LIGHT_BROWN = Color.rgb(153, 102, 0)
    @JvmField val BROWN = Color.rgb(102, 51, 0)
    @JvmField val DARK_BROWN = Color.rgb(51, 0, 0)
    @JvmField val PURPLE = Color.rgb(102, 0, 153)
    @JvmField val BLACK = Color.rgb(0, 0, 0)
    @JvmField val WHITE = Color.rgb(255, 255, 255)
}
