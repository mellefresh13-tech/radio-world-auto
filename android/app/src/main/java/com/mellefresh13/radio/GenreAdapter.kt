package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_genre, parent, false)

        val profile = UiProfile.from(parent.resources)
        if (profile.isPhonePortrait && profile.widthDp > 400) {
            val card = view as ViewGroup
            card.setPadding(
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.toFloat(), parent.resources.displayMetrics).toInt(),
                0,
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8.toFloat(), parent.resources.displayMetrics).toInt(),
                0
            )

            val icon = card.getChildAt(0) as ImageView
            val info = card.getChildAt(1) as ViewGroup
            val arrow = card.getChildAt(2) as ImageView

            icon.layoutParams = (icon.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 52.toFloat(), parent.resources.displayMetrics).toInt()
                height = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 52.toFloat(), parent.resources.displayMetrics).toInt()
                marginStart = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 0.toFloat(), parent.resources.displayMetrics).toInt()
                padding = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 12.toFloat(), parent.resources.displayMetrics).toInt()
            }
            info.layoutParams = (info.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                marginStart = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.toFloat(), parent.resources.displayMetrics).toInt()
            }
            info.findViewById<TextView>(R.id.genreName).textSize = 16f
            info.findViewById<TextView>(R.id.genreCount).textSize = 11f
            arrow.layoutParams = (arrow.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 34.toFloat(), parent.resources.displayMetrics).toInt()
                height = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 34.toFloat(), parent.resources.displayMetrics).toInt()
                marginStart = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 6.toFloat(), parent.resources.displayMetrics).toInt()
            }
            arrow.setPadding(
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8.toFloat(), parent.resources.displayMetrics).toInt(),
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8.toFloat(), parent.resources.displayMetrics).toInt(),
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8.toFloat(), parent.resources.displayMetrics).toInt(),
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8.toFloat(), parent.resources.displayMetrics).toInt()
            )
            view.layoutParams.height = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 116.toFloat(), parent.resources.displayMetrics).toInt()
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = items[position]
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
