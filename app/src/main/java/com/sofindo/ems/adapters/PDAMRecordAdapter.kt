package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.PDAMRecord
import java.text.SimpleDateFormat
import java.util.*

class PDAMRecordAdapter(
    private val records: List<PDAMRecord>,
    private val onDeleteClick: (PDAMRecord) -> Unit
) : RecyclerView.Adapter<PDAMRecordAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val meterText: TextView = itemView.findViewById(R.id.meter_text)
        val consumeText: TextView = itemView.findViewById(R.id.consume_text)
        val costText: TextView = itemView.findViewById(R.id.cost_text)
        val recByText: TextView = itemView.findViewById(R.id.rec_by_text)
        val deleteButton: View = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdam_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= records.size) {
            return
        }
        val record = records[position]
        
        // Format date
        val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
        holder.dateText.text = dateFormat.format(record.date)
        
        // Format meter record
        holder.meterText.text = if (record.meterRecord.isEmpty() || record.meterRecord == "0") "-" else record.meterRecord
        
        // Format consume
        holder.consumeText.text = if (record.consume.isEmpty() || record.consume == "0") "-" else record.consume
        
        // Format cost
        holder.costText.text = if (record.estimateCost.isEmpty() || record.estimateCost == "0") "-" else record.estimateCost
        
        // Format rec by
        holder.recByText.text = if (record.recBy.isEmpty()) "-" else record.recBy
        
        // Double-click to delete
        var lastClickTime = 0L
        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) { // 500ms double click threshold
                onDeleteClick(record)
            }
            lastClickTime = currentTime
        }
        
        // Hide delete button
        holder.deleteButton.visibility = View.GONE
    }

    override fun getItemCount(): Int {
        return try {
            records.size
        } catch (e: Exception) {
            0
        }
    }
}
