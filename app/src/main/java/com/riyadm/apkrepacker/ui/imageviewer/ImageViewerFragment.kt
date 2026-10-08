package com.riyadm.apkrepacker.ui.imageviewer

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.riyadm.apkrepacker.databinding.FragmentImageViewerBinding
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.ui.motion.springTo
import com.sdsmdg.harjot.vectormaster.VectorMasterView
import java.io.File

/** Full-screen swipeable viewer for the images and vector drawables of a folder. */
class ImageViewerFragment : Fragment(R.layout.fragment_image_viewer) {

    private var binding: FragmentImageViewerBinding? = null
    private var paths: List<String> = emptyList()
    private var backgroundState = BACKGROUND_CHECKER
    private var barsVisible = true
    private var editorFabWanted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = arguments?.getStringArrayList(ARG_PATHS).orEmpty()
        backgroundState = savedInstanceState?.getInt(STATE_BACKGROUND) ?: BACKGROUND_CHECKER
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Activity recreated without viewer data: nothing to show.
        if (paths.isEmpty()) {
            requireActivity().finish()
            return
        }
        val b = FragmentImageViewerBinding.bind(view).also { binding = it }

        b.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        b.btnSwitchBackground.setOnClickListener { cycleBackground() }
        b.fabGoEditor.setOnClickListener {
            startActivity(Intent(requireContext(), CodeEditorActivity::class.java).putExtra("filePath", currentPath(b)))
        }

        b.viewPager.adapter = ImageViewerAdapter(::toggleBars).also { it.replace(paths) }
        b.viewPager.setPageTransformer(ViewPagerTransformers.DEPTH)
        b.viewPager.offscreenPageLimit = 1
        if (savedInstanceState == null) {
            b.viewPager.setCurrentItem(arguments?.getInt(ARG_POSITION) ?: 0, false)
        }
        b.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateTitle(b)
        })
        applyBackground(b)
        updateTitle(b)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_BACKGROUND, backgroundState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun currentPath(b: FragmentImageViewerBinding) = paths[b.viewPager.currentItem.coerceIn(paths.indices)]

    /** Tap on an image hides or shows the toolbar and the editor FAB with springs. */
    private fun toggleBars() {
        val b = binding ?: return
        barsVisible = !barsVisible
        val target = if (barsVisible) 0f else -(b.appBar.height + b.appBar.top).toFloat()
        b.appBar.springTo(DynamicAnimation.TRANSLATION_Y, target, MotionSpring.DefaultSpatial)
        if (editorFabWanted) setEditorFabShown(b, barsVisible)
    }

    private fun setEditorFabShown(b: FragmentImageViewerBinding, shown: Boolean) {
        val fab = b.fabGoEditor
        if (shown && fab.visibility != View.VISIBLE) {
            fab.springIn(fromScale = 0.6f, fromTranslationY = fab.resources.getDimension(R.dimen.space_6))
        } else if (!shown && fab.visibility == View.VISIBLE) {
            fab.springOut(toScale = 0.6f) { fab.visibility = View.GONE }
        }
    }

    private fun cycleBackground() {
        val b = binding ?: return
        backgroundState = (backgroundState + 1) % BACKGROUND_COUNT
        applyBackground(b)
    }

    private fun applyBackground(b: FragmentImageViewerBinding) {
        // Content colors behind the image (not UI chrome): checkerboard shows transparency.
        when (backgroundState) {
            BACKGROUND_CHECKER -> b.viewPager.setBackgroundResource(R.drawable.alpha)
            BACKGROUND_WHITE -> b.viewPager.setBackgroundColor(Color.WHITE)
            BACKGROUND_GRAY -> b.viewPager.setBackgroundColor(Color.GRAY)
            else -> b.viewPager.setBackgroundColor(Color.BLACK)
        }
    }

    private fun updateTitle(b: FragmentImageViewerBinding) {
        val path = currentPath(b)
        val file = File(path)
        val isXml = path.endsWith(".xml")
        val (width, height) = if (isXml) vectorSize(file) else bitmapSize(path)

        b.toolbar.title = file.name
        val size = paths.size
        b.toolbar.subtitle = if (size > 1) {
            getString(R.string.image_viewer_subtitle_format, b.viewPager.currentItem + 1, size) +
                if (width > 0 && height > 0) " ($width×$height)" else ""
        } else {
            null
        }

        editorFabWanted = isXml
        setEditorFabShown(b, isXml && barsVisible)
    }

    private fun vectorSize(file: File): Pair<Int, Int> {
        val model = VectorMasterView(context, file).vectorModel ?: return -1 to -1
        return model.width.toInt() to model.height.toInt()
    }

    /** Reads only the bounds, so flipping through big images stays cheap. */
    private fun bitmapSize(path: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return options.outWidth to options.outHeight
    }

    companion object {
        private const val ARG_PATHS = "paths"
        private const val ARG_POSITION = "position"
        private const val STATE_BACKGROUND = "background"

        private const val BACKGROUND_CHECKER = 0
        private const val BACKGROUND_WHITE = 1
        private const val BACKGROUND_GRAY = 2
        private const val BACKGROUND_COUNT = 4

        @JvmStatic
        fun newInstance(paths: List<String>, position: Int) = ImageViewerFragment().apply {
            arguments = bundleOf(ARG_PATHS to ArrayList(paths), ARG_POSITION to position)
        }
    }
}
