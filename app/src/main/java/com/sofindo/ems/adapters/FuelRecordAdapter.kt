package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.FuelRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FuelRecordAdapter(
    private val records: List<FuelRecord>,
    private val onDeleteClick: (FuelRecord) -> Unit
) : RecyclerView.Adapter<FuelRecordAdapter.ViewHolder>() {

    private var lastClickTime = 0L
    private val doubleClickDelay = 500L

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val newFuelText: TextView = itemView.findViewById(R.id.new_fuel_text)
        val totalFuelText: TextView = itemView.findViewById(R.id.total_fuel_text)
        val totalCostText: TextView = itemView.findViewById(R.id.total_cost_text)
        val recByText: TextView = itemView.findViewById(R.id.rec_by_text)
        val deleteButton: View = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fuel_record, parent, false)
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
        
        // Format new fuel
        val newFuelDisplay = when {
            record.newFuel.isEmpty() || record.newFuel == "0" || record.newFuel == "0.00" -> "-"
            else -> "${record.newFuel} ltr"
        }
        holder.newFuelText.text = newFuelDisplay
        
        // Format total fuel
        val totalFuelDisplay = when {
            record.totalFuel.isEmpty() || record.totalFuel == "0" || record.totalFuel == "0.00" -> "-"
            else -> "${record.totalFuel} ltr"
        }
        holder.totalFuelText.text = totalFuelDisplay
        
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
        holder.recByText.text = if (record.recBy.isEmpty() || record.recBy == "0") "-" else record.recBy
        
        // Double click to delete
        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < doubleClickDelay) {
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
    
    private fun formatIDR(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
    
    private fun formatNumber(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
}
