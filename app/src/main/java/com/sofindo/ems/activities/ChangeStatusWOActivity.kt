package com.sofindo.ems.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ChangeStatusWOActivity : AppCompatActivity() {
    
    private lateinit var workOrder: Map<String, Any>
    private var selectedStatus: String? = null
    private var userName: String = ""
    private var isLoading = false
    
    // Status list matching Flutter implementation
    private val statusList = listOf("received", "on progress", "pending", "done")
    
    // UI Components
    private lateinit var btnReceived: Button
    private lateinit var btnOnProgress: Button
    private lateinit var btnPending: Button
    private lateinit var btnDone: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_status_wo)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        // Get work order data from intent
        @Suppress("UNCHECKED_CAST")
        workOrder = intent.getSerializableExtra("workOrder") as? Map<String, Any> ?: emptyMap()
        
        // Get current user - will be set later when updating status
        userName = ""
        
        setupToolbar()
        initViews()
        populateWorkOrderInfo()
        setupStatusButtons()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Update Status"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }
    
    private fun initViews() {
        btnReceived = findViewById(R.id.btn_received)
        btnOnProgress = findViewById(R.id.btn_on_progress)
        btnPending = findViewById(R.id.btn_pending)
        btnDone = findViewById(R.id.btn_done)
    }
    
    private fun populateWorkOrderInfo() {
        // Location
        findViewById<TextView>(R.id.tv_location_value).text = workOrder["lokasi"]?.toString() ?: "N/A"
        
        // Job
        findViewById<TextView>(R.id.tv_job_value).text = workOrder["job"]?.toString() ?: "N/A"
        
        // Date
        findViewById<TextView>(R.id.tv_date_value).text = formatDateTime(workOrder["mulainya"]?.toString())
        
        // Current Status
        val currentStatus = workOrder["status"]?.toString() ?: ""
        val statusText = if (currentStatus.isEmpty()) "NEW" else currentStatus.uppercase()
        findViewById<TextView>(R.id.tv_current_status_value).text = statusText
    }
    
    private fun setupStatusButtons() {
        val currentStatus = (workOrder["status"]?.toString() ?: "").lowercase()
        
        // Setup each button
        setupButton(btnReceived, "received", currentStatus)
        setupButton(btnOnProgress, "on progress", currentStatus)
        setupButton(btnPending, "pending", currentStatus)
        setupButton(btnDone, "done", currentStatus)
    }
    
    private fun setupButton(button: Button, status: String, currentStatus: String) {
        val isDisabled = isButtonDisabled(status, currentStatus)
        
        button.apply {
            text = status.uppercase()
            isEnabled = !isDisabled
            
            if (isDisabled) {
                setBackgroundResource(R.drawable.button_disabled_background)
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            } else {
                setBackgroundResource(R.drawable.button_primary_background)
                setTextColor(resources.getColor(android.R.color.white, null))
            }
            
            setOnClickListener {
                if (!isDisabled) {
                    selectedStatus = status
                    onStatusSelected(status)
                }
            }
        }
    }
    
    private fun isButtonDisabled(status: String, currentStatus: String): Boolean {
        return when (currentStatus) {
            "" -> false // New: semua aktif
            "received" -> status == "received"
            "on progress" -> status == "received" || status == "on progress"
            "pending" -> status == "received" || status == "pending"
            "done" -> true // Semua disable
            else -> false
        }
    }
    
    private fun onStatusSelected(status: String) {
        // If status is pending or done, directly open additional form
        if (status == "pending" || status == "done") {
            // Navigate directly to ChangePendingDoneActivity without dialog
            val intent = Intent(this, ChangePendingDoneActivity::class.java)
            intent.putExtra("workOrder", workOrder as java.io.Serializable)
            intent.putExtra("status", status)
            intent.putExtra("userName", userName)
            startActivityForResult(intent, REQUEST_PENDING_DONE)
        } else {
            updateStatus(status)
        }
    }
    
    companion object {
        private const val REQUEST_PENDING_DONE = 1002
    }
    
    private fun updateStatus(newStatus: String) {
        if (isLoading) return
        
        isLoading = true
        showLoading(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = UserService.getCurrentUser()
                val userName = user?.username ?: ""
                
                val success = callUpdateStatusAPI(newStatus, userName)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        // Successfully updated
                        Toast.makeText(
                            this@ChangeStatusWOActivity,
                            "Status berhasil diupdate ke: ${newStatus.uppercase()}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Return result for refresh
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // Failed to update
                        Toast.makeText(
                            this@ChangeStatusWOActivity,
                            "Gagal mengupdate status",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ChangeStatusWOActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    showLoading(false)
                }
            }
        }
    }
    
    private suspend fun callUpdateStatusAPI(newStatus: String, userName: String): Boolean {
        return try {
            
            // Use woId (primary key) to update status, not nour
            val woId = workOrder["woId"]?.toString() ?: ""
            
            // Prepare timeAccept for status 'received' (matching PHP)
            val timeAccept = if (newStatus.lowercase() == "received") {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            } else {
                null
            }
            
            // Call API with parameters
            
            // Call API using RetrofitClient
            val response = RetrofitClient.apiService.updateWorkOrderStatus(
                woId = woId,
                status = newStatus,
                userName = userName,
                timeAccept = timeAccept
            )
            
            // Simple response parsing matching PHP (Success/Failed)
            val success = response.trim().equals("Success", ignoreCase = true)
            
            success
        } catch (e: Exception) {
            false
        }
    }
    
    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.loading_overlay).visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun formatDateTime(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrEmpty()) return "N/A"
        
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateTimeStr)
            val outputFormat = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateTimeStr
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PENDING_DONE && resultCode == RESULT_OK) {
            // Status successfully updated, return to home
            setResult(RESULT_OK)
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
