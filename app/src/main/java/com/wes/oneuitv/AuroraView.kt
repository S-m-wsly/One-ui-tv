package com.wes.oneuitv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

class Palette(val name: String, val a: Int, val b: Int, val c: Int)

/** Soft drifting colour blobs, like the One UI wallpaper. Redraws at about 20 fps to stay light on weak chips. */
class AuroraView(context: Context) : View(context) {

    private var pal = Palette("Aurora", Color.rgb(20, 20, 60), Color.rgb(60, 50, 140), Color.rgb(40, 150, 160))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var skip = 0
    private var base: LinearGradient? = null

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 26000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            skip++
            if (skip % 3 == 0) invalidate()
        }
    }

    fun setPalette(p: Palette) {
        pal = p
        base = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val clear = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val strong = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
        paint.shader = RadialGradient(cx, cy, r, strong, clear, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (base == null) {
            base = LinearGradient(0f, 0f, w, h, intArrayOf(pal.a, pal.b), null, Shader.TileMode.CLAMP)
        }
        paint.shader = base
        c.drawRect(0f, 0f, w, h, paint)
        val r = w * 0.45f
        blob(c, w * (0.1f + 0.12f * t), h * (0.1f + 0.15f * t), r, pal.c)
        blob(c, w * (0.95f - 0.12f * t), h * (0.3f + 0.1f * t), r, pal.b)
        blob(c, w * (0.45f + 0.1f * t), h * (1.0f - 0.12f * t), r, pal.c)
        // soft dark wash so white text stays readable
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(70, 5, 8, 16), Color.argb(130, 5, 8, 16), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
    }
}
