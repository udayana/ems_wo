package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.Project
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ProjectAdapter(
    private val projects: List<Project>,
    private val onItemClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateOutputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateAltFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemContainer: LinearLayout = itemView.findViewById(R.id.item_container)
        val tvProjectName: TextView = itemView.findViewById(R.id.tv_project_name)
        val llLocation: LinearLayout = itemView.findViewById(R.id.ll_location)
        val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        val llCreatedDate: LinearLayout = itemView.findViewById(R.id.ll_created_date)
        val tvCreatedDate: TextView = itemView.findViewById(R.id.tv_created_date)
        val tvMaterialCost: TextView = itemView.findViewById(R.id.tv_material_cost)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= projects.size) {
            return
        }
        
        val project = projects[position]
        val context = holder.itemView.context

        // Alternating background color (iOS style: #e1f5f7 for even index)
        if (position % 2 == 0) {
            holder.itemContainer.setBackgroundColor(
                ContextCompat.getColor(context, R.color.project_row_even)
            )
        } else {
            holder.itemContainer.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.transparent)
            )
        }

        // Project Name with number (1, 2, 3, etc.)
        val projectNumber = (position + 1).toString()
        holder.tvProjectName.text = "$projectNumber. ${project.projectName}"

        // Location (with "-" prefix, no label)
        if (!project.lokasi.isNullOrEmpty()) {
            holder.llLocation.visibility = View.VISIBLE
            holder.tvLocation.text = project.lokasi
        } else {
            holder.llLocation.visibility = View.GONE
        }

        // Created Date (format: dd MMM yyyy, no time)
        if (!project.createdDate.isNullOrEmpty()) {
            holder.llCreatedDate.visibility = View.VISIBLE
            holder.tvCreatedDate.text = formatDate(project.createdDate)
        } else {
            holder.llCreatedDate.visibility = View.GONE
        }

        // Material Cost (format: "Cost Rp X" or "Cost Rp 0")
        val cost = project.totalMaterialCost ?: 0.0
        if (cost > 0) {
            holder.tvMaterialCost.text = "Cost ${formatCurrency(cost)}"
            holder.tvMaterialCost.setTextColor(
                ContextCompat.getColor(context, R.color.primary_color)
            )
        } else {
            holder.tvMaterialCost.text = "Cost Rp 0"
            holder.tvMaterialCost.setTextColor(
                ContextCompat.getColor(context, android.R.color.darker_gray)
            )
        }

        // View Button click listener
        holder.btnView.setOnClickListener {
            onItemClick(project)
        }

        // Also allow clicking on the whole item
        holder.itemView.setOnClickListener {
            onItemClick(project)
        }
    }

    override fun getItemCount(): Int {
        return try {
            projects.size
        } catch (e: Exception) {
            0
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            // Try main format first
            val date = dateFormat.parse(dateString)
            if (date != null) {
                dateOutputFormat.format(date)
            } else {
                // Try alternative format
                val altDate = dateAltFormat.parse(dateString)
                if (altDate != null) {
                    dateOutputFormat.format(altDate)
                } else {
                    dateString
                }
            }
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        val formatted = formatter.format(amount)
        // Replace "Rp" with "Rp " (with space) if needed, or just return as is
        return formatted.replace("Rp", "Rp ").replace("  ", " ")
    }
}
