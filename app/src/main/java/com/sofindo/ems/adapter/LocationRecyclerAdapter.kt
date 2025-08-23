package com.sofindo.ems.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.LocationSuggestion

class LocationRecyclerAdapter(
    private val suggestions: List<LocationSuggestion>,
    private val onItemClick: (LocationSuggestion) -> Unit
) : RecyclerView.Adapter<LocationRecyclerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val locationName: TextView = view.findViewById(R.id.tv_location_name)
        val locationInfo: TextView = view.findViewById(R.id.tv_location_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val suggestion = suggestions[position]
        
        holder.locationName.text = suggestion.displayName
        holder.locationInfo.visibility = View.GONE // Hide secondary info
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(suggestion)
        }
        
        // Debug log
        android.util.Log.d("LocationRecyclerAdapter", "Binding item $position: ${suggestion.displayName}")
    }

    override fun getItemCount(): Int {
        val count = suggestions.size
        android.util.Log.d("LocationRecyclerAdapter", "Item count: $count")
        return count
    }
}
