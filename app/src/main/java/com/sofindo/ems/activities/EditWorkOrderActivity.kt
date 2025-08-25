package com.sofindo.ems.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EditWorkOrderActivity : AppCompatActivity() {
    
    private lateinit var tvWoNumber: TextView
    private lateinit var tvWoStatus: TextView
    private lateinit var tvWoDate: TextView
    
    private lateinit var etJob: EditText
    private lateinit var etLocation: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerDepartment: Spinner
    private lateinit var spinnerPriority: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var layoutLocationSuggestions: LinearLayout
    
    private var workOrder: Map<String, Any>? = null
    private var categories = mutableListOf<String>()
    private var departments = mutableListOf<String>()
    private var allLocations = mutableListOf<String>()
    private var locationSuggestions = mutableListOf<String>()
    private var isLoading = false
    private var isLoadingData = true
    
    private val priorities = listOf("Low", "Medium", "High")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("EditWorkOrderActivity", "onCreate started")
            setContentView(R.layout.activity_edit_work_order)
            android.util.Log.d("EditWorkOrderActivity", "setContentView completed")
        } catch (e: Exception) {
            android.util.Log.e("EditWorkOrderActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error: Cannot load edit screen", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Get work order data from intent
        @Suppress("DEPRECATION")
        try {
            workOrder = intent.getSerializableExtra("workOrder") as? Map<String, Any>
            
            if (workOrder == null) {
                Toast.makeText(this, "Error: Work order data not found", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("EditWorkOrderActivity", "Error parsing work order data", e)
            Toast.makeText(this, "Error: Invalid work order data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            android.util.Log.d("EditWorkOrderActivity", "Initializing views")
            initViews()
            android.util.Log.d("EditWorkOrderActivity", "Setting up toolbar")
            setupToolbar()
            android.util.Log.d("EditWorkOrderActivity", "Loading data")
            loadData()
            android.util.Log.d("EditWorkOrderActivity", "Setting up listeners")
            setupListeners()
            android.util.Log.d("EditWorkOrderActivity", "Loading master data")
            loadMasterData()
            android.util.Log.d("EditWorkOrderActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("EditWorkOrderActivity", "Error in onCreate setup", e)
            Toast.makeText(this, "Error: Cannot initialize edit screen", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun initViews() {
        try {
            tvWoNumber = findViewById(R.id.tv_wo_number)
            tvWoStatus = findViewById(R.id.tv_wo_status)
            tvWoDate = findViewById(R.id.tv_wo_date)
            
            etJob = findViewById(R.id.et_job)
            etLocation = findViewById(R.id.et_location)
            spinnerCategory = findViewById(R.id.spinner_category)
            spinnerDepartment = findViewById(R.id.spinner_department)
            spinnerPriority = findViewById(R.id.spinner_priority)
            btnSave = findViewById(R.id.btn_save)
            btnCancel = findViewById(R.id.btn_cancel)
            layoutLocationSuggestions = findViewById(R.id.layout_location_suggestions)
        } catch (e: Exception) {
            android.util.Log.e("EditWorkOrderActivity", "Error initializing views", e)
            Toast.makeText(this, "Error: Cannot initialize UI", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "Edit Work Order"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }
    }
    
    private fun loadData() {
        workOrder?.let { wo ->
            // Set work order information
            tvWoNumber.text = "WO: ${wo["nour"] ?: "N/A"}"
            tvWoStatus.text = (wo["status"]?.toString() ?: "NEW").uppercase()
            tvWoDate.text = formatDate(wo["mulainya"]?.toString())
            
            // Set form fields
            etJob.setText(wo["job"]?.toString() ?: "")
            etLocation.setText(wo["lokasi"]?.toString() ?: "")
        }
    }
    
    private fun setupListeners() {
        // Save button
        btnSave.setOnClickListener {
            if (!isLoading) {
                updateWorkOrder()
            }
        }
        
        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }
        
        // Location autocomplete
        etLocation.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterLocations(s?.toString() ?: "")
            }
        })
    }
    
    private fun loadMasterData() {
        lifecycleScope.launch {
            try {
                val propID = workOrder?.get("propID")?.toString() ?: ""
                if (propID.isEmpty()) {
                    android.util.Log.e("EditWorkOrderActivity", "propID is empty")
                    return@launch
                }
                
                // Load data with timeout handling
                val categoriesResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.getCategories(propID)
                    } catch (e: Exception) {
                        listOf("General", "Maintenance", "Housekeeping", "Security")
                    }
                }
                
                val departmentsResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.getDepartments(propID)
                    } catch (e: Exception) {
                        listOf("Engineering", "Housekeeping", "Security", "Front Office")
                    }
                }
                
                val locationsResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.getLocations(propID)
                    } catch (e: Exception) {
                        listOf("Lobby", "Room", "Kitchen", "Pool", "Garden")
                    }
                }
                
                // Set categories
                categories.clear()
                categories.addAll(categoriesResult)
                if (!categories.contains("General")) {
                    categories.add(0, "General")
                }
                
                // Set departments
                departments.clear()
                departments.addAll(departmentsResult)
                
                // Set locations
                allLocations.clear()
                allLocations.addAll(locationsResult)
                
                setupSpinners()
                isLoadingData = false
                updateUI()
                
            } catch (e: Exception) {
                android.util.Log.e("EditWorkOrderActivity", "Error loading master data", e)
                // Set fallback data
                categories = mutableListOf("General", "Maintenance", "Housekeeping", "Security")
                departments = mutableListOf("Engineering", "Housekeeping", "Security", "Front Office")
                allLocations = mutableListOf("Lobby", "Room", "Kitchen", "Pool", "Garden")
                setupSpinners()
                isLoadingData = false
                updateUI()
            }
        }
    }
    
    private fun setupSpinners() {
        // Category Spinner
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter
        
        // Department Spinner
        val departmentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = departmentAdapter
        
        // Priority Spinner
        val priorityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
        
        // Set current values from work order
        workOrder?.let { wo ->
            // Set category
            val category = wo["category"]?.toString() ?: "General"
            val categoryIndex = categories.indexOfFirst { it.equals(category, ignoreCase = true) }
            if (categoryIndex >= 0) {
                spinnerCategory.setSelection(categoryIndex)
            }
            
            // Set department
            val department = wo["woto"]?.toString() ?: ""
            val departmentIndex = departments.indexOfFirst { it.equals(department, ignoreCase = true) }
            if (departmentIndex >= 0) {
                spinnerDepartment.setSelection(departmentIndex)
            }
            
            // Set priority
            val priority = wo["priority"]?.toString() ?: "Low"
            val priorityIndex = priorities.indexOfFirst { it.equals(priority, ignoreCase = true) }
            if (priorityIndex >= 0) {
                spinnerPriority.setSelection(priorityIndex)
            }
        }
    }
    
    private fun filterLocations(query: String) {
        if (query.isEmpty()) {
            locationSuggestions.clear()
            layoutLocationSuggestions.visibility = View.GONE
            return
        }
        
        locationSuggestions.clear()
        locationSuggestions.addAll(
            allLocations.filter { 
                it.lowercase().contains(query.lowercase()) 
            }
        )
        
        if (locationSuggestions.isNotEmpty()) {
            setupLocationSuggestions()
            layoutLocationSuggestions.visibility = View.VISIBLE
        } else {
            layoutLocationSuggestions.visibility = View.GONE
        }
    }
    
    private fun setupLocationSuggestions() {
        layoutLocationSuggestions.removeAllViews()
        
        locationSuggestions.forEach { location ->
            val textView = TextView(this).apply {
                text = location
                setPadding(32, 16, 32, 16)
                setTextColor(resources.getColor(R.color.black, null))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = resources.getDrawable(R.drawable.suggestions_background, null)
                setOnClickListener {
                    etLocation.setText(location)
                    layoutLocationSuggestions.visibility = View.GONE
                }
            }
            layoutLocationSuggestions.addView(textView)
        }
    }
    
    private fun updateUI() {
        // Update UI based on loading state
        if (isLoadingData) {
            // Show loading state if needed
        } else {
            // Hide loading state
        }
    }
    
    private fun updateWorkOrder() {
        val job = etJob.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val category = spinnerCategory.selectedItem.toString()
        val department = spinnerDepartment.selectedItem.toString()
        val priority = spinnerPriority.selectedItem.toString()
        
        // Validation
        if (job.isEmpty()) {
            Toast.makeText(this, "Job tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (location.isEmpty()) {
            Toast.makeText(this, "Location tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        
        val woId = workOrder?.get("woId")?.toString() ?: workOrder?.get("nour")?.toString()
        if (woId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Work Order ID tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        btnSave.isEnabled = false
        btnSave.text = "Updating..."
        
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateWorkOrder(
                    woId = woId,
                    lokasi = location,
                    job = job,
                    priority = priority
                )
                
                val success = response["success"] as? Boolean ?: false
                val message = response["message"]?.toString() ?: "Unknown response"
                
                if (success) {
                    Toast.makeText(this@EditWorkOrderActivity, "Work Order berhasil diupdate", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@EditWorkOrderActivity, "Gagal mengupdate Work Order: $message", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("EditWorkOrderActivity", "Error updating work order", e)
                Toast.makeText(this@EditWorkOrderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                btnSave.isEnabled = true
                btnSave.text = "Save Change"
            }
        }
    }
    
    private fun formatDate(dateTimeStr: String?): String {
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
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        if (isLoading) {
            Toast.makeText(this, "Please wait, updating work order...", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }
}
