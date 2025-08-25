package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import java.text.SimpleDateFormat
import java.util.*

class WorkOrderAdapter(
    private var workOrders: List<Map<String, Any>> = emptyList(),
    private val onItemClick: (Map<String, Any>) -> Unit = {},
    private val onEditClick: (Map<String, Any>) -> Unit = {},
    private val onDeleteClick: (Map<String, Any>) -> Unit = {},
    private val onDetailClick: (Map<String, Any>) -> Unit = {},
    private val onFollowUpClick: (Map<String, Any>) -> Unit = {},
    private val showSender: Boolean = false,
    private val replaceWotoWithOrderBy: Boolean = false,
    private val isHomeFragment: Boolean = false
) : RecyclerView.Adapter<WorkOrderAdapter.WorkOrderViewHolder>() {

    class WorkOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Image
        val ivWorkOrderImage: ImageView = itemView.findViewById(R.id.iv_work_order_image)
        
        // Content
        val tvWoNumber: TextView = itemView.findViewById(R.id.tv_wo_number)
        val tvWoStatus: TextView = itemView.findViewById(R.id.tv_wo_status)
        val tvJobDescription: TextView = itemView.findViewById(R.id.tv_job_description)
        val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        val tvDepartment: TextView = itemView.findViewById(R.id.tv_department)
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvPriority: TextView = itemView.findViewById(R.id.tv_priority)
        
        // Menu button (hidden but kept for reference)
        val btnMenu: android.widget.ImageButton = itemView.findViewById(R.id.btn_menu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkOrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_order, parent, false)
        return WorkOrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkOrderViewHolder, position: Int) {
        val workOrder = workOrders[position]
        val context = holder.itemView.context
        
        // Bind WO Number
        holder.tvWoNumber.text = "WO: ${workOrder["nour"] ?: "N/A"}"
        
        // Bind Job Description
        holder.tvJobDescription.text = workOrder["job"]?.toString() ?: "No Job Title"
        
        // Bind Location
        holder.tvLocation.text = workOrder["lokasi"]?.toString() ?: "No Location"
        
        // Bind right-side label: either woto (department) or orderBy (sender)
        val orderByValue = workOrder["orderBy"]?.toString() ?: workOrder["orderby"]?.toString()
        val wotoValue = workOrder["woto"]?.toString()
        if (replaceWotoWithOrderBy) {
            holder.tvDepartment.text = "by: ${orderByValue ?: "Unknown"}"
        } else {
            holder.tvDepartment.text = wotoValue ?: "Unknown"
        }
        
        // Bind Date
        holder.tvDate.text = formatDateInline(workOrder["mulainya"]?.toString())
        
        // Bind Priority
        val priority = workOrder["priority"]?.toString() ?: ""
        holder.tvPriority.text = getPriorityText(priority)
        
        // Bind Status
        val status = workOrder["status"]?.toString() ?: ""
        val statusText = if (status.isEmpty()) "NEW" else status.uppercase()
        holder.tvWoStatus.text = statusText
        
        // Set status color and background
        val statusColor = getStatusColor(status)
        holder.tvWoStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(statusColor)
        
        // Handle image (sama seperti Flutter)
        val photoUrl = workOrder["photo"]?.toString()
        
        if (!photoUrl.isNullOrEmpty()) {
            val imageUrl = "https://emshotels.net/manager/workorder/photo/$photoUrl"
            Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_photo)
                .error(R.drawable.ic_photo)
                .into(holder.ivWorkOrderImage)
        } else {
            holder.ivWorkOrderImage.setImageResource(R.drawable.ic_photo)
        }
        
        // Set click listener for image
        holder.ivWorkOrderImage.setOnClickListener {
            val photoUrl = workOrder["photo"]?.toString()
            if (!photoUrl.isNullOrEmpty()) {
                val imageUrl = "https://emshotels.net/manager/workorder/photo/$photoUrl"
                val dialog = com.sofindo.ems.dialogs.ImageViewerDialog(context, imageUrl)
                dialog.show()
            }
        }
        
        // Hide the menu button since we're using card click instead
        holder.btnMenu.visibility = View.GONE
        
        // Set click listener for entire card to show popup menu
        holder.itemView.setOnClickListener {
            showPopupMenu(holder.itemView, workOrder)
        }
    }

    override fun getItemCount(): Int = workOrders.size

    fun updateData(newWorkOrders: List<Map<String, Any>>) {
        workOrders = newWorkOrders
        notifyDataSetChanged()
    }
    
    private fun showPopupMenu(view: View, workOrder: Map<String, Any>) {
        val popupMenu = android.widget.PopupMenu(view.context, view)
        
        // Use different menu based on fragment type
        if (isHomeFragment) {
            popupMenu.menuInflater.inflate(R.menu.work_order_home_menu, popupMenu.menu)
        } else {
            popupMenu.menuInflater.inflate(R.menu.work_order_menu, popupMenu.menu)
        }
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_detail -> {
                    onDetailClick(workOrder)
                    true
                }
                R.id.action_edit -> {
                    onEditClick(workOrder)
                    true
                }
                R.id.action_delete -> {
                    onDeleteClick(workOrder)
                    true
                }
                R.id.action_follow_up -> {
                    onFollowUpClick(workOrder)
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    // Helper functions (sama persis dengan Flutter)
    private fun getPriorityText(priority: String): String {
        return if (priority.isEmpty()) "LOW" else priority.uppercase()
    }
    
    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "done" -> android.graphics.Color.parseColor("#4CAF50")
            "pending" -> android.graphics.Color.parseColor("#FFC107")
            "on progress" -> android.graphics.Color.parseColor("#FF9800")
            "received" -> android.graphics.Color.parseColor("#2196F3")
            "" -> android.graphics.Color.parseColor("#9E9E9E")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
    }
    
    private fun formatDateInline(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrEmpty()) return "N/A"
        if (dateTimeStr == "0000-00-00" || dateTimeStr.contains("0000-00-00")) {
            return "-"
        }

        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateTimeStr)
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateTimeStr
        }
    }
}

