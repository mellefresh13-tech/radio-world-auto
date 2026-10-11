package com.mellefresh13.radio

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/** Removes built-in transparent / solid-colour margins from a logo so it can fill a backdrop edge to edge. */
object BitmapTrim {
    fun trimBorders(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src
        val scale = minOf(1f, 96f / maxOf(w, h))
        val sw = maxOf(1, (w * scale).toInt())
        val sh = maxOf(1, (h * scale).toInt())
        val small = if (scale < 1f) Bitmap.createScaledBitmap(src, sw, sh, true) else src
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)

        val corner = px[0]
        val transparentBorder = Color.alpha(corner) < 16
        val solidBorder = !transparentBorder &&
            close(px[sw - 1], corner) && close(px[(sh - 1) * sw], corner) && close(px[sh * sw - 1], corner)
        if (!transparentBorder && !solidBorder) return src

        fun isBg(c: Int) = if (transparentBorder) Color.alpha(c) < 16 else close(c, corner)

        var minX = sw; var maxX = -1; var minY = sh; var maxY = -1
        for (y in 0 until sh) for (x in 0 until sw) {
            if (!isBg(px[y * sw + x])) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return src

        val left = (minX.toFloat() / sw * w).toInt().coerceIn(0, w - 1)
        val top = (minY.toFloat() / sh * h).toInt().coerceIn(0, h - 1)
        val right = (((maxX + 1).toFloat() / sw) * w).toInt().coerceIn(left + 1, w)
        val bottom = (((maxY + 1).toFloat() / sh) * h).toInt().coerceIn(top + 1, h)
        val nw = right - left
        val nh = bottom - top
        // Don't crop tiny marks out of big empty canvases, and skip negligible trims.
        if (nw < w * 0.35f || nh < h * 0.35f) return src
        if (nw > w * 0.98f && nh > h * 0.98f) return src
        return runCatching { Bitmap.createBitmap(src, left, top, nw, nh) }.getOrDefault(src)
    }

    private fun close(a: Int, b: Int): Boolean =
        abs(Color.alpha(a) - Color.alpha(b)) <= 12 &&
            abs(Color.red(a) - Color.red(b)) <= 12 &&
            abs(Color.green(a) - Color.green(b)) <= 12 &&
            abs(Color.blue(a) - Color.blue(b)) <= 12
}
