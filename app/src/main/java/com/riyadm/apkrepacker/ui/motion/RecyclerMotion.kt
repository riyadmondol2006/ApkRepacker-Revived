package com.riyadm.apkrepacker.ui.motion

import androidx.recyclerview.widget.RecyclerView

/**
 * Gives a list the Expressive item motion: items spring in with a staggered fade/scale/rise,
 * fade out and shrink when removed, and spring to their new place when moved. Every
 * RecyclerView in the app calls this once after creating its adapter. See [SpringItemAnimator].
 */
fun RecyclerView.applyExpressiveMotion() {
    if (itemAnimator !is SpringItemAnimator) itemAnimator = SpringItemAnimator()
}
