package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val icon: TextView = view.findViewById(R.id.genreIcon)
        val name: TextView = view.findViewById(R.id.genreName)
        val count: TextView = view.findViewById(R.id.genreCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_genre, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = items[position]
        holder.icon.text = when (genre.name) {
            "Rock" -> "♫"
            "Jazz" -> "♬"
            "Classical" -> "♪"
            "Electronic", "Dance" -> "⚡"
            "News", "Talk" -> "▤"
            else -> "♫"
        }
        holder.name.text = genre.name
        holder.count.text = genre.stationCount.toString() + " stations"
        holder.itemView.setOnClickListener { onClick(genre) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<GenreItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
