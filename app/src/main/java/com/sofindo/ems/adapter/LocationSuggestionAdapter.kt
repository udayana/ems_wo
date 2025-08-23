package com.sofindo.ems.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.sofindo.ems.R
import com.sofindo.ems.models.LocationSuggestion

class LocationSuggestionAdapter(
    context: Context,
    private val suggestions: List<LocationSuggestion>
) : ArrayAdapter<LocationSuggestion>(context, 0, suggestions) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_location_suggestion, parent, false)

        val suggestion = getItem(position)
        
        val tvLocationName = view.findViewById<TextView>(R.id.tv_location_name)
        val tvLocationInfo = view.findViewById<TextView>(R.id.tv_location_info)

        tvLocationName.text = suggestion?.displayName
        
        // Always hide secondary info since we only want single line
        tvLocationInfo.visibility = View.GONE

        // Debug log
        android.util.Log.d("LocationAdapter", "Creating view for position $position: ${suggestion?.displayName}")

        return view
    }
    
    override fun getCount(): Int {
        val count = suggestions.size
        android.util.Log.d("LocationAdapter", "Adapter count: $count")
        return count
    }
}
