package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.HeatPumpRecord
import java.text.SimpleDateFormat
import java.util.*

class HeatPumpRecordAdapter(
    private val records: List<HeatPumpRecord>,
    private val minTemp: Double? = null
) : RecyclerView.Adapter<HeatPumpRecordAdapter.ViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
    
    // Helper function to convert string to double, handling both . and , as decimal separator
    private fun flexibleToDouble(raw: String): Double? {
        if (raw.isEmpty()) return null
        val normalized = raw.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_heat_pump_record, parent, false)
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
        private val tvWaterIn: TextView = itemView.findViewById(R.id.tv_water_in)
        private val tvWaterOut: TextView = itemView.findViewById(R.id.tv_water_out)
        private val tvHighPress: TextView = itemView.findViewById(R.id.tv_high_press)
        private val tvLowPress: TextView = itemView.findViewById(R.id.tv_low_press)
        private val tvAmp: TextView = itemView.findViewById(R.id.tv_amp)
        private val tvVolt: TextView = itemView.findViewById(R.id.tv_volt)
        private val tvRecBy: TextView = itemView.findViewById(R.id.tv_rec_by)
        
        fun bind(record: HeatPumpRecord) {
            tvDate.text = dateFormat.format(record.date)
            
            // Check if record is empty
            val isEmptyRecord = record.waterIn.isEmpty() && 
                               record.waterOut.isEmpty() && 
                               record.highPress.isEmpty() && 
                               record.lowPress.isEmpty() &&
                               record.amp.isEmpty() &&
                               record.volt.isEmpty() &&
                               record.recBy.isEmpty()
            
            if (isEmptyRecord) {
                tvWaterIn.text = "-"
                tvWaterOut.text = "-"
                tvHighPress.text = "-"
                tvLowPress.text = "-"
                tvAmp.text = "-"
                tvVolt.text = "-"
                tvRecBy.text = "-"
                
                // Style empty records
                itemView.setBackgroundColor(
                    itemView.context.getColor(android.R.color.white)
                )
                tvDate.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvWaterIn.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvWaterOut.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvHighPress.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvLowPress.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvAmp.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvVolt.setTextColor(itemView.context.getColor(R.color.text_secondary))
                tvRecBy.setTextColor(itemView.context.getColor(R.color.text_secondary))
            } else {
                tvWaterIn.text = if (record.waterIn.isEmpty() || record.waterIn == "0" || record.waterIn == "0.00") {
                    "-"
                } else {
                    record.waterIn
                }
                
                // Water Out with color logic based on diff = waterOut - minTemp
                val waterOutValue = if (record.waterOut.isEmpty() || record.waterOut == "0" || record.waterOut == "0.00") {
                    "-"
                } else {
                    record.waterOut
                }
                tvWaterOut.text = waterOutValue
                
                // Apply color to Water Out based on diff logic
                if (waterOutValue != "-" && minTemp != null) {
                    val waterOutDouble = flexibleToDouble(record.waterOut)
                    if (waterOutDouble != null) {
                        val diff = waterOutDouble - minTemp
                        val color = when {
                            diff > 2 -> ContextCompat.getColor(itemView.context, R.color.red)
                            1 <= diff && diff <= 2 -> ContextCompat.getColor(itemView.context, R.color.orange)
                            else -> ContextCompat.getColor(itemView.context, R.color.on_surface)
                        }
                        tvWaterOut.setTextColor(color)
                    } else {
                        tvWaterOut.setTextColor(ContextCompat.getColor(itemView.context, R.color.on_surface))
                    }
                } else {
                    tvWaterOut.setTextColor(ContextCompat.getColor(itemView.context, R.color.on_surface))
                }
                tvHighPress.text = if (record.highPress.isEmpty() || record.highPress == "0" || record.highPress == "0.00") {
                    "-"
                } else {
                    record.highPress
                }
                tvLowPress.text = if (record.lowPress.isEmpty() || record.lowPress == "0" || record.lowPress == "0.00") {
                    "-"
                } else {
                    record.lowPress
                }
                tvAmp.text = if (record.amp.isEmpty() || record.amp == "0" || record.amp == "0.00") {
                    "-"
                } else {
                    record.amp
                }
                tvVolt.text = if (record.volt.isEmpty() || record.volt == "0" || record.volt == "0.00") {
                    "-"
                } else {
                    record.volt
                }
                tvRecBy.text = if (record.recBy.isEmpty()) "-" else record.recBy
                
                // Style filled records
                itemView.setBackgroundColor(
                    itemView.context.getColor(android.R.color.white)
                )
                tvDate.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvWaterIn.setTextColor(itemView.context.getColor(R.color.on_surface))
                // tvWaterOut color is set above based on diff logic
                tvHighPress.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvLowPress.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvAmp.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvVolt.setTextColor(itemView.context.getColor(R.color.on_surface))
                tvRecBy.setTextColor(itemView.context.getColor(R.color.on_surface))
                
                // Make waterIn bold (like Swift)
                tvWaterIn.typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
            }
        }
    }
}

