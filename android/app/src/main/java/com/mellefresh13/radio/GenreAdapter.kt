package com.mellefresh13.radio

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class GenreItem(
    val name: String,
    val stationCount: Int
)

class GenreAdapter(
    private var items: List<GenreItem>,
    private val onClick: (GenreItem) -> Unit
) : RecyclerView.Adapter<GenreAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.genreIcon)
        val name: TextView = view.findViewById(R.id.genreName)
        val count: TextView = view.findViewById(R.id.genreCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val profile = UiProfile.from(parent.resources)
        if (profile.isPhoneLandscape) {
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            val card = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(150))
                setBackgroundResource(R.drawable.bg_card)
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
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_main))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                setHorizontallyScrolling(true)
            }
            card.addView(name, FrameLayout.LayoutParams(-1, dp(30), Gravity.TOP).apply { topMargin = dp(8); leftMargin = dp(8); rightMargin = dp(8) })
            val count = TextView(parent.context).apply {
                id = R.id.genreCount
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_muted))
                includeFontPadding = false
            }
            card.addView(count, FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM).apply { bottomMargin = dp(8) })
            return ViewHolder(card)
        }
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_genre, parent, false)
        val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
        if (profile.isPhonePortrait && profile.widthDp > 400) {
            val card = view as ViewGroup
            card.setPadding(dp(10), 0, dp(8), 0)
            val icon = card.getChildAt(0) as ImageView
            val info = card.getChildAt(1) as ViewGroup
            val arrow = card.getChildAt(2) as ImageView
            icon.layoutParams = (icon.layoutParams as android.widget.LinearLayout.LayoutParams).apply { width = dp(52); height = dp(52); marginStart = dp(0) }
            icon.setPadding(dp(12), dp(12), dp(12), dp(12))
            info.layoutParams = (info.layoutParams as android.widget.LinearLayout.LayoutParams).apply { marginStart = dp(10) }
            info.findViewById<TextView>(R.id.genreName).textSize = 16f
            info.findViewById<TextView>(R.id.genreCount).textSize = 11f
            arrow.layoutParams = (arrow.layoutParams as android.widget.LinearLayout.LayoutParams).apply { width = dp(34); height = dp(34); marginStart = dp(6) }
            arrow.setPadding(dp(8), dp(8), dp(8), dp(8))
            view.layoutParams.height = dp(116)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = items[position]
        holder.name.text = genre.name
        holder.count.text = genre.stationCount.toString() + " stations"
        holder.name.post { holder.name.isSelected = true }
        holder.itemView.setOnClickListener { onClick(genre) }
    }

    override fun getItemCount(): Int = items.size
    fun submitList(newItems: List<GenreItem>) { items = newItems; notifyDataSetChanged() }
}
