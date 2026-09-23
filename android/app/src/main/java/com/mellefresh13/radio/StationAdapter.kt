package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private var items: List<Station>,
    private val onPlay: (Station) -> Unit,
    private val onFavorite: (Station) -> Unit
) : RecyclerView.Adapter<StationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: TextView = view.findViewById(R.id.logoText)
        val title: TextView = view.findViewById(R.id.stationTitle)
        val meta: TextView = view.findViewById(R.id.stationMeta)
        val favorite: Button = view.findViewById(R.id.favoriteButton)
        val play: Button = view.findViewById(R.id.playButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_station, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = items[position]

        holder.logo.text = station.name.firstOrNull()?.uppercase() ?: "R"
        holder.title.text = station.name
        holder.meta.text = station.country + " • " + station.city + " • " + station.genre
        holder.favorite.text = if (station.favorite) "★" else "☆"
        holder.favorite.setTextColor(
            holder.itemView.context.getColor(
                if (station.favorite) R.color.auto_accent else R.color.auto_text_muted
            )
        )

        holder.play.setOnClickListener { onPlay(station) }
        holder.favorite.setOnClickListener { onFavorite(station) }
        holder.itemView.setOnClickListener { onPlay(station) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Station>) {
        items = newItems
        notifyDataSetChanged()
    }
}
