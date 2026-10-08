package com.riyadm.apkrepacker.fragment

/** Implemented by fragments that want to handle back before the activity pops the back stack. */
fun interface OnBackPressedListener {
    fun onBackPressed()
}
