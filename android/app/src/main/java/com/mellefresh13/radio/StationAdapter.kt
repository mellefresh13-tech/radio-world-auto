package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private var items: List<Station>,
    private val onPlay: (Station) -> Unit,
    private val onFavorite: (Station) -> Unit,
    private val isCurrent: (Station) -> Boolean = { false }
) : RecyclerView.Adapter<StationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logoImage)
        val title: TextView = view.findViewById(R.id.stationTitle)
        val meta: TextView = view.findViewById(R.id.stationMeta)
        val favorite: ImageButton = view.findViewById(R.id.favoriteButton)
        val play: ImageButton = view.findViewById(R.id.playButton)
        val hint: TextView = view.findViewById(R.id.stationHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val profile = UiProfile.from(parent.resources)
        if (profile.isPhoneLandscape) {
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            val card = android.widget.FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(150))
                setBackgroundResource(R.drawable.bg_card)
                clipChildren = true
                clipToOutline = true
                elevation = dp(2).toFloat()
            }

            val logo = ImageView(parent.context).apply {
                id = R.id.logoImage
                setImageResource(R.drawable.app_logo)
                alpha = 0.12f
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
            }
            card.addView(logo, android.widget.FrameLayout.LayoutParams(-1, -1))

            val title = TextView(parent.context).apply {
                id = R.id.stationTitle
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setTextColor(parent.context.getColor(R.color.auto_text_main))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                setHorizontallyScrolling(true)
            }
            card.addView(title, android.widget.FrameLayout.LayoutParams(-1, dp(30), android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(8)
                leftMargin = dp(14)
                rightMargin = dp(48)
            })

            val meta = TextView(parent.context).apply {
                id = R.id.stationMeta
                visibility = View.GONE
            }
            card.addView(meta, android.widget.FrameLayout.LayoutParams(1, 1))

            val hint = TextView(parent.context).apply {
                id = R.id.stationHint
                visibility = View.GONE
            }
            card.addView(hint, android.widget.FrameLayout.LayoutParams(1, 1))

            val favorite = android.widget.ImageButton(parent.context).apply {
                id = R.id.favoriteButton
                setBackgroundResource(0)
                setPadding(0, 0, 0, 0)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = "Favorite"
                setMinimumWidth(0)
                setMinimumHeight(0)
            }
            card.addView(favorite, android.widget.FrameLayout.LayoutParams(dp(30), dp(30), android.view.Gravity.TOP or android.view.Gravity.END).apply {
                topMargin = dp(6)
                rightMargin = dp(6)
            })

            val play = android.widget.ImageButton(parent.context).apply {
                id = R.id.playButton
                setBackgroundResource(R.drawable.bg_giant_play)
                setImageResource(R.drawable.ic_play)
                imageTintList = android.content.res.ColorStateList.valueOf(parent.context.getColor(R.color.auto_bg))
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = "Play"
                setMinimumWidth(0)
                setMinimumHeight(0)
                setPadding(0, 0, 0, 0)
            }
            card.addView(play, android.widget.FrameLayout.LayoutParams(dp(64), dp(64), android.view.Gravity.CENTER))

            return ViewHolder(card)
        }

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        if (profile.isPhonePortrait) {
            val card = view as FrameLayout
            card.clipChildren = true
            card.clipToOutline = true
            val logo = view.findViewById<ImageView>(R.id.logoImage)
            logo.alpha = 0.12f
            logo.background = null
            logo.setPadding(0, 0, 0, 0)
            logo.scaleType = ImageView.ScaleType.CENTER_CROP
            logo.layoutParams = (logo.layoutParams as FrameLayout.LayoutParams).apply {
                width = -1
                height = -1
                gravity = android.view.Gravity.FILL
                leftMargin = 0
                topMargin = 0
                rightMargin = 0
                bottomMargin = 0
            }
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = items[position]
        holder.logo.animate().cancel()
        holder.logo.alpha = 1f
        holder.logo.setImageResource(R.drawable.app_logo)
        holder.logo.imageTintList = null
        holder.logo.tag = station.id
        station.logo?.takeIf { it.isNotBlank() }?.let { url ->
            ImageLoader.load(url) { bitmap ->
                if (holder.logo.tag == station.id) {
                    holder.logo.imageTintList = null
                    holder.logo.setImageBitmap(bitmap)
                    holder.logo.alpha = 0f
                    holder.logo.animate().alpha(1f).setDuration(220L).start()
                }
            }
        }
        holder.title.text = station.name
        holder.meta.text = listOf(station.country, station.genre).filter { it.isNotBlank() }.joinToString(" • ")
        holder.hint.text = when {
            isCurrent(station) -> "● NOW PLAYING"
            !station.songTitle.isNullOrBlank() || !station.artist.isNullOrBlank() -> {
                val artist = station.artist?.trim().orEmpty()
                val title = station.songTitle?.trim().orEmpty()
                when {
                    artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"
                    title.isNotBlank() -> title
                    else -> artist
                }
            }
            else -> "RADIO STATION"
        }
        holder.hint.setTextColor(holder.itemView.context.getColor(
            when {
                isCurrent(station) -> R.color.auto_success
                !station.songTitle.isNullOrBlank() || !station.artist.isNullOrBlank() -> R.color.auto_text_muted
                else -> R.color.auto_text_dark
            }
        ))
        holder.title.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        holder.title.isSingleLine = true
        holder.title.setHorizontallyScrolling(true)
        holder.title.marqueeRepeatLimit = -1
        if (UiProfile.from(holder.itemView.resources).isPhonePortrait) {
            holder.title.setPadding(dp(6), 0, dp(6), 0)
            holder.meta.setPadding(dp(6), 0, dp(6), 0)
            holder.hint.setPadding(dp(6), 0, dp(6), 0)
        } else {
            holder.title.setPadding(0, 0, 0, 0)
            holder.meta.setPadding(0, 0, 0, 0)
            holder.hint.setPadding(0, 0, 0, 0)
        }
        holder.meta.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        holder.meta.isSingleLine = true
        holder.meta.setHorizontallyScrolling(true)
        holder.meta.marqueeRepeatLimit = -1
        holder.hint.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        holder.hint.isSingleLine = true
        holder.hint.setHorizontallyScrolling(true)
        holder.hint.marqueeRepeatLimit = -1
        holder.itemView.post {
            holder.title.isSelected = true
            holder.meta.isSelected = true
            holder.hint.isSelected = true
        }
        holder.itemView.setBackgroundResource(if (isCurrent(station)) R.drawable.bg_station_current else R.drawable.bg_card)
        holder.favorite.setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        holder.favorite.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_muted))
        holder.play.setImageResource(R.drawable.ic_play)
        holder.play.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(R.color.auto_bg))
        holder.play.setOnClickListener { onPlay(station) }
        holder.favorite.setOnClickListener {
            onFavorite(station)
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(adapterPosition)
            holder.favorite.animate().cancel()
            holder.favorite.scaleX = 0.7f
            holder.favorite.scaleY = 0.7f
            holder.favorite.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator()).start()
        }
        UiMotion.pressFeedback(holder.itemView)
        UiMotion.pressFeedback(holder.play)
        holder.itemView.setOnClickListener { onPlay(station) }
    }

    private fun dp(value: Int): Int = (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    override fun getItemCount(): Int = items.size
    fun submitList(newItems: List<Station>) { items = newItems; notifyDataSetChanged() }
}
