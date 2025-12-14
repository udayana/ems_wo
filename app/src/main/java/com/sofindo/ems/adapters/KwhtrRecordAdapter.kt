package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.KwhtrRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class KwhtrRecordAdapter(
    private val records: List<KwhtrRecord>,
    private val onDeleteClick: (KwhtrRecord) -> Unit
) : RecyclerView.Adapter<KwhtrRecordAdapter.ViewHolder>() {

    private var lastClickTime = 0L
    private val doubleClickDelay = 500L

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val meterRecordText: TextView = itemView.findViewById(R.id.meter_record_text)
        val consumeText: TextView = itemView.findViewById(R.id.consume_text)
        val estimateCostText: TextView = itemView.findViewById(R.id.estimate_cost_text)
        val recByText: TextView = itemView.findViewById(R.id.rec_by_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kwhtr_record, parent, false)
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
        val meterRecordDisplay = when {
            record.meterRecord.isEmpty() || record.meterRecord == "0" -> "-"
            else -> record.meterRecord
        }
        holder.meterRecordText.text = meterRecordDisplay
        
        // Format consume
        val consumeDisplay = when {
            record.consume.isEmpty() || record.consume == "0" -> "-"
            else -> record.consume
        }
        holder.consumeText.text = consumeDisplay
        
        // Format estimate cost (without Rp. prefix)
        val estimateCostDisplay = when {
            record.estimateCost.isEmpty() || record.estimateCost == "0" || record.estimateCost == "0.00" -> "-"
            else -> {
                val cost = record.estimateCost.toDoubleOrNull() ?: 0.0
                formatNumber(cost)
            }
        }
        holder.estimateCostText.text = estimateCostDisplay
        
        // Format rec by
        holder.recByText.text = if (record.recBy.isEmpty()) "-" else record.recBy
        
        // Double click to delete
        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < doubleClickDelay) {
                onDeleteClick(record)
            }
            lastClickTime = currentTime
        }
    }

    override fun getItemCount(): Int {
        return try {
            records.size
        } catch (e: Exception) {
            0
        }
    }

    private fun formatNumber(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
}



