package com.mellefresh13.radio

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object UiMotion {
    private const val PRESS_SCALE = 0.96f
    private const val PRESS_DOWN_MS = 90L
    private const val PRESS_UP_MS = 150L
    private const val ENTER_MS = 180L
    private const val POPUP_IN_MS = 180L
    private const val POPUP_OUT_MS = 180L
    private const val POPUP_VISIBLE_MS = 1_600L

    private var activePopup: TextView? = null
    private var activePopupHost: ViewGroup? = null

    fun pressFeedback(view: View, scale: Float = PRESS_SCALE) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(PRESS_DOWN_MS)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(PRESS_UP_MS)
                        .start()
                }
            }
            false
        }
    }

    fun enter(view: View, offsetDp: Int = 6) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(view, offsetDp).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_MS)
            .start()
    }

    fun pulse(view: View?, fromAlpha: Float = 0.45f) {
        view?.animate()?.cancel()
        view?.alpha = fromAlpha
        view?.animate()
            ?.alpha(1f)
            ?.setDuration(160L)
            ?.start()
    }

    fun showPopup(host: ViewGroup, message: String) {
        if (message.isBlank()) return

        activePopup?.let { popup ->
            popup.animate().cancel()
            if (activePopupHost != null) activePopupHost?.overlay?.remove(popup)
        }
        activePopup = null
        activePopupHost = null

        host.post {
            if (host.width <= 0 || host.height <= 0) return@post

            val popup = TextView(host.context).apply {
                text = message
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.create(
                    "sans-serif-medium",
                    android.graphics.Typeface.BOLD
                )
                includeFontPadding = false
                gravity = android.view.Gravity.CENTER
                setPadding(dp(this, 14), dp(this, 8), dp(this, 14), dp(this, 8))
                setBackgroundResource(R.drawable.bg_status_pill)
                elevation = dp(this, 8).toFloat()
                alpha = 0f
                scaleX = 0.96f
                scaleY = 0.96f
            }

            val maxWidth = (host.width - dp(host, 32)).coerceAtLeast(dp(host, 120))
            popup.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val left = (host.width - popup.measuredWidth) / 2
            val top = dp(host, 14)
            popup.layout(
                left,
                top,
                left + popup.measuredWidth,
                top + popup.measuredHeight
            )

            host.overlay.add(popup)
            activePopup = popup
            activePopupHost = host

            popup.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(POPUP_IN_MS)
                .start()

            host.postDelayed({
                if (activePopup !== popup) return@postDelayed
                popup.animate()
                    .alpha(0f)
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .setDuration(POPUP_OUT_MS)
                    .withEndAction {
                        host.overlay.remove(popup)
                        if (activePopup === popup) {
                            activePopup = null
                            activePopupHost = null
                        }
                    }
                    .start()
            }, POPUP_VISIBLE_MS)
        }
    }

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density).toInt()
}
