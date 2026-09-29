package com.mellefresh13.radio

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small presentation-only hook for controls that are created dynamically in MainActivity. */
object ResponsiveUiPatch {
    fun install(activity: Activity) {
        val root = activity.window.decorView
        root.viewTreeObserver.addOnGlobalLayoutListener {
            hideStationInfo(root)
            val profile = UiProfile.from(activity.resources)
            if (profile.isPhoneLandscape) setupLandscapePhoneSidebar(activity)
            if (profile.isPhonePortrait || profile.isPhoneLandscape) centerMobileMiniPlayers(root, profile)
        }
    }

    private fun hideStationInfo(root: View) {
        if (root !is ViewGroup) return
        val matches = ArrayList<View>()
        root.findViewsWithText(matches, "Station details", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        matches.forEach { it.visibility = View.GONE }
    }

    private fun setupLandscapePhoneSidebar(activity: Activity) {
        val nav = activity.findViewById<ViewGroup>(R.id.navContainer) ?: return
        if (nav.findViewWithTag<View>("responsive_phone_sidebar") != null) return

        val brandIcon = activity.findViewById<View>(R.id.navBrandIcon) ?: return
        val brandLabel = activity.findViewById<View>(R.id.navBrandLabel) ?: return
        val buttons = listOf(
            activity.findViewById<View>(R.id.navPlayer),
            activity.findViewById<View>(R.id.navCountries),
            activity.findViewById<View>(R.id.navGenres),
            activity.findViewById<View>(R.id.navFavorites),
            activity.findViewById<View>(R.id.navRecents),
            activity.findViewById<View>(R.id.navSearch)
        ).filterNotNull()
        val mini = activity.findViewById<ViewGroup>(R.id.navMiniPlayerContainer) ?: return

        buttons.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        (brandIcon.parent as? ViewGroup)?.removeView(brandIcon)
        (brandLabel.parent as? ViewGroup)?.removeView(brandLabel)
        (mini.parent as? ViewGroup)?.removeView(mini)
        nav.removeAllViews()
        nav.tag = "responsive_phone_sidebar"

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(brandIcon, LinearLayout.LayoutParams(-1, dp(activity, 40)))
            addView(brandLabel, LinearLayout.LayoutParams(-1, dp(activity, 20)))
        }
        nav.addView(header, LinearLayout.LayoutParams(-1, dp(activity, 62)))

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        buttons.forEach { list.addView(it) }
        scroll.addView(list, ScrollView.LayoutParams(-1, -2))
        nav.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        nav.addView(mini, LinearLayout.LayoutParams(-1, dp(activity, 86)))
        mini.visibility = View.GONE
    }

    private fun centerMobileMiniPlayers(root: View, profile: UiProfile) {
        val candidates = ArrayList<FrameLayout>()
        collectMiniCards(root, candidates, profile)
        candidates.forEach { card ->
            val favorite = findChildByDescription(card, "Favorite") ?: return@forEach
            val play = findChildByDescription(card, "Play") ?: findChildByDescription(card, "Pause") ?: return@forEach

            val starSize = dp(root.context as Activity, 30)
            favorite.layoutParams = FrameLayout.LayoutParams(starSize, starSize, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(root.context as Activity, 6)
                rightMargin = dp(root.context as Activity, 6)
            }
            favorite.setBackgroundResource(R.drawable.bg_icon_button)
            favorite.requestLayout()

            val playSize = if (profile.isPhonePortrait) dp(root.context as Activity, 62) else dp(root.context as Activity, 54)
            play.layoutParams = FrameLayout.LayoutParams(playSize, playSize, Gravity.CENTER)
            play.setBackgroundResource(R.drawable.bg_giant_play)
            play.requestLayout()
        }
    }

    private fun collectMiniCards(view: View, result: MutableList<FrameLayout>, profile: UiProfile) {
        if (view is FrameLayout) {
            val minHeight = dp(view.context as Activity, 60)
            val maxHeight = dp(view.context as Activity, 120)
            if (view.height in minHeight..maxHeight &&
                findChildByDescription(view, "Favorite") != null &&
                (findChildByDescription(view, "Play") != null || findChildByDescription(view, "Pause") != null)
            ) {
                result.add(view)
                return
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectMiniCards(view.getChildAt(index), result, profile)
        }
    }

    private fun findChildByDescription(root: ViewGroup, description: String): View? {
        val direct = root.findViewsWithText(ArrayList(), description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        if (direct.isNotEmpty()) return direct.first()
        return null
    }

    private fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
