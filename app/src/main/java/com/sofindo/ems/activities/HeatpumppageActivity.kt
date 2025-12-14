package com.sofindo.ems.activities

import androidx.appcompat.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.adapters.HeatPumpRecordAdapter
import com.sofindo.ems.models.HeatPumpRecord
import com.sofindo.ems.views.HeatPumpChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HeatpumppageActivity : AppCompatActivity() {
    
    private lateinit var customToolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var recordsRecycler: RecyclerView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var addButton: FloatingActionButton
    private lateinit var heatPumpChartView: HeatPumpChartView
    private lateinit var summaryContainer: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var summaryDeltaT: TextView
    
    // Utility parameters
    private var utilityId: Int = 0
    private var propId: String = ""
    private var codeName: String = ""
    private var category: String = ""
    private var utilityName: String = ""
    private var satuan: String = ""
    private var folder: String = ""
    private var location: String = ""
    private var link: String = ""
    private var icon: String = ""
    
    // Data
    private var records: MutableList<HeatPumpRecord> = mutableListOf()
    private var adapter: HeatPumpRecordAdapter? = null
    private var isLoading = false
    private var errorMessage: String? = null
    
    // User info
    private lateinit var sharedPrefs: SharedPreferences
    private var userName: String = ""
    private var userDept: String = ""
    
    // Heat Pump specific data
    private var deltaTSetting: Double? = null
    private var minTemp: Double? = null
    private var lastSavedRecord: HeatPumpRecord? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heat_pump_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        loadHeatPumpData()
        loadTariff()
    }
    
    private fun initViews() {
        customToolbar = findViewById(R.id.toolbar)
        backButton = customToolbar.findViewById(R.id.back_button)
        toolbarTitle = customToolbar.findViewById(R.id.toolbar_title)
        recordsRecycler = findViewById(R.id.heat_pump_records_recycler)
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_message)
        retryButton = findViewById(R.id.retry_button)
        addButton = findViewById(R.id.add_button)
        heatPumpChartView = findViewById(R.id.heat_pump_chart)
        summaryContainer = findViewById(R.id.summary_container)
        summaryText = findViewById(R.id.summary_text)
        summaryDeltaT = findViewById(R.id.summary_delta_t)
        
        sharedPrefs = getSharedPreferences("ems_user_prefs", MODE_PRIVATE)
        userName = sharedPrefs.getString("username", "") ?: ""
        userDept = sharedPrefs.getString("dept", "") ?: ""
        
        retryButton.setOnClickListener {
            loadHeatPumpData()
        }
        
        addButton.setOnClickListener {
            showAddRecordDialog()
        }
        
        recordsRecycler.layoutManager = LinearLayoutManager(this)
        // Draw continuous vertical separators across the whole list
        recordsRecycler.addItemDecoration(com.sofindo.ems.views.RecyclerColumnDecoration(this))
    }
    
    private fun loadParameters() {
        utilityId = intent.getIntExtra("utility_id", 0)
        propId = intent.getStringExtra("prop_id") ?: ""
        codeName = intent.getStringExtra("code_name") ?: ""
        category = intent.getStringExtra("category") ?: ""
        utilityName = intent.getStringExtra("utility_name") ?: ""
        satuan = intent.getStringExtra("satuan") ?: ""
        folder = intent.getStringExtra("folder") ?: ""
        location = intent.getStringExtra("location") ?: ""
        link = intent.getStringExtra("link") ?: ""
        icon = intent.getStringExtra("icon") ?: ""
    }
    
    private fun setupToolbar() {
        toolbarTitle.text = utilityName.ifEmpty { "Heat Pump" }
        backButton.setOnClickListener { finish() }
    }
    
    private fun generateAllDatesInMonth(): List<Date> {
        val calendar = Calendar.getInstance()
        val now = Date()
        
        if (records.isNotEmpty()) {
            calendar.time = records.first().date
        } else {
            calendar.time = now
        }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        
        val allDates = mutableListOf<Date>()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        for (day in 1..daysInMonth) {
            val dateCalendar = Calendar.getInstance()
            dateCalendar.set(year, month, day)
            allDates.add(dateCalendar.time)
        }
        
        return allDates
    }
    
    private fun loadHeatPumpData() {
        isLoading = true
        errorMessage = null
        updateUI()
        
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/heatpump_read.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    isLoading = false
                    errorMessage = "Network error: ${e.message}"
                    updateUI()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    isLoading = false
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.getBoolean("success")
                        
                        if (success) {
                            val dataArray = jsonResponse.getJSONArray("data")
                            records.clear()
                            
                            for (i in 0 until dataArray.length()) {
                                val recordData = dataArray.getJSONObject(i)
                                val record = parseHeatPumpRecord(recordData)
                                if (record != null) {
                                    records.add(record)
                                }
                            }
                            
                            records.sortBy { it.date }
                            updateChart()
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            errorMessage = message
                        }
                    } catch (e: Exception) {
                        errorMessage = "Failed to parse response: ${e.message}"
                    }
                    updateUI()
                }
            }
        })
    }
    
    private fun parseHeatPumpRecord(data: JSONObject): HeatPumpRecord? {
        return try {
            val tglString = data.getString("tgl")
            val recBy = data.optString("rec_by", "")
            
            val waterIn = data.optString("waterTempIn", "")
            val waterOut = data.optString("waterTempOut", "")
            val highPress = data.optString("pressTinggi", "")
            val lowPress = data.optString("pressRendah", "")
            val amp = data.optString("Ampere", "")
            val volt = data.optString("Volt", "")
            
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatter.parse(tglString) ?: return null
            
            HeatPumpRecord(
                date = date,
                waterIn = waterIn,
                waterOut = waterOut,
                highPress = highPress,
                lowPress = lowPress,
                amp = amp,
                volt = volt,
                recBy = recBy
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun loadTariff() {
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/heatpump_tarif.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silent fail
            }
            
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.getBoolean("success")
                        
                        if (success) {
                            val data = jsonResponse.getJSONObject("data")
                            // Try multiple possible deltaT keys
                            val deltaTKeys = listOf("deltaT", "delta_t", "deltat", "delta", "dt")
                            for (key in deltaTKeys) {
                                if (data.has(key)) {
                                    val value = data.optString(key, "")
                                    if (value.isNotEmpty()) {
                                        deltaTSetting = value.toDoubleOrNull()
                                        break
                                    }
                                }
                            }
                            // Try multiple possible minTemp keys
                            val minTempKeys = listOf("minTemp", "min_temp", "mintemp", "min", "minT")
                            for (key in minTempKeys) {
                                if (data.has(key)) {
                                    val value = data.optString(key, "")
                                    if (value.isNotEmpty()) {
                                        minTemp = value.toDoubleOrNull()
                                        break
                                    }
                                }
                            }
                            updateSummary()
                            updateRecordsDisplay() // Refresh table to apply color changes
                        }
                    } catch (e: Exception) {
                        // Silent fail
                    }
                }
            }
        })
    }
    
    private fun updateUI() {
        when {
            isLoading -> {
                loadingContainer.visibility = View.VISIBLE
                recordsRecycler.visibility = View.GONE
                errorContainer.visibility = View.GONE
            }
            errorMessage != null -> {
                loadingContainer.visibility = View.GONE
                recordsRecycler.visibility = View.GONE
                errorContainer.visibility = View.VISIBLE
                errorText.text = errorMessage
            }
            else -> {
                loadingContainer.visibility = View.GONE
                errorContainer.visibility = View.GONE
                recordsRecycler.visibility = View.VISIBLE
                updateRecordsDisplay()
                heatPumpChartView.setData(records)
                updateSummary()
            }
        }
    }
    
    private fun updateChart() {
        heatPumpChartView.setData(records)
    }
    
    private fun updateRecordsDisplay() {
        val allDates = generateAllDatesInMonth()
        val displayRecords = mutableListOf<HeatPumpRecord>()
        
        allDates.forEach { date ->
            val record = records.find { 
                val recordCalendar = Calendar.getInstance()
                recordCalendar.time = it.date
                val dateCalendar = Calendar.getInstance()
                dateCalendar.time = date
                recordCalendar.get(Calendar.DAY_OF_MONTH) == dateCalendar.get(Calendar.DAY_OF_MONTH)
            }
            
            if (record != null) {
                displayRecords.add(record)
            } else {
                // Add empty record for missing date
                val emptyRecord = HeatPumpRecord(date = date)
                displayRecords.add(emptyRecord)
            }
        }
        
        adapter = HeatPumpRecordAdapter(displayRecords, minTemp)
        recordsRecycler.adapter = adapter
    }
    
    private fun updateSummary() {
        // Use lastSavedRecord if available (just saved), otherwise use latest from records
        val targetRecord = lastSavedRecord ?: records
            .sortedByDescending { it.date }
            .firstOrNull { r ->
                flexibleToDouble(r.waterIn) != null && flexibleToDouble(r.waterOut) != null
            }
        
        if (targetRecord != null) {
            val deltaT = computeDeltaT(targetRecord)
            val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(targetRecord.date)
            
            if (deltaT != null) {
                summaryDeltaT.text = if (deltaTSetting != null) {
                    "ΔT: ${String.format(Locale.US, "%.1f", deltaT)} °C (set: ${String.format(Locale.US, "%.1f", deltaTSetting!!)})"
                } else {
                    "ΔT: ${String.format(Locale.US, "%.1f", deltaT)} °C"
                }
                
                // Check if deltaT >= setting
                if (deltaTSetting != null && deltaT >= deltaTSetting!!) {
                    summaryText.text = "$dateStr: Summary: Operation Normal"
                    summaryText.setTextColor(ContextCompat.getColor(this, R.color.primary_color))
                } else {
                    summaryText.text = "$dateStr: Summary: Efisiensi kurang"
                    summaryText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
            } else {
                summaryText.text = "$dateStr: Summary: Need Maintenance"
                summaryText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                summaryDeltaT.text = "ΔT: -"
            }
        } else {
            summaryText.text = "Summary: Need Maintenance"
            summaryText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            summaryDeltaT.text = "ΔT: -"
        }
    }
    
    private fun computeDeltaT(record: HeatPumpRecord): Double? {
        val waterOut = flexibleToDouble(record.waterOut) ?: return null
        val waterIn = flexibleToDouble(record.waterIn) ?: return null
        val delta = waterOut - waterIn
        return if (delta < 0) null else delta
    }

    private fun flexibleToDouble(raw: String): Double? {
        if (raw.isEmpty()) return null
        // Handle values like "14", "14.0", "14,0"
        val normalized = raw.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }
    
    private fun showAddRecordDialog() {
        if (userDept.lowercase() != "engineering" && userDept.lowercase() != "admin") {
            showAccessDeniedAlert()
            return
        }
        
        try {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_add_heat_pump_record)
            
            val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
            val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
            val waterInInput = dialog.findViewById<EditText>(R.id.water_in_input)
            val waterOutInput = dialog.findViewById<EditText>(R.id.water_out_input)
            val highPressInput = dialog.findViewById<EditText>(R.id.high_press_input)
            val lowPressInput = dialog.findViewById<EditText>(R.id.low_press_input)
            val ampInput = dialog.findViewById<EditText>(R.id.amp_input)
            val voltInput = dialog.findViewById<EditText>(R.id.volt_input)
            val saveButton = dialog.findViewById<Button>(R.id.save_button)
            val cancelButton = dialog.findViewById<Button>(R.id.cancel_button)
            
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            
            datePicker.text = dateFormat.format(calendar.time)
            datePickerContainer.setOnClickListener {
                val datePickerDialog = DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        calendar.set(year, month, dayOfMonth)
                        datePicker.text = dateFormat.format(calendar.time)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                datePickerDialog.show()
            }
            
            saveButton.setOnClickListener {
                val waterIn = waterInInput.text.toString().trim()
                val waterOut = waterOutInput.text.toString().trim()
                val highPress = highPressInput.text.toString().trim()
                val lowPress = lowPressInput.text.toString().trim()
                val amp = ampInput.text.toString().trim()
                val volt = voltInput.text.toString().trim()
                
                // At least one field must be filled
                if (waterIn.isNotEmpty() || waterOut.isNotEmpty() || 
                    highPress.isNotEmpty() || lowPress.isNotEmpty() || 
                    amp.isNotEmpty() || volt.isNotEmpty()) {
                    saveNewRecord(calendar.time, waterIn, waterOut, highPress, lowPress, amp, volt)
                    dialog.dismiss()
                }
            }
            
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveNewRecord(date: Date, waterIn: String, waterOut: String, highPress: String, lowPress: String, amp: String, volt: String) {
        // Create record from current input for summary tracking (like Swift)
        val newRecord = HeatPumpRecord(
            date,
            waterIn,
            waterOut,
            highPress,
            lowPress,
            amp,
            volt,
            if (userName.isEmpty()) "User" else userName
        )
        lastSavedRecord = newRecord
        updateSummary() // Update immediately with the new record
        
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", dateString)
            put("waterTempIn", waterIn)
            put("waterTempOut", waterOut)
            put("pressTinggi", highPress)
            put("pressRendah", lowPress)
            put("Ampere", amp)
            put("Volt", volt)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/heatpump_post.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showErrorAlert("Save failed: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.getBoolean("success")
                        
                        if (success) {
                            loadHeatPumpData() // Refresh data
                        } else {
                            val message = jsonResponse.optString("message", "Save failed")
                            showErrorAlert(message)
                        }
                    } catch (e: Exception) {
                        showErrorAlert("Failed to parse response: ${e.message}")
                    }
                }
            }
        })
    }
    
    private fun showDeleteConfirmDialog(record: HeatPumpRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteHeatPumpRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteHeatPumpRecord(date: Date) {
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/heatpump_delete.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showErrorAlert("Delete failed: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.getBoolean("success")
                        
                        if (success) {
                            loadHeatPumpData()
                        } else {
                            val message = jsonResponse.optString("message", "Delete failed")
                            showErrorAlert(message)
                        }
                    } catch (e: Exception) {
                        showErrorAlert("Failed to parse response: ${e.message}")
                    }
                }
            }
        })
    }
    
    private fun showAccessDeniedAlert() {
        AlertDialog.Builder(this)
            .setTitle("Access Denied")
            .setMessage("You are not allowed")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showErrorAlert(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
