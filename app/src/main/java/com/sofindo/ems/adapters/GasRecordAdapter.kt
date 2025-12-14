package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.GasRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class GasRecordAdapter(
    private val records: List<GasRecord>,
    private val onDeleteClick: (GasRecord) -> Unit
) : RecyclerView.Adapter<GasRecordAdapter.ViewHolder>() {

    private var lastClickTime = 0L
    private val doubleClickDelay = 500L

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val newGasText: TextView = itemView.findViewById(R.id.new_gas_text)
        val totalGasText: TextView = itemView.findViewById(R.id.total_gas_text)
        val totalCostText: TextView = itemView.findViewById(R.id.total_cost_text)
        val recByText: TextView = itemView.findViewById(R.id.rec_by_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gas_record, parent, false)
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
        
        // Format new gas
        val newGasDisplay = when {
            record.newGas.isEmpty() || record.newGas == "0" -> "-"
            else -> "${record.newGas} tabung"
        }
        holder.newGasText.text = newGasDisplay
        
        // Format total gas
        val totalGasDisplay = when {
            record.newGas.isEmpty() || record.newGas == "0" -> "-"
            else -> "${record.totalGas} tabung"
        }
        holder.totalGasText.text = totalGasDisplay
        
        // Format total cost (without Rp. prefix)
        val totalCostDisplay = when {
            record.totalCost.isEmpty() || record.totalCost == "0" || record.totalCost == "0.00" -> "-"
            else -> {
                val cost = record.totalCost.toDoubleOrNull() ?: 0.0
                formatNumber(cost)
            }
        }
        holder.totalCostText.text = totalCostDisplay
        
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



