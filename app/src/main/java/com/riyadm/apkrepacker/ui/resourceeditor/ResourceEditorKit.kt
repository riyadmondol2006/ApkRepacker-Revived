package com.riyadm.apkrepacker.ui.resourceeditor

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo

/**
 * Wires an Expressive [SearchBar] to its expanding [SearchView]: typing filters the list behind the bar,
 * closing the view leaves the query visible in the bar, and a trailing action clears it again.
 */
class ResourceSearch(
    private val searchBar: SearchBar,
    private val searchView: SearchView,
    private val onQuery: (String) -> Unit,
) {

    init {
        searchView.setupWithSearchBar(searchBar)
        searchBar.inflateMenu(R.menu.menu_resource_search)
        searchBar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_search) {
                clear()
                true
            } else {
                false
            }
        }
        searchView.editText.doAfterTextChanged { onQuery(it?.toString().orEmpty()) }
        searchView.editText.setOnEditorActionListener { _, _, _ ->
            syncBar()
            searchView.hide()
            false
        }
        searchView.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.HIDDEN) syncBar()
        }
        syncBar()
    }

    val query: String
        get() = searchView.text?.toString().orEmpty()

    fun clear() {
        searchView.setText("")
        syncBar()
    }

    private fun syncBar() {
        val text = query
        searchBar.setText(text)
        searchBar.menu.findItem(R.id.action_clear_search)?.isVisible = text.isNotEmpty()
    }
}

/** Short feedback message above any bottom-anchored action. */
fun View.showSnack(message: CharSequence, anchor: View? = null) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).setAnchorView(anchor).show()
}

/** Extended FAB collapses to an icon while the list scrolls down and expands again on the way up. */
fun RecyclerView.shrinkFabOnScroll(fab: ExtendedFloatingActionButton) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            when {
                dy > 4 && fab.isExtended -> fab.shrink()
                dy < -4 && !fab.isExtended -> fab.extend()
            }
        }
    })
}

private val CornerSizeProperty = object : FloatPropertyCompat<MaterialCardView>("cornerSize") {
    private val bounds = RectF(0f, 0f, 10_000f, 10_000f)

    override fun getValue(card: MaterialCardView): Float =
        card.shapeAppearanceModel.topLeftCornerSize.getCornerSize(bounds)

    override fun setValue(card: MaterialCardView, value: Float) {
        card.shapeAppearanceModel = card.shapeAppearanceModel.withCornerSize(value.coerceAtLeast(0f))
    }
}

/** Shape morph: springs every corner of the card to [radiusPx] (squarer on press, rounder when selected). */
fun MaterialCardView.morphCorners(radiusPx: Float, spring: MotionSpring = MotionSpring.FastSpatial) {
    val animation = getTag(R.id.h_tag_corner_spring) as? SpringAnimation
        ?: SpringAnimation(this, CornerSizeProperty).also {
            it.setMinimumVisibleChange(0.5f)
            setTag(R.id.h_tag_corner_spring, it)
        }
    animation.spring = SpringForce(radiusPx).apply {
        stiffness = spring.stiffness
        dampingRatio = spring.dampingRatio
    }
    animation.start()
}

/**
 * Press feedback for tonal cards: the card squishes and its corners tighten while the finger is down,
 * then both spring back with overshoot. Doesn't consume touches.
 */
fun MaterialCardView.pressMorph(restRadiusPx: Float, pressedRadiusPx: Float, pressedScale: Float = 0.98f) {
    setOnTouchListener { card, event ->
        val target = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedScale to pressedRadiusPx
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1f to restRadiusPx
            else -> null
        }
        if (target != null) {
            card.springTo(DynamicAnimation.SCALE_X, target.first, MotionSpring.FastSpatial)
            card.springTo(DynamicAnimation.SCALE_Y, target.first, MotionSpring.FastSpatial)
            (card as MaterialCardView).morphCorners(target.second)
        }
        false
    }
}

/** Dialog fragments report to their parent fragment, or to the host activity when they are shown from it. */
inline fun <reified T> Fragment.findListener(): T? = (parentFragment as? T) ?: (activity as? T)
