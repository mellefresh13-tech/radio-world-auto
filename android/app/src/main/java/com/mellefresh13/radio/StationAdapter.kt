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
    private val onFavorite: (Station) -> Unit
) : RecyclerView.Adapter<StationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logoImage)
        val title: TextView = view.findViewById(R.id.stationTitle)
        val meta: TextView = view.findViewById(R.id.stationMeta)
        val favorite: ImageButton = view.findViewById(R.id.favoriteButton)
        val play: ImageButton = view.findViewById(R.id.playButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = items[position]
        holder.logo.setImageResource(R.drawable.ic_radio)
        holder.logo.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(R.color.auto_accent))
        station.logo?.takeIf { it.isNotBlank() }?.let { url ->
            holder.logo.tag = station.id
            ImageLoader.load(url) { bitmap ->
                if (holder.logo.tag == station.id) { holder.logo.imageTintList = null; holder.logo.setImageBitmap(bitmap) }
            }
        }
        holder.title.text = station.name
        holder.meta.text = station.country + " • " + station.city + " • " + station.genre
        holder.title.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        holder.title.isSingleLine = true
        holder.title.setHorizontallyScrolling(true)
        holder.title.marqueeRepeatLimit = -1
        holder.meta.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        holder.meta.isSingleLine = true
        holder.meta.setHorizontallyScrolling(true)
        holder.meta.marqueeRepeatLimit = -1
        holder.itemView.post { holder.title.isSelected = true; holder.meta.isSelected = true }
        holder.favorite.setImageResource(if (station.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        holder.favorite.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(if (station.favorite) R.color.auto_accent else R.color.auto_text_muted))
        holder.play.setImageResource(R.drawable.ic_play)
        holder.play.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(R.color.auto_bg))
        holder.play.setOnClickListener { onPlay(station) }
        holder.favorite.setOnClickListener { onFavorite(station); notifyItemChanged(holder.bindingAdapterPosition) }
        holder.itemView.setOnClickListener { onPlay(station) }
    }

    override fun getItemCount(): Int = items.size
    fun submitList(newItems: List<Station>) { items = newItems; notifyDataSetChanged() }
}
