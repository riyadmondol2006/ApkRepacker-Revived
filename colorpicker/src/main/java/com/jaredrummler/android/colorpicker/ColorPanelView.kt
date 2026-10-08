package com.jaredrummler.android.colorpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.use
import androidx.appcompat.widget.TooltipCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a swatch filled with a colour. It morphs between a circle and a rounded square on a spring
 * while pressed or checked, and shows a checkerboard behind translucent colours.
 */
class ColorPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var argb = Color.BLACK
    private var originalArgb = Color.BLACK
    private var shape = ColorShape.CIRCLE
    private var showOldColor = false
    private var explicitBorder: Int? = null

    /** 0 = resting shape, 1 = the opposite shape. Driven by a spring. */
    private var morph = 0f
    private var morphed = false

    var isChecked = false
        set(value) {
            if (field == value) return
            field = value
            updateDescription()
            updateMorphTarget()
            invalidate()
        }

    /** Square-cornered swatch without outline or morph; used for segments of a connected tonal strip. */
    var isFlat = false
        set(value) {
            field = value
            invalidate()
        }

    private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = DrawingUtils.dpToPx(context, 1f)
    }
    private val bounds = RectF()
    private val inner = RectF()
    private val check = AppCompatResources.getDrawable(context, R.drawable.cpv_ic_check)?.mutate()

    private val morphSpring = SpringAnimation(this, MORPH).apply {
        spring = SpringForce(0f).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.6f)
        setMinimumVisibleChange(0.002f)
    }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ColorPanelView).use {
            shape = it.getInt(R.styleable.ColorPanelView_cpv_colorShape, ColorShape.CIRCLE)
            showOldColor = it.getBoolean(R.styleable.ColorPanelView_cpv_showOldColor, false)
            if (it.hasValue(R.styleable.ColorPanelView_cpv_borderColor)) {
                explicitBorder = it.getColor(R.styleable.ColorPanelView_cpv_borderColor, Color.TRANSPARENT)
            }
        }
        patternPaint.shader = DrawingUtils.alphaShader(DrawingUtils.dpToPx(context, 6f).toInt())
        updateDescription()
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable("instanceState", super.onSaveInstanceState())
        putInt("color", argb)
        putBoolean("checked", isChecked)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val restored = if (state is Bundle) {
            argb = state.getInt("color")
            isChecked = state.getBoolean("checked")
            updateDescription()
            @Suppress("DEPRECATION")
            state.getParcelable("instanceState")
        } else {
            state
        }
        super.onRestoreInstanceState(restored)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        updateMorphTarget()
    }

    /** Forces the opposite shape, e.g. while the user drags a slider that edits this colour. */
    fun setMorphed(on: Boolean) {
        morphed = on
        updateMorphTarget()
    }

    private fun updateMorphTarget() {
        val target = if (isChecked || isPressed || morphed) 1f else 0f
        if (!isAttachedToWindow) {
            morph = target
            invalidate()
            return
        }
        morphSpring.animateToFinalPosition(target)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        morphSpring.cancel()
        morph = if (isChecked || morphed) 1f else 0f
    }

    private fun updateDescription() {
        val hex = hexString()
        TooltipCompat.setTooltipText(this, hex)
        if (contentDescription.isNullOrEmpty() || contentDescription.toString().startsWith("#")) {
            contentDescription = hex
        }
        isSelected = isChecked
    }

    private fun hexString(): String =
        if (Color.alpha(argb) == 255) {
            String.format(Locale.ROOT, "#%06X", 0xFFFFFF and argb)
        } else {
            String.format(Locale.ROOT, "#%08X", argb)
        }

    private fun resolveBorder(): Int = explicitBorder
        ?: DrawingUtils.themeColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0x33000000)

    /** Reveals the colour code (long press tooltip). */
    fun showHint() {
        performLongClick()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        val stroke = borderPaint.strokeWidth
        if (shape == ColorShape.CIRCLE) {
            val side = min(w, h).toFloat()
            val cx = paddingLeft + w / 2f
            val cy = paddingTop + h / 2f
            bounds.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
        } else {
            bounds.set(paddingLeft.toFloat(), paddingTop.toFloat(), (paddingLeft + w).toFloat(), (paddingTop + h).toFloat())
        }
        if (!isFlat) bounds.inset(stroke, stroke)
        val side = min(bounds.width(), bounds.height())
        val circle = side / 2f
        val squircle = side * 0.28f
        val (rest, other) = if (shape == ColorShape.CIRCLE) circle to squircle else squircle to circle
        val radius = if (isFlat) 0f else rest + (other - rest) * morph

        if (showOldColor) {
            drawFill(canvas, bounds, radius, originalArgb)
            val shrink = side * 0.18f
            inner.set(bounds)
            inner.inset(shrink, shrink)
            drawFill(canvas, inner, max(0f, radius - shrink), argb)
        } else {
            drawFill(canvas, bounds, radius, argb)
        }

        if (!isFlat) {
            borderPaint.color = resolveBorder()
            canvas.drawRoundRect(bounds, radius, radius, borderPaint)
        }

        val tick = check
        if (tick != null && morph > 0.02f && isChecked) {
            val size = side * 0.5f * min(1.15f, morph)
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            tick.setTint(DrawingUtils.contrastOn(argb))
            tick.setBounds((cx - size / 2f).toInt(), (cy - size / 2f).toInt(), (cx + size / 2f).toInt(), (cy + size / 2f).toInt())
            tick.draw(canvas)
        }
    }

    private fun drawFill(canvas: Canvas, rect: RectF, radius: Float, @ColorInt color: Int) {
        if (Color.alpha(color) < 255) {
            canvas.drawRoundRect(rect, radius, radius, patternPaint)
        }
        colorPaint.color = color
        canvas.drawRoundRect(rect, radius, radius, colorPaint)
    }

    fun setColor(@ColorInt color: Int) {
        argb = color
        updateDescription()
        invalidate()
    }

    @ColorInt
    fun getColor(): Int = argb

    /** Colour of the outer ring, drawn when `cpv_showOldColor` is set. */
    fun setOriginalColor(@ColorInt color: Int) {
        originalArgb = color
        invalidate()
    }

    fun setBorderColor(@ColorInt color: Int) {
        explicitBorder = color
        invalidate()
    }

    @ColorInt
    fun getBorderColor(): Int = resolveBorder()

    fun setShape(@ColorShape.Type shape: Int) {
        this.shape = shape
        invalidate()
    }

    @ColorShape.Type
    fun getShape(): Int = shape

    private companion object {
        val MORPH = object : FloatPropertyCompat<ColorPanelView>("morph") {
            override fun getValue(view: ColorPanelView): Float = view.morph
            override fun setValue(view: ColorPanelView, value: Float) {
                view.morph = value
                view.invalidate()
            }
        }
    }
}
