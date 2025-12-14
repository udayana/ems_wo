package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R

class AssetSearchAdapter(
    private val assets: List<Map<String, Any>>,
    private val onItemClick: (Map<String, Any>) -> Unit
) : RecyclerView.Adapter<AssetSearchAdapter.AssetViewHolder>() {

    class AssetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProperty: TextView = itemView.findViewById(R.id.tv_property)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_asset_search, parent, false)
        return AssetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        if (position < 0 || position >= assets.size) {
            return
        }
        
        val asset = assets[position]
        val property = asset["Property"]?.toString() ?: ""
        
        holder.tvProperty.text = property
        
        holder.itemView.setOnClickListener {
            onItemClick(asset)
        }
    }

    override fun getItemCount() = assets.size
}

















