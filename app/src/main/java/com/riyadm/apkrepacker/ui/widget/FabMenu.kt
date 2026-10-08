package com.riyadm.apkrepacker.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.ui.motion.springTo

/**
 * Material 3 Expressive FAB menu built from official components: a Large [FloatingActionButton]
 * that expands into a stack of labeled small [ExtendedFloatingActionButton]s over a surface scrim.
 *
 * Declare the menu entries as [ExtendedFloatingActionButton] children in XML (top to bottom);
 * they are moved into the expanding stack. Every movement is a spring.
 *
 * The view itself is `match_parent`-friendly: while collapsed it doesn't intercept touches.
 */
class FabMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrim = View(context).apply {
        setBackgroundColor(MaterialColors.getColor(this@FabMenu, R.attr.colorSurface, 0))
        alpha = 0f
        visibility = GONE
        isClickable = true
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END or Gravity.BOTTOM
    }
    private val itemsHost = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
        visibility = GONE
    }
    private val mainFab: FloatingActionButton

    private var openDescription: CharSequence
    private var closeDescription: CharSequence
    private val items = ArrayList<ExtendedFloatingActionButton>()

    /** Called for a tapped entry after the menu started to collapse. */
    var onItemClick: ((View) -> Unit)? = null

    var isOpen: Boolean = false
        private set

    private var menuVisible = true

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FabMenu, defStyleAttr, 0)
        val icon = a.getResourceId(R.styleable.FabMenu_fabMenuIcon, R.drawable.ic_add)
        openDescription = a.getText(R.styleable.FabMenu_fabMenuOpenDescription) ?: context.getString(R.string.files_fab_open)
        closeDescription = a.getText(R.styleable.FabMenu_fabMenuCloseDescription) ?: context.getString(R.string.files_fab_close)
        a.recycle()

        // Id'd so the instance state of declared children is not lost; the menu's own parts are not.
        mainFab = FloatingActionButton(context, null, R.attr.floatingActionButtonLargeStyle).apply {
            setImageResource(icon)
            contentDescription = openDescription
            setOnClickListener { toggle() }
        }
        super.addView(scrim, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim.setOnClickListener { close() }
        column.addView(itemsHost, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
        })
        column.addView(mainFab, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
        })
        val margin = resources.getDimensionPixelSize(R.dimen.screen_margin)
        super.addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
            setMargins(margin, margin, margin, margin)
        })
    }

    /** Entries declared in XML land in the stack; everything else is a regular child. */
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (child is ExtendedFloatingActionButton && child !== mainFab) {
            addEntry(child)
        } else {
            super.addView(child, index, params)
        }
    }

    private fun addEntry(entry: ExtendedFloatingActionButton) {
        items.add(entry)
        val gap = resources.getDimensionPixelSize(R.dimen.space_3)
        itemsHost.addView(entry, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
            bottomMargin = gap
        })
        entry.extend()
        entry.setOnClickListener { view ->
            close()
            onItemClick?.invoke(view)
        }
    }

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        if (isOpen) return
        isOpen = true
        mainFab.contentDescription = closeDescription
        mainFab.springTo(DynamicAnimation.ROTATION, 45f, MotionSpring.DefaultSpatial)
        scrim.visibility = VISIBLE
        scrim.springTo(DynamicAnimation.ALPHA, SCRIM_ALPHA, MotionSpring.DefaultEffects)
        itemsHost.visibility = VISIBLE
        // The entry nearest to the FAB appears first and the stack fans out upwards.
        items.asReversed().forEachIndexed { index, entry ->
            entry.pivotX = entry.width.toFloat().takeIf { it > 0 } ?: 0f
            entry.springIn(
                delayMs = index * STAGGER_MS,
                fromScale = 0.6f,
                fromTranslationY = resources.getDimension(R.dimen.space_6),
            )
        }
        ViewCompat.setImportantForAccessibility(scrim, IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        mainFab.contentDescription = openDescription
        mainFab.springTo(DynamicAnimation.ROTATION, 0f, MotionSpring.DefaultSpatial)
        scrim.springTo(DynamicAnimation.ALPHA, 0f, MotionSpring.FastEffects) {
            if (!isOpen) scrim.visibility = GONE
        }
        items.forEachIndexed { index, entry ->
            val last = index == items.lastIndex
            entry.springOut(toScale = 0.6f) {
                if (last && !isOpen) itemsHost.visibility = GONE
            }
        }
        if (items.isEmpty()) itemsHost.visibility = GONE
    }

    /** Shows or hides the whole menu (e.g. while files are multi-selected). */
    fun setMenuVisible(visible: Boolean) {
        if (visible == menuVisible) return
        menuVisible = visible
        if (visible) {
            mainFab.visibility = VISIBLE
            mainFab.springIn(fromScale = 0.4f)
        } else {
            close()
            mainFab.springOut(toScale = 0.4f) { if (!menuVisible) mainFab.visibility = INVISIBLE }
        }
    }

    /** The main FAB, e.g. to anchor a Snackbar to it. */
    val anchorView: View get() = mainFab

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        mainFab.isEnabled = enabled
    }

    private companion object {
        const val SCRIM_ALPHA = 0.9f
        const val STAGGER_MS = 35L
    }
}
