package com.wes.oneuitv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Everything is laid out on a 1280 x 720 grid (same as the web design) and scaled to the real screen. */
object Ui {
    var scale = 1f

    fun init(ctx: Context) {
        val m = ctx.resources.displayMetrics
        scale = max(m.widthPixels, m.heightPixels) / 1280f
    }

    fun px(v: Int): Int = (v * scale).toInt()
    fun pxf(v: Float): Float = v * scale
}

fun TextView.sizePx(n: Float) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, n * Ui.scale)
}

fun round(color: Int, radius: Float): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = Ui.pxf(radius)
    return g
}

/** The white One UI focus outline: it grows outward from the item with a small gap. */
class RingDrawable(private val radius: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    var grow = 0f
        set(v) {
            field = v
            invalidateSelf()
        }

    init {
        p.style = Paint.Style.STROKE
        p.color = Color.WHITE
    }

    override fun draw(c: Canvas) {
        if (grow <= 0f) return
        val off = Ui.pxf(5f) * grow
        p.strokeWidth = Ui.pxf(4f)
        p.alpha = (255 * min(1f, grow * 1.6f)).toInt()
        val r = RectF(bounds)
        r.inset(-off, -off)
        c.drawRoundRect(r, radius + off, radius + off, p)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(cf: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * One UI TV focus: springs up to 1.06 with an overshoot and grows a white outline.
 * Parents must have clipChildren = false so the outline can show outside the item.
 */
fun View.oneUiFocus(radius: Float, onFocus: ((Boolean) -> Unit)? = null) {
    val ring = RingDrawable(Ui.pxf(radius))
    overlay.add(ring)
    ring.setBounds(0, 0, width, height)
    addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> ring.setBounds(0, 0, r - l, b - t) }
    setOnFocusChangeListener { v, has ->
        val s = if (has) 1.06f else 1f
        v.animate().scaleX(s).scaleY(s)
            .setDuration(if (has) 380L else 220L)
            .setInterpolator(if (has) OvershootInterpolator(2.2f) else DecelerateInterpolator())
            .start()
        val a = ValueAnimator.ofFloat(ring.grow, if (has) 1f else 0f)
        a.duration = 300
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { ring.grow = it.animatedValue as Float }
        a.start()
        onFocus?.invoke(has)
    }
}

/** Press feedback: shrinks to 0.94, springs back, then runs the action. */
fun View.pressable(action: () -> Unit) {
    isFocusable = true
    isClickable = true
    setOnClickListener {
        animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction {
            val s = if (isFocused) 1.06f else 1f
            animate().scaleX(s).scaleY(s).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
            action()
        }.start()
    }
}
