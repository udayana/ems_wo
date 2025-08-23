package com.sofindo.ems.adapter

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.WorkOrder
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

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
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWoId: TextView = itemView.findViewById(R.id.tv_wo_id)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvJobTitle: TextView = itemView.findViewById(R.id.tv_job_title)
        private val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvEngineer: TextView = itemView.findViewById(R.id.tv_engineer)
        private val tvPriority: TextView = itemView.findViewById(R.id.tv_priority)
        private val tvRemarks: TextView = itemView.findViewById(R.id.tv_remarks)
        private val llRemarks: LinearLayout = itemView.findViewById(R.id.ll_remarks)
        private val ivPhotoBefore: ImageView = itemView.findViewById(R.id.iv_photo_before)
        private val ivPhotoAfter: ImageView = itemView.findViewById(R.id.iv_photo_after)
        private val tvBeforeLabel: TextView = itemView.findViewById(R.id.tv_before_label)
        private val llAfterImage: LinearLayout = itemView.findViewById(R.id.ll_after_image)

        fun bind(workOrder: WorkOrder, position: Int) {
            // Set alternating background
            setAlternatingBackground(position)
            
            // Basic info
            tvWoId.text = "WO: ${workOrder.nour ?: "N/A"}"
            tvStatus.text = workOrder.displayStatus
            tvJobTitle.text = workOrder.job ?: workOrder.detail ?: "No Job Title"
            tvLocation.text = workOrder.displayLocation
            tvDate.text = formatDate(workOrder.mulainya ?: workOrder.dateCreate)
            tvEngineer.text = workOrder.displayOrderBy
            
            // Priority
            val priority = workOrder.priority?.uppercase() ?: "LOW"
            tvPriority.text = priority
            
            // Remarks
            if (!workOrder.remark.isNullOrEmpty()) {
                llRemarks.visibility = View.VISIBLE
                tvRemarks.text = workOrder.remark
            } else {
                llRemarks.visibility = View.GONE
            }
            
            // Set status badge color
            val statusBackground = GradientDrawable()
            statusBackground.shape = GradientDrawable.RECTANGLE
            statusBackground.cornerRadius = 6f
            statusBackground.setColor(Color.parseColor(workOrder.statusColor))
            tvStatus.background = statusBackground
            
            // Set priority badge color
            val priorityBackground = GradientDrawable()
            priorityBackground.shape = GradientDrawable.RECTANGLE
            priorityBackground.cornerRadius = 6f
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
        
        private fun setAlternatingBackground(position: Int) {
            val isEven = position % 2 == 0
            val backgroundColor = if (isEven) {
                Color.parseColor("#F8F9FA") // Light gray for even
            } else {
                Color.parseColor("#FFFFFF") // White for odd
            }
            
            val background = GradientDrawable()
            background.shape = GradientDrawable.RECTANGLE
            background.cornerRadius = 12f
            background.setColor(backgroundColor)
            background.setStroke(1, Color.parseColor("#E5E7EB"))
            
            itemView.background = background
        }
        
        private fun setupImages(workOrder: WorkOrder) {
            // Debug: Log image data
            android.util.Log.d("WorkOrderAdapter", "Photo before: ${workOrder.photo}")
            android.util.Log.d("WorkOrderAdapter", "Photo after: ${workOrder.photoDone}")
            android.util.Log.d("WorkOrderAdapter", "Photo before URL: ${workOrder.photoBeforeUrl}")
            
            // Before image - with click listener for fullscreen
            if (workOrder.hasPhotoBefore) {
                Picasso.get()
                    .load(workOrder.photoBeforeUrl ?: "")
                    .into(ivPhotoBefore)
                
                // Add click listener for fullscreen view
                ivPhotoBefore.setOnClickListener {
                    workOrder.photoBeforeUrl?.let { url ->
                        showFullscreenImage(itemView.context, url, "Before")
                    }
                }
            } else {
                ivPhotoBefore.setImageResource(R.drawable.ic_photo)
                ivPhotoBefore.setOnClickListener(null)
            }
            
            // After image - with click listener for fullscreen
            if (workOrder.hasPhotoAfter) {
                llAfterImage.visibility = View.VISIBLE
                tvBeforeLabel.visibility = View.VISIBLE
                
                Picasso.get()
                    .load(workOrder.photoAfterUrl ?: "")
                    .into(ivPhotoAfter)
                
                // Add click listener for fullscreen view
                ivPhotoAfter.setOnClickListener {
                    workOrder.photoAfterUrl?.let { url ->
                        showFullscreenImage(itemView.context, url, "After")
                    }
                }
            } else {
                llAfterImage.visibility = View.GONE
                tvBeforeLabel.visibility = View.GONE
                ivPhotoAfter.setOnClickListener(null)
            }
        }
        
        private fun showFullscreenImage(context: Context, imageUrl: String, imageType: String) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(true)
            
            // Set dialog to fullscreen
            dialog.window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            
            // Inflate the layout
            val layout = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_image, null)
            
            // Set title
            val titleText = layout.findViewById<TextView>(R.id.tv_title)
            titleText.text = "$imageType Image"
            
            // Set close button
            val closeButton = layout.findViewById<TextView>(R.id.btn_close)
            closeButton.setOnClickListener { dialog.dismiss() }
            
            // Set fullscreen image
            val fullscreenImage = layout.findViewById<ImageView>(R.id.iv_fullscreen_image)
            fullscreenImage.setOnClickListener { dialog.dismiss() }
            
            // Load image with Picasso
            Picasso.get()
                .load(imageUrl)
                .into(fullscreenImage)
            
            dialog.setContentView(layout)
            dialog.show()
        }
        
        private fun formatDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "N/A"
            if (dateString == "0000-00-00" || dateString.contains("0000-00-00")) {
                return "-"
            }
            
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateString
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
