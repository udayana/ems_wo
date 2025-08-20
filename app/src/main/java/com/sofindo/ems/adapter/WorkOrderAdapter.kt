package com.sofindo.ems.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.WorkOrder
import com.squareup.picasso.Picasso

class WorkOrderAdapter(
    private val onItemClick: (WorkOrder) -> Unit,
    private val onChangeStatusClick: (WorkOrder) -> Unit
) : ListAdapter<WorkOrder, WorkOrderAdapter.ViewHolder>(WorkOrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWoId: TextView = itemView.findViewById(R.id.tv_wo_id)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvJobTitle: TextView = itemView.findViewById(R.id.tv_job_title)
        private val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvEngineer: TextView = itemView.findViewById(R.id.tv_engineer)
        private val tvPriority: TextView = itemView.findViewById(R.id.tv_priority)
        private val ivPhotoBefore: ImageView = itemView.findViewById(R.id.iv_photo_before)
        private val ivPhotoAfter: ImageView = itemView.findViewById(R.id.iv_photo_after)
        private val tvBeforeLabel: TextView = itemView.findViewById(R.id.tv_before_label)
        private val llAfterImage: LinearLayout = itemView.findViewById(R.id.ll_after_image)

        fun bind(workOrder: WorkOrder) {
            // Basic info
            tvWoId.text = "WO: ${workOrder.nour ?: "N/A"}"
            tvStatus.text = workOrder.displayStatus
            tvJobTitle.text = workOrder.job ?: workOrder.detail ?: "No Job Title"
            tvLocation.text = workOrder.displayLocation
            tvDate.text = workOrder.dateCreate ?: ""
            tvEngineer.text = workOrder.displayEngineer
            
            // Priority
            val priority = workOrder.priority?.uppercase() ?: "NORMAL"
            tvPriority.text = priority
            
            // Set status badge color
            val statusBackground = GradientDrawable()
            statusBackground.shape = GradientDrawable.RECTANGLE
            statusBackground.cornerRadius = 8f
            statusBackground.setColor(Color.parseColor(workOrder.statusColor))
            tvStatus.background = statusBackground
            
            // Set priority badge color
            val priorityBackground = GradientDrawable()
            priorityBackground.shape = GradientDrawable.RECTANGLE
            priorityBackground.cornerRadius = 8f
            priorityBackground.setColor(Color.parseColor(workOrder.priorityColor))
            tvPriority.background = priorityBackground
            
            // Handle images
            setupImages(workOrder)
            
            // Click listeners
            itemView.setOnClickListener {
                onItemClick(workOrder)
            }
            
            itemView.setOnLongClickListener {
                onChangeStatusClick(workOrder)
                true
            }
        }
        
        private fun setupImages(workOrder: WorkOrder) {
            // Debug: Log image data
            android.util.Log.d("WorkOrderAdapter", "Photo before: ${workOrder.photo}")
            android.util.Log.d("WorkOrderAdapter", "Photo after: ${workOrder.photoDone}")
            android.util.Log.d("WorkOrderAdapter", "Photo before URL: ${workOrder.photoBeforeUrl}")
            
            // Before image
            if (workOrder.hasPhotoBefore) {
                Picasso.get()
                    .load(workOrder.photoBeforeUrl)
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .into(ivPhotoBefore)
            } else {
                ivPhotoBefore.setImageResource(R.drawable.ic_photo)
            }
            
            // After image
            if (workOrder.hasPhotoAfter) {
                llAfterImage.visibility = View.VISIBLE
                tvBeforeLabel.visibility = View.VISIBLE
                
                Picasso.get()
                    .load(workOrder.photoAfterUrl)
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .into(ivPhotoAfter)
            } else {
                llAfterImage.visibility = View.GONE
                tvBeforeLabel.visibility = View.GONE
            }
        }
    }

    class WorkOrderDiffCallback : DiffUtil.ItemCallback<WorkOrder>() {
        override fun areItemsTheSame(oldItem: WorkOrder, newItem: WorkOrder): Boolean {
            return oldItem.woId == newItem.woId
        }

        override fun areContentsTheSame(oldItem: WorkOrder, newItem: WorkOrder): Boolean {
            return oldItem == newItem
        }
    }
}
