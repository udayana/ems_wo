package com.sofindo.ems.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.dialogs.ImageViewerDialog
import java.text.SimpleDateFormat
import java.util.*

class WorkOrderDetailActivity : AppCompatActivity() {
    
    private lateinit var workOrder: Map<String, Any>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_order_detail)
        
        // Get work order data from intent
        @Suppress("UNCHECKED_CAST")
        workOrder = intent.getSerializableExtra("workOrder") as? Map<String, Any> ?: emptyMap()
        
        setupToolbar()
        populateWorkOrderDetails()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Detail Work Order"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }
    
    private fun populateWorkOrderDetails() {
        // WO ID
        findViewById<TextView>(R.id.tv_wo_id_value).text = workOrder["nour"]?.toString() ?: "N/A"
        
        // Job
        findViewById<TextView>(R.id.tv_job_value).text = workOrder["job"]?.toString() ?: "No Job Title"
        
        // Location
        findViewById<TextView>(R.id.tv_location_value).text = workOrder["lokasi"]?.toString() ?: "No Location"
        
        // Category
        findViewById<TextView>(R.id.tv_category_value).text = workOrder["category"]?.toString() ?: "N/A"
        
        // Priority
        findViewById<TextView>(R.id.tv_priority_value).text = workOrder["priority"]?.toString() ?: "N/A"
        
        // Dept
        findViewById<TextView>(R.id.tv_dept_value).text = workOrder["dept"]?.toString() ?: "N/A"
        
        // To
        findViewById<TextView>(R.id.tv_to_value).text = workOrder["woto"]?.toString() ?: "N/A"
        
        // Order by
        findViewById<TextView>(R.id.tv_order_by_value).text = workOrder["orderBy"]?.toString() ?: "Unknown"
        
        // Status
        findViewById<TextView>(R.id.tv_status_value).text = getStatusText(workOrder["status"]?.toString())
        
        // Start Time
        findViewById<TextView>(R.id.tv_start_time_value).text = formatDateTime(workOrder["mulainya"]?.toString())
        
        // Accepted
        findViewById<TextView>(R.id.tv_accepted_value).text = formatDateTime(workOrder["timeAccept"]?.toString())
        
        // Done
        findViewById<TextView>(R.id.tv_done_value).text = formatDateTime(workOrder["timeDone"]?.toString())
        
        // Spent
        findViewById<TextView>(R.id.tv_spent_value).text = calculateTimeSpent(
            workOrder["mulainya"]?.toString(),
            workOrder["timeDone"]?.toString()
        )
        
        // Done By
        findViewById<TextView>(R.id.tv_done_by_value).text = workOrder["doneBy"]?.toString() ?: "N/A"
        
        // Remarks (conditional)
        val remarks = workOrder["remarks"]?.toString()
        val remarksContainer = findViewById<View>(R.id.remarks_container)
        if (!remarks.isNullOrEmpty() && remarks.trim().isNotEmpty()) {
            findViewById<TextView>(R.id.tv_remarks_value).text = remarks
            remarksContainer.visibility = View.VISIBLE
        } else {
            remarksContainer.visibility = View.GONE
        }
        
        // Setup photo section
        setupPhotoSection()
    }
    
    private fun setupPhotoSection() {
        val photoContainer = findViewById<View>(R.id.photo_container)
        val hasPhoto = workOrder["photo"]?.toString()?.isNotEmpty() == true
        val hasPhotoDone = workOrder["photoDone"]?.toString()?.isNotEmpty() == true
        
        if (!hasPhoto && !hasPhotoDone) {
            photoContainer.visibility = View.GONE
            return
        }
        
        photoContainer.visibility = View.VISIBLE
        
        if (hasPhoto && !hasPhotoDone) {
            // Single photo (before only)
            setupSinglePhoto("https://emshotels.net/manager/workorder/photo/${workOrder["photo"]}")
        } else if (!hasPhoto && hasPhotoDone) {
            // Single photo (after only)
            setupSinglePhoto("https://emshotels.net/manager/workorder/photo/${workOrder["photoDone"]}")
        } else {
            // Both photos (before and after)
            setupDualPhotos()
        }
    }
    
    private fun setupSinglePhoto(imageUrl: String) {
        val singlePhotoContainer = findViewById<View>(R.id.single_photo_container)
        val dualPhotoContainer = findViewById<View>(R.id.dual_photo_container)
        
        singlePhotoContainer.visibility = View.VISIBLE
        dualPhotoContainer.visibility = View.GONE
        
        val photoImageView = findViewById<ImageView>(R.id.single_photo_image)
        
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_photo)
            .error(R.drawable.ic_photo)
            .into(photoImageView)
        
        photoImageView.setOnClickListener {
            val dialog = ImageViewerDialog(this, imageUrl)
            dialog.show()
        }
    }
    
    private fun setupDualPhotos() {
        val singlePhotoContainer = findViewById<View>(R.id.single_photo_container)
        val dualPhotoContainer = findViewById<View>(R.id.dual_photo_container)
        
        singlePhotoContainer.visibility = View.GONE
        dualPhotoContainer.visibility = View.VISIBLE
        
        val beforeImageView = findViewById<ImageView>(R.id.before_photo_image)
        val afterImageView = findViewById<ImageView>(R.id.after_photo_image)
        
        val beforeUrl = "https://emshotels.net/manager/workorder/photo/${workOrder["photo"]}"
        val afterUrl = "https://emshotels.net/manager/workorder/photo/${workOrder["photoDone"]}"
        
        Glide.with(this)
            .load(beforeUrl)
            .placeholder(R.drawable.ic_photo)
            .error(R.drawable.ic_photo)
            .into(beforeImageView)
        
        Glide.with(this)
            .load(afterUrl)
            .placeholder(R.drawable.ic_photo)
            .error(R.drawable.ic_photo)
            .into(afterImageView)
        
        beforeImageView.setOnClickListener {
            val dialog = ImageViewerDialog(this, beforeUrl, "Before")
            dialog.show()
        }
        
        afterImageView.setOnClickListener {
            val dialog = ImageViewerDialog(this, afterUrl, "After")
            dialog.show()
        }
    }
    
    private fun getStatusText(status: String?): String {
        return if (status.isNullOrEmpty()) "NEW" else status.uppercase()
    }
    
    private fun formatDateTime(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrEmpty()) return "N/A"
        
        // Check if date is "0000-00-00" or similar empty date format
        if (dateTimeStr == "0000-00-00" || 
            dateTimeStr == "0000-00-00 00:00:00" || 
            dateTimeStr.contains("0000-00-00")) {
            return "-"
        }
        
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateTimeStr)
            val outputFormat = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateTimeStr
        }
    }
    
    private fun calculateTimeSpent(startTime: String?, doneTime: String?): String {
        if (startTime.isNullOrEmpty() || doneTime.isNullOrEmpty()) {
            return "N/A"
        }
        
        // Check if done time is "0000-00-00" or similar empty date format
        if (doneTime == "0000-00-00" || 
            doneTime == "0000-00-00 00:00:00" || 
            doneTime.contains("0000-00-00")) {
            return "-"
        }
        
        return try {
            val start = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(startTime)
            val done = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(doneTime)
            
            if (start == null || done == null) {
                return "Invalid date format"
            }
            
            val difference = done.time - start.time
            
            if (difference < 0) {
                return "Invalid time range"
            }
            
            val totalMinutes = difference / (1000 * 60)
            val totalHours = difference / (1000 * 60 * 60)
            val totalDays = difference / (1000 * 60 * 60 * 24)
            val totalMonths = (done.year - start.year) * 12 + (done.month - start.month)
            
            return when {
                totalMonths > 0 -> "$totalMonths month${if (totalMonths > 1) "s" else ""}"
                totalDays > 0 -> "$totalDays day${if (totalDays > 1) "s" else ""}"
                totalHours > 0 -> "$totalHours hour${if (totalHours > 1) "s" else ""}"
                totalMinutes > 0 -> "$totalMinutes minute${if (totalMinutes > 1) "s" else ""}"
                else -> "Less than 1 minute"
            }
        } catch (e: Exception) {
            "Invalid date format"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
