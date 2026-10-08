package com.jaredrummler.android.colorpicker

import androidx.annotation.ColorInt

interface ColorPickerDialogListener {

    /**
     * Invoked when a colour is selected from the colour picker dialog.
     *
     * @param dialogId The dialog id used to create the dialog instance.
     * @param color The selected colour.
     */
    fun onColorSelected(dialogId: Int, @ColorInt color: Int)

    /**
     * Invoked when the colour picker dialog was dismissed.
     *
     * @param dialogId The dialog id used to create the dialog instance.
     */
    fun onDialogDismissed(dialogId: Int)
}
