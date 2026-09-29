package com.mellefresh13.radio

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Presentation-only hook for controls that are created dynamically in MainActivity. */
object ResponsiveUiPatch {
    fun install(activity: Activity) {
        val root = activity.window.decorView
        root.viewTreeObserver.addOnGlobalLayoutListener {
            hideStationInfo(root)
            val profile = UiProfile.from(activity.resources)
            if (profile.isPhoneLandscape) setupLandscapePhoneSidebar(activity)
            if (profile.isPhonePortrait || profile.isPhoneLandscape) centerMobileMiniPlayers(activity, root, profile)
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
        if (nav.tag == "responsive_phone_sidebar") return

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

    private fun centerMobileMiniPlayers(activity: Activity, root: View, profile: UiProfile) {
        val candidates = ArrayList<FrameLayout>()
        collectMiniCards(activity, root, candidates)
        candidates.forEach { card ->
            val favorite = findChildByDescription(card, "Favorite") ?: return@forEach
            val play = findChildByDescription(card, "Play") ?: findChildByDescription(card, "Pause") ?: return@forEach

            favorite.layoutParams = FrameLayout.LayoutParams(dp(activity, 30), dp(activity, 30), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(activity, 6)
                rightMargin = dp(activity, 6)
            }
            favorite.setBackgroundResource(R.drawable.bg_icon_button)
            favorite.requestLayout()

            val playSize = if (profile.isPhonePortrait) dp(activity, 62) else dp(activity, 54)
            play.layoutParams = FrameLayout.LayoutParams(playSize, playSize, Gravity.CENTER)
            play.setBackgroundResource(R.drawable.bg_giant_play)
            play.requestLayout()
        }
    }

    private fun collectMiniCards(activity: Activity, view: View, result: MutableList<FrameLayout>) {
        if (view is FrameLayout) {
            val minHeight = dp(activity, 60)
            val maxHeight = dp(activity, 120)
            if (view.height in minHeight..maxHeight &&
                findChildByDescription(view, "Favorite") != null &&
                (findChildByDescription(view, "Play") != null || findChildByDescription(view, "Pause") != null)
            ) {
                result.add(view)
                return
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectMiniCards(activity, view.getChildAt(index), result)
        }
    }

    private fun findChildByDescription(root: ViewGroup, description: String): View? {
        val matches = ArrayList<View>()
        root.findViewsWithText(matches, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        return matches.firstOrNull()
    }

    private fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
