package com.mellefresh13.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class CountryItem(
    val name: String,
    val code: String,
    val flag: String,
    val stationCount: Int
)

class CountryAdapter(
    private var items: List<CountryItem>,
    private val onClick: (CountryItem) -> Unit
) : RecyclerView.Adapter<CountryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flag: TextView = view.findViewById(R.id.countryFlag)
        val name: TextView = view.findViewById(R.id.countryName)
        val count: TextView = view.findViewById(R.id.countryCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_country, parent, false)

        val profile = UiProfile.from(parent.resources)
        val dp: (Int) -> Int = { value -> (value * parent.resources.displayMetrics.density).toInt() }
        if (profile.isPhonePortrait && profile.widthDp > 400) {
            val card = view as ViewGroup
            card.setPadding(
                dp(10),
                0,
                dp(8),
                0
            )

            val flag = card.getChildAt(0) as TextView
            val info = card.getChildAt(1) as ViewGroup
            val arrow = card.getChildAt(2) as ImageView

            flag.layoutParams = (flag.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = dp(52)
                height = dp(52)
                marginStart = dp(0)
            }
            info.layoutParams = (info.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                marginStart = dp(10)
            }
            info.findViewById<TextView>(R.id.countryName).textSize = 16f
            info.findViewById<TextView>(R.id.countryCount).textSize = 11f
            arrow.layoutParams = (arrow.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = dp(34)
                height = dp(34)
                marginStart = dp(6)
            }
            arrow.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
            )
            view.layoutParams.height = dp(116)
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val country = items[position]
        holder.flag.text = country.flag
        holder.name.text = country.name
        holder.count.text = country.stationCount.toString() + " stations"
        holder.itemView.setOnClickListener { onClick(country) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<CountryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
