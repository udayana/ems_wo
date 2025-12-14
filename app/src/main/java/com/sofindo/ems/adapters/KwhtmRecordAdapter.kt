package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.KwhtmRecord
import java.text.SimpleDateFormat
import java.util.*

class KwhtmRecordAdapter(
    private val records: List<KwhtmRecord>,
    private val onDeleteClick: (KwhtmRecord) -> Unit,
    private val calculateTotalCost: (KwhtmRecord) -> String
) : RecyclerView.Adapter<KwhtmRecordAdapter.KwhtmRecordViewHolder>() {

    private var clickCount = 0
    private var lastClickTime = 0L
    private var lastClickedPosition = -1

    class KwhtmRecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val recLWBPText: TextView = itemView.findViewById(R.id.rec_lwbp_text)
        val recWBPText: TextView = itemView.findViewById(R.id.rec_wbp_text)
        val consumeLWBPText: TextView = itemView.findViewById(R.id.consume_lwbp_text)
        val consumeWBPText: TextView = itemView.findViewById(R.id.consume_wbp_text)
        val totalCostText: TextView = itemView.findViewById(R.id.total_cost_text)
        val recByText: TextView = itemView.findViewById(R.id.rec_by_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KwhtmRecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kwhtm_record, parent, false)
        return KwhtmRecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: KwhtmRecordViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= records.size) {
            return
        }
        val record = records[position]
        val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
        
        holder.dateText.text = dateFormat.format(record.date)
        holder.recLWBPText.text = if (record.recLWBP.isEmpty() || record.recLWBP == "0") "-" else record.recLWBP
        holder.recWBPText.text = if (record.recWBP.isEmpty() || record.recWBP == "0") "-" else record.recWBP
        holder.consumeLWBPText.text = if (record.consumeLWBP.isEmpty() || record.consumeLWBP == "0") "-" else record.consumeLWBP
        holder.consumeWBPText.text = if (record.consumeWBP.isEmpty() || record.consumeWBP == "0") "-" else record.consumeWBP
        holder.totalCostText.text = calculateTotalCost(record)
        holder.recByText.text = if (record.recBy.isEmpty()) "-" else record.recBy

        // Double click to delete
        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            
            if (lastClickedPosition == position && (currentTime - lastClickTime) < 500) {
                // Double click detected
                onDeleteClick(record)
                clickCount = 0
            } else {
                clickCount = 1
                lastClickTime = currentTime
                lastClickedPosition = position
            }
        }
        
        // Long click to delete (alternative)
        holder.itemView.setOnLongClickListener {
            onDeleteClick(record)
            true
        }
    }

    override fun getItemCount(): Int {
        return try {
            records.size
        } catch (e: Exception) {
            0
        }
    }
}
