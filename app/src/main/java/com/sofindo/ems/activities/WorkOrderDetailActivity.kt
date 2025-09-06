package com.sofindo.ems.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.dialogs.ImageViewerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_work_order_detail, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_whatsapp -> {
                shareViaWhatsApp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun shareViaWhatsApp() {
        try {
            // Create WhatsApp message with work order details
            val woId = workOrder["nour"]?.toString() ?: "N/A"
            val job = workOrder["job"]?.toString() ?: "No Job Title"
            val location = workOrder["lokasi"]?.toString() ?: "No Location"
            val status = getStatusText(workOrder["status"]?.toString())
            val to = workOrder["woto"]?.toString() ?: "N/A"
            val orderBy = workOrder["orderBy"]?.toString() ?: "Unknown"
            val startTime = formatDateTime(workOrder["mulainya"]?.toString())
            val priority = workOrder["priority"]?.toString() ?: "N/A"
            val spent = calculateTimeSpent(
                workOrder["mulainya"]?.toString(),
                workOrder["timeDone"]?.toString()
            )
            val remarks = workOrder["remarks"]?.toString()
            
            val message = """📋 *Work Order Details*

🆔 WO ID: $woId
🔧 Job: $job
📍 Location: $location
📊 Status: $status
👤 To: $to
📝 Order By: $orderBy
🕐 Start Time: $startTime
⚡ Priority: $priority${if (spent != "N/A" && spent != "-") "\n⏱️ Time Spent: $spent" else ""}${if (!remarks.isNullOrEmpty() && remarks.trim().isNotEmpty()) "\n💬 Remarks: $remarks" else ""}

---
Sent from EMS Work Order App"""
            
            // Check if work order has photos
            val hasPhoto = workOrder["photo"]?.toString()?.isNotEmpty() == true
            val hasPhotoDone = workOrder["photoDone"]?.toString()?.isNotEmpty() == true
            
            if (hasPhoto || hasPhotoDone) {
                // Share with photo
                shareWithPhoto(message)
            } else {
                // Share text only
                shareTextOnly(message)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareWithPhoto(message: String) {
        try {
            // Get photo URL (prefer photoDone if available, otherwise use photo)
            val photoUrl = if (workOrder["photoDone"]?.toString()?.isNotEmpty() == true) {
                "https://emshotels.net/manager/workorder/photo/${workOrder["photoDone"]}"
            } else {
                "https://emshotels.net/manager/workorder/photo/${workOrder["photo"]}"
            }
            
            // Download image and share
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = downloadImage(photoUrl)
                    if (bitmap != null) {
                        // Optimize image for WhatsApp display
                        val optimizedBitmap = optimizeImageForWhatsApp(bitmap)
                        val imageUri = saveImageToCache(optimizedBitmap)
                        
                        // Clean up original bitmap if it was resized
                        if (optimizedBitmap != bitmap) {
                            bitmap.recycle()
                        }
                        
                        withContext(Dispatchers.Main) {
                            shareImageWithText(imageUri, message)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            shareTextOnly(message)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        shareTextOnly(message)
                    }
                }
            }
        } catch (e: Exception) {
            shareTextOnly(message)
        }
    }
    
    private fun shareTextOnly(message: String) {
        try {
            // Encode message for URL
            val encodedMessage = Uri.encode(message)
            
            // Try to open WhatsApp with direct intent
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encodedMessage"))
                startActivity(intent)
            } catch (e: Exception) {
                // If direct method fails, try share intent
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        setPackage("com.whatsapp")
                    }
                    startActivity(shareIntent)
                } catch (e2: Exception) {
                    // If WhatsApp specific fails, show general share chooser
                    try {
                        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }, "Share Work Order Details")
                        startActivity(chooser)
                    } catch (e3: Exception) {
                        Toast.makeText(this, "No app available to share", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareImageWithText(imageUri: Uri, message: String) {
        try {
            // Method 1: Try WhatsApp Business API for better image display
            try {
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, message)
                    putExtra(Intent.EXTRA_SUBJECT, "Work Order Details")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp.w4b") // WhatsApp Business
                }
                startActivity(whatsappIntent)
                return
            } catch (e: Exception) {
                // WhatsApp Business not available, try regular WhatsApp
            }
            
            // Method 2: Try regular WhatsApp
            try {
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, message)
                    putExtra(Intent.EXTRA_SUBJECT, "Work Order Details")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                startActivity(whatsappIntent)
                return
            } catch (e: Exception) {
                // Regular WhatsApp not available, try general share
            }
            
            // Method 3: General share chooser
            val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_SUBJECT, "Work Order Details")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share Work Order Details with Photo")
            startActivity(chooser)
            
        } catch (e: Exception) {
            Toast.makeText(this, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun downloadImage(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun saveImageToCache(bitmap: Bitmap): Uri {
        val imagesFolder = File(cacheDir, "images")
        imagesFolder.mkdirs()
        
        // Generate unique filename to avoid conflicts
        val timestamp = System.currentTimeMillis()
        val file = File(imagesFolder, "wo_share_${timestamp}.jpg")
        
        val stream = FileOutputStream(file)
        // Use higher quality for better WhatsApp display
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        stream.close()
        
        return androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
    }
    
    private fun optimizeImageForWhatsApp(bitmap: Bitmap): Bitmap {
        // WhatsApp works better with certain image dimensions
        // Try to maintain aspect ratio while optimizing for WhatsApp display
        val maxWidth = 1920
        val maxHeight = 1080
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Calculate new dimensions maintaining aspect ratio
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return if (ratio < 1.0f) {
            // Only resize if image is larger than max dimensions
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
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
