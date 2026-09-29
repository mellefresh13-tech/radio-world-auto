package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        val profile = UiProfile.from(parent.resources)
        if (profile.isPhoneLandscape) {
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            view.layoutParams.height = dp(150)
            (view as ViewGroup).clipChildren = true
            view.clipToOutline = true
            val logo = view.findViewById<ImageView>(R.id.logoImage)
            logo.layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
            logo.setBackgroundResource(0)
            logo.setPadding(0, 0, 0, 0)
            logo.alpha = 0.12f
            logo.scaleType = ImageView.ScaleType.CENTER_CROP
            val title = view.findViewById<TextView>(R.id.stationTitle)
            title.layoutParams = android.widget.FrameLayout.LayoutParams(-1, dp(30), android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8); leftMargin = dp(10); rightMargin = dp(44) }
            title.gravity = android.view.Gravity.CENTER
            title.textSize = 15f
            val meta = view.findViewById<TextView>(R.id.stationMeta)
            meta.visibility = View.GONE
            val hint = view.findViewById<TextView>(R.id.stationHint)
            hint.visibility = View.GONE
            val favorite = view.findViewById<ImageButton>(R.id.favoriteButton)
            favorite.layoutParams = android.widget.FrameLayout.LayoutParams(dp(30), dp(30), android.view.Gravity.TOP or android.view.Gravity.END).apply { topMargin = dp(6); rightMargin = dp(6) }
            favorite.setBackgroundResource(0)
            favorite.setPadding(0, 0, 0, 0)
            val play = view.findViewById<ImageButton>(R.id.playButton)
            play.layoutParams = android.widget.FrameLayout.LayoutParams(dp(64), dp(64), android.view.Gravity.CENTER)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = items[position]
        holder.logo.setImageResource(R.drawable.app_logo)
        holder.logo.imageTintList = null
        holder.logo.tag = station.id
        station.logo?.takeIf { it.isNotBlank() }?.let { url ->
            ImageLoader.load(url) { bitmap ->
                if (holder.logo.tag == station.id) { holder.logo.imageTintList = null; holder.logo.setImageBitmap(bitmap) }
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
            notifyItemChanged(holder.bindingAdapterPosition)
            holder.favorite.animate().cancel()
            holder.favorite.scaleX = 0.7f
            holder.favorite.scaleY = 0.7f
            holder.favorite.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator()).start()
        }
        holder.itemView.setOnClickListener { onPlay(station) }
    }

    override fun getItemCount(): Int = items.size
    fun submitList(newItems: List<Station>) { items = newItems; notifyDataSetChanged() }
}
