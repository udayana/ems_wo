package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.WaterTankRecord
import java.text.SimpleDateFormat
import java.util.*

class WaterTankRecordAdapter(
    private val records: List<WaterTankRecord>
) : RecyclerView.Adapter<WaterTankRecordAdapter.ViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_water_tank_record, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= records.size) {
            return
        }
        
        val record = records[position]
        holder.bind(record)
    }
    
    override fun getItemCount(): Int {
        return try {
            records.size
        } catch (e: Exception) {
            0
        }
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvNewWater: TextView = itemView.findViewById(R.id.tv_new_water)
        private val tvTotWater: TextView = itemView.findViewById(R.id.tv_tot_water)
        private val tvPriceTotal: TextView = itemView.findViewById(R.id.tv_price_total)
        private val tvRecBy: TextView = itemView.findViewById(R.id.tv_rec_by)
        
        fun bind(record: WaterTankRecord) {
            tvDate.text = dateFormat.format(record.date)
            
            // Check if record is empty
            val isEmptyRecord = record.newWater.isEmpty() && 
                               record.totWater.isEmpty() && 
                               record.priceTotal.isEmpty() && 
                               record.recBy.isEmpty()
            
            if (isEmptyRecord) {
                tvNewWater.text = "-"
                tvTotWater.text = "-"
                tvPriceTotal.text = "-"
                tvRecBy.text = "-"
                
                // Style empty records
                itemView.setBackgroundColor(
                    itemView.context.getColor(android.R.color.white)
                )
                tvDate.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvNewWater.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvTotWater.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvPriceTotal.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvRecBy.setTextColor(itemView.context.getColor(R.color.text_secondary))
            } else {
                tvNewWater.text = if (record.newWater.isEmpty() || record.newWater == "0" || record.newWater == "0.00") {
                    "-"
                } else {
                    "${record.newWater} m³"
                }
                tvTotWater.text = if (record.totWater.isEmpty() || record.totWater == "0" || record.totWater == "0.00") {
                    "-"
                } else {
                    "${record.totWater} m³"
                }
                tvPriceTotal.text = if (record.priceTotal.isEmpty() || record.priceTotal == "0" || record.priceTotal == "0.00") {
                    "-"
                } else {
                    formatIDR(record.priceTotal.toDoubleOrNull() ?: 0.0)
                }
                tvRecBy.text = if (record.recBy.isEmpty()) "-" else record.recBy
                
                // Style filled records
                itemView.setBackgroundColor(
                    itemView.context.getColor(android.R.color.white)
                )
                tvDate.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvNewWater.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvTotWater.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvPriceTotal.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvRecBy.setTextColor(itemView.context.getColor(R.color.on_surface))
                
                // Make newWater bold
                tvNewWater.typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
            }
        }
        
        private fun formatIDR(value: Double): String {
            val formatter = java.text.NumberFormat.getNumberInstance(Locale("id", "ID"))
            formatter.maximumFractionDigits = 0
            return formatter.format(value)
        }
    }
}

