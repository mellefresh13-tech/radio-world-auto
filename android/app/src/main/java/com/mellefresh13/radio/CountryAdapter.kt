package com.mellefresh13.radio

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

data class CountryItem(
    val name: String,
    val code: String,
    val flag: String,
    val stationCount: Int
)

class CountryAdapter(
    private var items: List<CountryItem>,
    private val mobileCards: Boolean = false,
    private val onClick: (CountryItem) -> Unit
) : RecyclerView.Adapter<CountryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flag: TextView = view.findViewById(R.id.countryFlag)
        val name: TextView = view.findViewById(R.id.countryName)
        val count: TextView = view.findViewById(R.id.countryCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val profile = UiProfile.from(parent.resources)
        if (mobileCards) {
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            val phone = profile.isPhonePortrait || profile.isPhoneLandscape
            val cardHeight = when {
                profile.isPhonePortrait -> 124
                profile.isTabletPortrait -> 136
                profile.isTabletLandscape -> 154
                else -> 150
            }
            val titleSize = when {
                profile.isPhonePortrait -> 12f
                profile.isTabletPortrait -> 16f
                profile.isTabletLandscape -> 18f
                else -> 16f
            }
            val countSize = if (phone) 9f else if (profile.isTabletPortrait) 11f else 12f
            val flagSize = when {
                profile.isPhonePortrait -> 34f
                profile.isTabletPortrait -> 48f
                profile.isTabletLandscape -> 64f
                else -> 54f
            }
            val sideMargin = if (phone) 4 else 8
            val card = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(cardHeight))
                setBackgroundResource(R.drawable.bg_card)
                clipChildren = true
                clipToOutline = true
                elevation = dp(2).toFloat()
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            val name = TextView(parent.context).apply {
                id = R.id.countryName
                textSize = titleSize
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_main))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            card.addView(name, FrameLayout.LayoutParams(-1, dp(if (phone) 26 else 30), Gravity.TOP).apply {
                topMargin = dp(4); leftMargin = dp(sideMargin); rightMargin = dp(sideMargin)
            })
            val flag = TextView(parent.context).apply {
                id = R.id.countryFlag
                textSize = flagSize
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            card.addView(flag, FrameLayout.LayoutParams(-1, dp(if (phone) 48 else 66), Gravity.CENTER))
            val count = TextView(parent.context).apply {
                id = R.id.countryCount
                textSize = countSize
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_muted))
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            card.addView(count, FrameLayout.LayoutParams(-1, dp(20), Gravity.BOTTOM).apply { bottomMargin = dp(4) })
            UiMotion.pressFeedback(card)
            return ViewHolder(card)
        }
        if (!mobileCards && (profile.isPhoneLandscape || profile.isTabletLandscape)) {
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            val tablet = profile.isTabletLandscape
            val cardHeight = if (tablet) 154 else 150
            val titleSize = if (tablet) 18f else 16f
            val countSize = if (tablet) 12f else 11f
            val flagSize = if (tablet) 68f else 58f
            val sideMargin = if (tablet) 12 else 8
            val topBottomMargin = if (tablet) 10 else 8
            val card = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(cardHeight))
                setBackgroundResource(R.drawable.bg_card)
                clipChildren = true
                clipToOutline = true
                elevation = dp(2).toFloat()
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            val flag = TextView(parent.context).apply {
                id = R.id.countryFlag
                textSize = flagSize
                gravity = Gravity.CENTER
                alpha = 0.12f
                setTextColor(Color.WHITE)
            }
            card.addView(flag, FrameLayout.LayoutParams(-1, -1))
            val name = TextView(parent.context).apply {
                id = R.id.countryName
                textSize = titleSize
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_main))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                setHorizontallyScrolling(true)
            }
            card.addView(name, FrameLayout.LayoutParams(-1, dp(34), Gravity.TOP).apply { topMargin = dp(topBottomMargin); leftMargin = dp(sideMargin); rightMargin = dp(sideMargin) })
            val count = TextView(parent.context).apply {
                id = R.id.countryCount
                textSize = countSize
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_muted))
                includeFontPadding = false
            }
            card.addView(count, FrameLayout.LayoutParams(-1, dp(26), Gravity.BOTTOM).apply { bottomMargin = dp(topBottomMargin) })
            UiMotion.pressFeedback(card)
            return ViewHolder(card)
        }
        // Keep the existing automotive card layout unchanged.
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_country, parent, false)
        UiMotion.pressFeedback(view)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val country = items[position]
        holder.flag.text = country.flag
        holder.name.text = country.name
        holder.count.text = country.stationCount.toString() + " stations"
        holder.name.post { holder.name.isSelected = true }
        holder.itemView.setOnClickListener { onClick(country) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<CountryItem>) {
        val oldItems = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].code == newItems[newItemPosition].code
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}
