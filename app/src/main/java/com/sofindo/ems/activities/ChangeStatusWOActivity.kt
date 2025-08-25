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
    
    // Status list sesuai dengan Flutter
    private val statusList = listOf("received", "on progress", "pending", "done")
    
    // UI Components
    private lateinit var btnReceived: Button
    private lateinit var btnOnProgress: Button
    private lateinit var btnPending: Button
    private lateinit var btnDone: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_status_wo)
        
        // Get work order data from intent
        @Suppress("UNCHECKED_CAST")
        workOrder = intent.getSerializableExtra("workOrder") as? Map<String, Any> ?: emptyMap()
        
        // Get current user - akan di-set nanti saat update status
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
        // Jika status adalah pending atau done, langsung buka form tambahan
        if (status == "pending" || status == "done") {
            // Navigate langsung ke ChangePendingDoneActivity tanpa dialog
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
        
        android.util.Log.d("ChangeStatusWO", "Updating status to: $newStatus")
        android.util.Log.d("ChangeStatusWO", "Work Order: ${workOrder["nour"]}")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = UserService.getCurrentUser()
                val userName = user?.username ?: ""
                
                android.util.Log.d("ChangeStatusWO", "User: $userName")
                
                val success = callUpdateStatusAPI(newStatus, userName)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        // Berhasil update
                        Toast.makeText(
                            this@ChangeStatusWOActivity,
                            "Status berhasil diupdate ke: ${newStatus.uppercase()}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Return result untuk refresh
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // Gagal update
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
            // Debug: Log semua field yang tersedia di work order
            android.util.Log.d("ChangeStatusWO", "=== WORK ORDER FIELDS ===")
            workOrder.forEach { (key, value) ->
                android.util.Log.d("ChangeStatusWO", "$key: $value")
            }
            
            // Gunakan woId (primary key) untuk update status, bukan nour
            val woId = workOrder["woId"]?.toString() ?: ""
            
            // Prepare timeAccept for status 'received' (sesuai PHP)
            val timeAccept = if (newStatus.lowercase() == "received") {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            } else {
                null
            }
            
            android.util.Log.d("ChangeStatusWO", "Calling API with:")
            android.util.Log.d("ChangeStatusWO", "woId: $woId")
            android.util.Log.d("ChangeStatusWO", "status: $newStatus")
            android.util.Log.d("ChangeStatusWO", "userName: $userName")
            android.util.Log.d("ChangeStatusWO", "timeAccept: $timeAccept")
            
            // Call API using RetrofitClient
            val response = RetrofitClient.apiService.updateWorkOrderStatus(
                woId = woId,
                status = newStatus,
                userName = userName,
                timeAccept = timeAccept
            )
            
            android.util.Log.d("ChangeStatusWO", "Raw API Response: '$response'")
            
            // Simple response parsing sesuai PHP (Success/Failed)
            val success = response.trim().equals("Success", ignoreCase = true)
            
            android.util.Log.d("ChangeStatusWO", "Parsed success: $success")
            
            success
        } catch (e: Exception) {
            android.util.Log.e("ChangeStatusWO", "API Error: ${e.message}", e)
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
            // Status berhasil diupdate, kembali ke home
            setResult(RESULT_OK)
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
