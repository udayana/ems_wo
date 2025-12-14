package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.Property

class PropertySearchAdapter(
    private val properties: MutableList<Property>,
    private val onPropertyClick: (Property) -> Unit
) : RecyclerView.Adapter<PropertySearchAdapter.PropertyViewHolder>() {

    class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPropertyName: TextView = itemView.findViewById(R.id.tv_property_name)
        // Removed: tvPropertyId - no longer needed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_property_search, parent, false)
        return PropertyViewHolder(view)
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        try {
            android.util.Log.d("PropertySearchAdapter", "onBindViewHolder called for position: $position, total: ${properties.size}")
            if (position < 0 || position >= properties.size) {
                android.util.Log.w("PropertySearchAdapter", "⚠️ Invalid position: $position, size: ${properties.size}")
                return
            }
            val property = properties[position]
            android.util.Log.d("PropertySearchAdapter", "Binding property: ${property.nama} (${property.id})")
            
            try {
                holder.tvPropertyName.text = property.nama
            } catch (e: Exception) {
                android.util.Log.e("PropertySearchAdapter", "❌ Error setting property name: ${e.message}")
                e.printStackTrace()
            }
            
            // Removed: Property ID display - no longer needed
            
            holder.itemView.setOnClickListener {
                try {
                    android.util.Log.d("PropertySearchAdapter", "Item clicked at position: $position")
                    if (position >= 0 && position < properties.size) {
                        onPropertyClick(properties[position])
                    } else {
                        android.util.Log.w("PropertySearchAdapter", "⚠️ Invalid position on click: $position")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PropertySearchAdapter", "❌ Click error: ${e.message}", e)
                    e.printStackTrace()
                }
            }
            android.util.Log.d("PropertySearchAdapter", "✅ onBindViewHolder completed for position: $position")
        } catch (e: Exception) {
            android.util.Log.e("PropertySearchAdapter", "❌❌❌ Bind error: ${e.message}", e)
            android.util.Log.e("PropertySearchAdapter", "Error type: ${e.javaClass.simpleName}")
            android.util.Log.e("PropertySearchAdapter", "Stack trace: ${e.stackTraceToString()}")
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return try {
            properties.size
        } catch (e: Exception) {
            0
        }
    }
    
    fun updateList(newList: List<Property>) {
        try {
            android.util.Log.d("PropertySearchAdapter", "updateList called with ${newList.size} items")
            properties.clear()
            properties.addAll(newList)
            android.util.Log.d("PropertySearchAdapter", "Properties updated, calling notifyDataSetChanged...")
            notifyDataSetChanged()
            android.util.Log.d("PropertySearchAdapter", "✅ notifyDataSetChanged completed")
        } catch (e: Exception) {
            android.util.Log.e("PropertySearchAdapter", "❌ Update list error: ${e.message}", e)
            android.util.Log.e("PropertySearchAdapter", "Error type: ${e.javaClass.simpleName}")
            android.util.Log.e("PropertySearchAdapter", "Stack trace: ${e.stackTraceToString()}")
            e.printStackTrace()
        }
    }
}
