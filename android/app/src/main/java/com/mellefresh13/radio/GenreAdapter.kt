package com.mellefresh13.radio

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

data class GenreItem(
    val name: String,
    val stationCount: Int
)

class GenreAdapter(
    private var items: List<GenreItem>,
    private val mobileCards: Boolean = false,
    private val onClick: (GenreItem) -> Unit
) : RecyclerView.Adapter<GenreAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.genreIcon)
        val name: TextView = view.findViewById(R.id.genreName)
        val count: TextView = view.findViewById(R.id.genreCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val profile = UiProfile.from(parent.resources)
        if (mobileCards) {
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            val metrics = CatalogCardMetrics.forProfile(profile, mobileCards = true)
            val phone = metrics.isPhone
            val cardHeight = metrics.cardHeightDp
            val titleSize = metrics.titleSizeSp
            val countSize = metrics.countSizeSp
            val iconSize = when {
                profile.isPhonePortrait -> 32
                profile.isTabletPortrait -> 44
                profile.isTabletLandscape -> 60
                else -> 50
            }
            val sideMargin = metrics.sideMarginDp
            val card = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(cardHeight))
                setBackgroundResource(R.drawable.bg_card)
                clipChildren = true
                clipToOutline = true
                elevation = dp(2).toFloat()
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            val name = TextView(parent.context).apply {
                id = R.id.genreName
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
            val icon = ImageView(parent.context).apply {
                id = R.id.genreIcon
                setImageResource(R.drawable.ic_music_note)
                imageTintList = android.content.res.ColorStateList.valueOf(parent.context.getColor(R.color.auto_accent))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    val accent = parent.context.getColor(R.color.auto_accent)
                    setColor(android.graphics.Color.argb(14, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent)))
                    setStroke(dp(1), android.graphics.Color.argb(38, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent)))
                }
            }
            card.addView(icon, FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER))
            val count = TextView(parent.context).apply {
                id = R.id.genreCount
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
            val metrics = CatalogCardMetrics.forProfile(profile, mobileCards = false)
            val cardHeight = metrics.cardHeightDp
            val titleSize = metrics.titleSizeSp
            val countSize = metrics.countSizeSp
            val sideMargin = metrics.sideMarginDp
            val topBottomMargin = metrics.verticalMarginDp
            val card = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(cardHeight))
                setBackgroundResource(R.drawable.bg_card)
                clipChildren = true
                clipToOutline = true
                elevation = dp(2).toFloat()
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            val icon = ImageView(parent.context).apply {
                id = R.id.genreIcon
                setImageResource(R.drawable.ic_music_note)
                imageTintList = android.content.res.ColorStateList.valueOf(parent.context.getColor(R.color.auto_text_main))
                alpha = 0.12f
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            card.addView(icon, FrameLayout.LayoutParams(-1, -1))
            val name = TextView(parent.context).apply {
                id = R.id.genreName
                textSize = titleSize
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_main))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isSingleLine = true
                setHorizontallyScrolling(false)
            }
            card.addView(name, FrameLayout.LayoutParams(-1, dp(34), Gravity.TOP).apply { topMargin = dp(topBottomMargin); leftMargin = dp(sideMargin); rightMargin = dp(sideMargin) })
            val count = TextView(parent.context).apply {
                id = R.id.genreCount
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_genre, parent, false)
        UiMotion.pressFeedback(view)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = items[position]
        // Genre tiles are label/count cards; hide the decorative music-note icon in every layout.
        holder.icon.visibility = View.GONE
        holder.name.text = genre.name
        holder.count.text = genre.stationCount.toString() + " stations"
        holder.name.post { holder.name.isSelected = true }
        holder.itemView.setOnClickListener { onClick(genre) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<GenreItem>) {
        val oldItems = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].name == newItems[newItemPosition].name
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}
