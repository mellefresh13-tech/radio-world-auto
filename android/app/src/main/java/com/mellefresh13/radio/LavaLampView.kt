package com.mellefresh13.radio

import android.graphics.Color
import android.view.View

internal class LavaLampView(context: android.content.Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var palette = LavaLampView.defaultPalette()
    private var phase = 0f
    private var paletteAnimator: android.animation.ValueAnimator? = null
    private var animationEnabled = context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
        .getBoolean(KEY_LAVA_LAMP_ENABLED, true)
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 14000L
        repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        alpha = 0.94f
        if (!animationEnabled) visibility = View.GONE
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun setAnimationEnabled(enabled: Boolean) {
        if (animationEnabled == enabled) return
        animationEnabled = enabled
        visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    fun setPalette(bitmap: android.graphics.Bitmap?) {
        setPaletteColors(
            if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
                LavaLampView.defaultPalette()
            } else {
                paletteFromBitmap(bitmap)
            }
        )
    }

    fun fadeTo(target: Float) {
        animate().cancel()
        animate().alpha(target).setDuration(600L).start()
    }

    fun setPaletteColors(colors: IntArray, animate: Boolean = true) {
        if (colors.isEmpty()) return
        val target = IntArray(3) { colors[it % colors.size] }
        paletteAnimator?.cancel()
        paletteAnimator = null
        if (!animate) {
            palette = target
            invalidate()
            return
        }
        if (!animationEnabled) {
            palette = target
            invalidate()
            return
        }
        val from = palette.copyOf()
        paletteAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 780L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { value ->
                val fraction = value.animatedValue as Float
                palette = IntArray(target.size) { i ->
                    android.animation.ArgbEvaluator().evaluate(fraction, from[i], target[i]) as Int
                }
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationEnabled && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        paletteAnimator?.cancel()
        paletteAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val t = phase * (2f * Math.PI.toFloat())
        val radius = maxOf(width, height) * 0.72f
        val centers = arrayOf(
            Pair(0.50f + 0.28f * kotlin.math.sin(t.toDouble()).toFloat(), 0.48f + 0.33f * kotlin.math.cos((t * 0.83f).toDouble()).toFloat()),
            Pair(0.50f + 0.32f * kotlin.math.sin((t + 2.1f).toDouble()).toFloat(), 0.52f + 0.31f * kotlin.math.cos((t * 0.72f + 1.4f).toDouble()).toFloat()),
            Pair(0.50f + 0.25f * kotlin.math.sin((t + 4.0f).toDouble()).toFloat(), 0.50f + 0.34f * kotlin.math.cos((t * 0.91f + 2.7f).toDouble()).toFloat())
        )

        for (i in centers.indices) {
            val (cx, cy) = centers[i]
            val color = palette[i % palette.size]
            val midColor = Color.argb(
                (Color.alpha(color) * 0.48f).toInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
            paint.shader = android.graphics.RadialGradient(
                width * cx,
                height * cy,
                radius,
                intArrayOf(color, midColor, Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(width * cx, height * cy, radius, paint)
        }
        paint.shader = null
    }

    companion object {
        private const val SETTINGS_PREFS = "radio_world_auto_settings"
        private const val KEY_LAVA_LAMP_ENABLED = "lava_lamp_enabled"

        fun isAnimationEnabled(context: android.content.Context): Boolean =
            context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_LAVA_LAMP_ENABLED, true)

        fun setAnimationEnabled(context: android.content.Context, enabled: Boolean) {
            context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LAVA_LAMP_ENABLED, enabled)
                .apply()
        }

        fun paletteFromBitmap(bitmap: android.graphics.Bitmap): IntArray {
            if (bitmap.width <= 0 || bitmap.height <= 0) return defaultPalette()
            // Build a weighted hue histogram from visible, reasonably saturated pixels.
            // This preserves multicolour logos instead of reducing every logo to one hue.
            val bins = FloatArray(24)
            val hsv = FloatArray(3)
            val stepX = (bitmap.width / 64).coerceAtLeast(1)
            val stepY = (bitmap.height / 64).coerceAtLeast(1)
            for (y in 0 until bitmap.height step stepY) {
                for (x in 0 until bitmap.width step stepX) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.alpha(color) < 100) continue
                    Color.colorToHSV(color, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    if (saturation < 0.24f || value < 0.18f) continue
                    val bin = ((hsv[0] / 360f) * bins.size).toInt().coerceIn(0, bins.lastIndex)
                    bins[bin] += saturation * (1f - kotlin.math.abs(value - 0.62f) * 0.55f)
                }
            }

            val ranked = bins.indices.sortedByDescending { bins[it] }
            val selectedHues = mutableListOf<Float>()
            for (bin in ranked) {
                if (bins[bin] <= 0f) break
                val hue = (bin + 0.5f) * (360f / bins.size)
                if (selectedHues.none { existing ->
                        val delta = kotlin.math.abs(existing - hue)
                        minOf(delta, 360f - delta) < 32f
                    }) {
                    selectedHues.add(hue)
                }
                if (selectedHues.size == 3) break
            }

            if (selectedHues.isEmpty()) return defaultPalette()
            val hues = when (selectedHues.size) {
                1 -> listOf(selectedHues[0], (selectedHues[0] + 26f) % 360f, (selectedHues[0] + 335f) % 360f)
                2 -> listOf(selectedHues[0], selectedHues[1], (selectedHues[0] + 335f) % 360f)
                else -> selectedHues
            }
            val alpha = intArrayOf(220, 190, 175)
            val saturation = floatArrayOf(0.82f, 0.88f, 0.78f)
            val value = floatArrayOf(0.92f, 0.95f, 0.88f)
            return IntArray(3) { i ->
                Color.HSVToColor(alpha[i], floatArrayOf(hues[i], saturation[i], value[i]))
            }
        }

        fun defaultPalette() = intArrayOf(
            Color.argb(220, 35, 101, 220),
            Color.argb(190, 10, 13, 22),
            Color.argb(175, 238, 242, 248)
        )
    }
}
