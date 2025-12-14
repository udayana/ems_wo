package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.Maintenance

class MaintenanceAdapter(
    private val maintenanceList: List<Maintenance>
) : RecyclerView.Adapter<MaintenanceAdapter.MaintenanceViewHolder>() {

    class MaintenanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
        val statusIndicator: android.widget.ImageView = itemView.findViewById(R.id.status_indicator)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MaintenanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance, parent, false)
        return MaintenanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: MaintenanceViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= maintenanceList.size) {
            return
        }
        
        val maintenance = maintenanceList[position]
        
        holder.tvDate.text = maintenance.formattedDate
        holder.tvTitle.text = maintenance.title
        
        if (maintenance.description.isNotEmpty()) {
            holder.tvDescription.text = maintenance.description
            holder.tvDescription.visibility = View.VISIBLE
        } else {
            holder.tvDescription.visibility = View.GONE
        }
        
        // Set status indicator and status text based on maintenance status
        val status = maintenance.status.lowercase()
        val isCompleted = status == "done" || status == "completed"
        
        // Set status indicator and text
        when (status) {
            "done", "completed" -> {
                holder.tvStatus.text = "DONE"
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.status_done))
                holder.statusIndicator.setImageResource(R.drawable.ic_status_done)
                holder.statusIndicator.setColorFilter(holder.itemView.context.getColor(R.color.status_done))
            }
            "pending" -> {
                holder.tvStatus.text = "PENDING"
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.status_pending))
                holder.statusIndicator.setImageResource(R.drawable.ic_status_pending)
                holder.statusIndicator.setColorFilter(holder.itemView.context.getColor(R.color.status_pending))
            }
            "cancelled" -> {
                holder.tvStatus.text = "CANCELLED"
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.status_cancelled))
                holder.statusIndicator.setImageResource(R.drawable.ic_status_cancelled)
                holder.statusIndicator.setColorFilter(holder.itemView.context.getColor(R.color.status_cancelled))
            }
            else -> {
                holder.tvStatus.text = status.uppercase()
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.on_surface_variant))
                holder.statusIndicator.setImageResource(R.drawable.ic_status_pending)
                holder.statusIndicator.setColorFilter(holder.itemView.context.getColor(R.color.on_surface_variant))
            }
        }
        
        // Set background color alternating
        val backgroundColor = if (position % 2 == 0) {
            holder.itemView.context.getColor(R.color.surface)
        } else {
            holder.itemView.context.getColor(R.color.surface_container_highest)
        }
        holder.itemView.setBackgroundColor(backgroundColor)
        
        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false
    }

    override fun getItemCount(): Int {
        return try {
            maintenanceList.size
        } catch (e: Exception) {
            0
        }
    }
}
