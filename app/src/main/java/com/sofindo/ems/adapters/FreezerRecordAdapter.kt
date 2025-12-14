package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.FreezerRecord
import java.text.SimpleDateFormat
import java.util.*

class FreezerRecordAdapter(
    private val records: List<FreezerRecord>,
    private val onDeleteClick: (FreezerRecord) -> Unit,
    private val maxTemp: Double = 0.0
) : RecyclerView.Adapter<FreezerRecordAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val tempText: TextView = itemView.findViewById(R.id.temp_text)
        val diffText: TextView = itemView.findViewById(R.id.diff_text)
        val upDownText: TextView = itemView.findViewById(R.id.up_down_text)
        val recByText: TextView = itemView.findViewById(R.id.rec_by_text)
        val deleteButton: View = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_freezer_record, parent, false)
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
        
        // Format temperature record
        val tempDisplay = when {
            record.tempRecord.isEmpty() || record.tempRecord == "0" || record.tempRecord == "0.00" -> "-"
            else -> record.tempRecord
        }
        holder.tempText.text = tempDisplay
        
        // Set temperature color based on maxTemp
        if (maxTemp > 0 && tempDisplay != "-") {
            val temp = tempDisplay.toDoubleOrNull() ?: 0.0
            if (temp >= maxTemp) {
                holder.tempText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
            } else {
                holder.tempText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
            }
        } else {
            holder.tempText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
        }
        
        // Format diff
        val diffDisplay = when {
            record.tempRecord.isEmpty() || record.tempRecord == "0" || record.tempRecord == "0.00" -> "-"
            record.diff.isEmpty() || record.diff == "0" || record.diff == "0.00" -> "-"
            else -> record.diff
        }
        holder.diffText.text = diffDisplay
        
        // Format up/down
        val upDownDisplay = when {
            record.tempRecord.isEmpty() || record.tempRecord == "0" || record.tempRecord == "0.00" -> "-"
            record.upDown.isEmpty() || record.upDown == "0" -> "-"
            else -> record.upDown
        }
        holder.upDownText.text = upDownDisplay
        
        // Format rec by
        holder.recByText.text = if (record.recBy.isEmpty() || record.recBy == "0") "-" else record.recBy
        
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
