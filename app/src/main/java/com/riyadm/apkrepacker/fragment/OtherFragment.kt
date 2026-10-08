package com.riyadm.apkrepacker.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentOtherBinding
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springTo
import com.riyadm.apkrepacker.utils.FragmentUtils

/** Tool grid: tonal tiles whose icon containers morph from a circle to a squircle while pressed. */
class OtherFragment : Fragment() {

    private var binding: FragmentOtherBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentOtherBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        val tiles = listOf(
            Triple(b.actionSettings, b.settingsIcon, ::openSettings),
            Triple(b.actionCheckForUpdates, b.updatesIcon, ::showUpdatesNotice),
            Triple(b.actionAbout, b.aboutIcon, ::openAbout),
        )
        tiles.forEachIndexed { index, (tile, icon, onClick) ->
            tile.setOnClickListener { onClick() }
            tile.attachPressMotion(icon)
            tile.springIn(delayMs = index * 60L, fromScale = 0.92f, fromTranslationY = 48f)
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun openSettings() = openFragment(SettingsFragment())

    private fun openAbout() = openFragment(AboutFragment())

    private fun openFragment(fragment: Fragment) {
        FragmentUtils.add(fragment, parentFragmentManager, android.R.id.content)
    }

    private fun showUpdatesNotice() {
        view?.let { Snackbar.make(it, R.string.m3e_in_development, Snackbar.LENGTH_SHORT).show() }
    }

    /** Squishes the tile and morphs its icon container's corners while the finger is down; springs back on release. */
    @SuppressLint("ClickableViewAccessibility")
    private fun View.attachPressMotion(iconContainer: MaterialCardView) {
        val morph = SpringAnimation(FloatValueHolder(CIRCLE)).apply {
            spring = SpringForce(CIRCLE).apply {
                stiffness = MotionSpring.FastSpatial.stiffness
                dampingRatio = MotionSpring.FastSpatial.dampingRatio
            }
            setMinimumVisibleChange(0.002f)
            addUpdateListener { _, value, _ ->
                iconContainer.shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .setAllCornerSizes(RelativeCornerSize(value.coerceIn(0f, 0.5f)))
                    .build()
            }
        }
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.springTo(DynamicAnimation.SCALE_X, PRESSED_SCALE, MotionSpring.FastSpatial)
                    v.springTo(DynamicAnimation.SCALE_Y, PRESSED_SCALE, MotionSpring.FastSpatial)
                    morph.animateToFinalPosition(SQUIRCLE)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.springTo(DynamicAnimation.SCALE_X, 1f, MotionSpring.FastSpatial)
                    v.springTo(DynamicAnimation.SCALE_Y, 1f, MotionSpring.FastSpatial)
                    morph.animateToFinalPosition(CIRCLE)
                }
            }
            false
        }
    }

    companion object {
        const val TAG = "OtherFragment"

        private const val CIRCLE = 0.5f
        private const val SQUIRCLE = 0.28f
        private const val PRESSED_SCALE = 0.97f
    }
}
