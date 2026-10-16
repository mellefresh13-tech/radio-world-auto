package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private var items: List<Station>,
    private val onPlay: (Station) -> Unit,
    private val onFavorite: (Station) -> Unit,
    private val isCurrent: (Station) -> Boolean = { false },
    private val isPlaying: (Station) -> Boolean = { false },
    private val onTogglePlay: (Station) -> Unit = onPlay
) : RecyclerView.Adapter<StationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logoImage)
        val title: TextView = view.findViewById(R.id.stationTitle)
        val meta: TextView = view.findViewById(R.id.stationMeta)
        val favorite: ImageButton = view.findViewById(R.id.favoriteButton)
        val play: ImageButton = view.findViewById(R.id.playButton)
        val hint: TextView = view.findViewById(R.id.stationHint)
    }

    private var density = 1f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        density = parent.resources.displayMetrics.density
        val profile = UiProfile.from(parent.resources)
        if (profile.isPhonePortrait || profile.isPhoneLandscape) {
            // Phone station cards use one non-overlapping layout in both orientations:
            // marquee title and station details on the left, favorite above Play on the right.
            val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
            val card = android.widget.FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, dp(124))
                setBackgroundResource(R.drawable.bg_card)
                clipChildren = true
                clipToOutline = true
                elevation = dp(2).toFloat()
            }

            val logo = ImageView(parent.context).apply {
                id = R.id.logoImage
                setImageResource(R.drawable.app_logo)
                alpha = 0.20f
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
            }
            card.addView(logo, android.widget.FrameLayout.LayoutParams(-1, -1))

            val title = TextView(parent.context).apply {
                id = R.id.stationTitle
                textSize = 15f
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                // Keep the first glyph away from the card edge; reset marquee state on rebind below.
                setPadding(dp(4), 0, dp(8), 0)
                setTextColor(parent.context.getColor(R.color.auto_text_main))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                isHorizontalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(12))
                setHorizontallyScrolling(true)
            }
            card.addView(title, android.widget.FrameLayout.LayoutParams(-1, dp(24), android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(6)
                leftMargin = dp(8)
                // Title row only overlaps the favorite at the top, not the centered Play control.
                rightMargin = dp(56)
            })

            val meta = TextView(parent.context).apply {
                id = R.id.stationMeta
                textSize = 11f
                setTextColor(parent.context.getColor(R.color.auto_text_muted))
                includeFontPadding = false
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            card.addView(meta, android.widget.FrameLayout.LayoutParams(-1, dp(16), android.view.Gravity.TOP or android.view.Gravity.START).apply {
                topMargin = dp(34)
                leftMargin = dp(12)
                // Metadata is above Play but still shares the top Favorite row.
                rightMargin = dp(56)
            })

            val hint = TextView(parent.context).apply {
                id = R.id.stationHint
                textSize = 10f
                setTextColor(parent.context.getColor(R.color.auto_text_muted))
                includeFontPadding = false
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            card.addView(hint, android.widget.FrameLayout.LayoutParams(-1, dp(16), android.view.Gravity.TOP or android.view.Gravity.START).apply {
                topMargin = dp(54)
                leftMargin = dp(12)
                // Hint overlaps the lower Play control vertically, so keep its full control lane clear.
                rightMargin = dp(68)
            })

            val favorite = android.widget.ImageButton(parent.context).apply {
                id = R.id.favoriteButton
                setBackgroundResource(0)
                setPadding(0, 0, 0, 0)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = "Favorite"
                setMinimumWidth(0)
                setMinimumHeight(0)
            }
            card.addView(favorite, android.widget.FrameLayout.LayoutParams(dp(44), dp(44), android.view.Gravity.TOP or android.view.Gravity.END).apply {
                topMargin = dp(4)
                rightMargin = dp(4)
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
            card.addView(play, android.widget.FrameLayout.LayoutParams(dp(56), dp(56), android.view.Gravity.END or android.view.Gravity.BOTTOM).apply {
                rightMargin = dp(10)
                bottomMargin = dp(8)
            })

            val overlay = View(parent.context).apply { setBackgroundColor(parent.context.getColor(R.color.auto_card_overlay)); isClickable=false; isFocusable=false }
            card.addView(overlay,1,android.widget.FrameLayout.LayoutParams(-1,-1))
            return ViewHolder(card)
        }

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        (view as? FrameLayout)?.apply {
            clipChildren = true
            clipToOutline = true
        }
        if (!profile.isPhonePortrait) {
            val card = view as FrameLayout
            val logo = view.findViewById<ImageView>(R.id.logoImage)
            val content = card.getChildAt(1) as? android.widget.LinearLayout
            logo.background = null; logo.setPadding(0,0,0,0); logo.scaleType = ImageView.ScaleType.CENTER_CROP; logo.alpha=0.14f
            logo.layoutParams = (logo.layoutParams as FrameLayout.LayoutParams).apply { width=-1; height=-1; gravity=android.view.Gravity.FILL; leftMargin=0; topMargin=0; rightMargin=0; bottomMargin=0 }
            content?.layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
                // On portrait phones the logo is a soft full-card backdrop, not a left-side column.
                leftMargin = dp(12)
                rightMargin = dp(70)
            }
            val overlay = View(parent.context).apply { setBackgroundColor(parent.context.getColor(R.color.auto_card_overlay)); isClickable=false; isFocusable=false }
            card.addView(overlay,1,FrameLayout.LayoutParams(-1,-1))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val station = items[position]
        if (payloads.isNotEmpty()) {
            val payload = payloads.lastOrNull() as? Set<*>
            if (payload?.contains(PAYLOAD_PLAYBACK) == true) {
                holder.play.setImageResource(if (isPlaying(station)) R.drawable.ic_pause else R.drawable.ic_play)
                holder.play.contentDescription = if (isPlaying(station)) "Pause" else "Play"
            }
            if (payload?.contains(PAYLOAD_FAVORITE) == true) {
                holder.favorite.setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                holder.favorite.imageTintList = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(
                        if (station.favorite) R.color.auto_favorite else R.color.auto_text_muted
                    )
                )
            }
            if (payload != null && payload.contains(PAYLOAD_PLAYBACK) || payload?.contains(PAYLOAD_FAVORITE) == true) {
                return
            }
        }
        val profile = UiProfile.from(holder.itemView.resources)
        holder.logo.animate().cancel()
        holder.logo.alpha = if (profile.isPhonePortrait) 0.10f else 0.14f
        holder.logo.setImageResource(R.drawable.app_logo)
        holder.logo.imageTintList = null
        holder.logo.tag = station.id
        station.logo?.takeIf { it.isNotBlank() }?.let { url ->
            ImageLoader.load(url) { bitmap ->
                if (holder.logo.tag == station.id) {
                    holder.logo.imageTintList = null
                    holder.logo.setImageBitmap(bitmap)
                    holder.logo.alpha = if (profile.isPhonePortrait) 0.10f else 0.14f
                }
            }
        }
        // RecyclerView reuses TextViews. Stop the previous station's marquee before
        // replacing its text; otherwise the old horizontal scroll offset can hide the first
        // character of the next station (for example "Laut FM" -> "aut FM").
        holder.title.isSelected = false
        holder.title.text = ""
        holder.title.scrollTo(0, 0)
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
        holder.title.isHorizontalFadingEdgeEnabled = true
        holder.title.setFadingEdgeLength(dp(12))
        holder.title.isSingleLine = true
        holder.title.marqueeRepeatLimit = -1
        holder.title.setHorizontallyScrolling(true)
        if (profile.isPhonePortrait || profile.isPhoneLandscape) {
            // Keep the phone title inset; metadata uses explicit margins in the card layout.
            holder.title.setPadding(dp(4), 0, dp(16), 0)
            holder.meta.setPadding(0, 0, 0, 0)
            holder.hint.setPadding(0, 0, 0, 0)
        } else {
            holder.title.setPadding(dp(8), 0, dp(8), 0)
            holder.meta.setPadding(dp(4), 0, dp(4), 0)
            holder.hint.setPadding(dp(4), 0, dp(4), 0)
        }
        holder.meta.ellipsize = android.text.TextUtils.TruncateAt.END
        holder.meta.isSingleLine = true
        holder.meta.setHorizontallyScrolling(false)
        holder.hint.ellipsize = android.text.TextUtils.TruncateAt.END
        holder.hint.isSingleLine = true
        holder.hint.setHorizontallyScrolling(false)
        holder.itemView.post {
            // RecyclerView can retain the old marquee's horizontal offset even after scrollTo(0, 0).
            // Reset after layout, then start the new station title from its first character.
            holder.title.scrollTo(0, 0)
            holder.title.isSelected = true
            holder.meta.isSelected = true
            holder.hint.isSelected = true
        }
        holder.itemView.setBackgroundResource(R.drawable.bg_card)
        holder.favorite.setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        holder.favorite.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(if (station.favorite) R.color.auto_favorite else R.color.auto_text_muted))
        holder.play.setImageResource(if (isPlaying(station)) R.drawable.ic_pause else R.drawable.ic_play)
        holder.play.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(R.color.auto_bg))
        holder.play.contentDescription = if (isPlaying(station)) "Pause" else "Play"
        holder.play.setOnClickListener {
            if (isCurrent(station)) onTogglePlay(station) else onPlay(station)
        }
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

    private fun dp(value: Int): Int = (value * density).toInt()

    override fun getItemCount(): Int = items.size

    companion object {
        private const val PAYLOAD_FAVORITE = "favorite"
        private const val PAYLOAD_PLAYBACK = "playback"
    }

    fun submitList(newItems: List<Station>) {
        val oldItems = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].id == newItems[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val oldItem = oldItems[oldItemPosition]
                val newItem = newItems[newItemPosition]
                return buildSet {
                    if (oldItem.favorite != newItem.favorite) add(PAYLOAD_FAVORITE)
                    if (
                        oldItem.songTitle != newItem.songTitle ||
                        oldItem.artist != newItem.artist
                    ) add(PAYLOAD_PLAYBACK)
                }.takeIf { it.isNotEmpty() }
            }
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}
