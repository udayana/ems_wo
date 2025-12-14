package com.sofindo.ems.activities

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.adapters.ChillerRecordAdapter
import com.sofindo.ems.models.ChillerRecord
import com.sofindo.ems.views.ChillerChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChillerpageActivity : AppCompatActivity() {
    
    private lateinit var customToolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var recordsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var addButton: FloatingActionButton
    private lateinit var chillerChartView: ChillerChartView
    
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
    private var records: MutableList<ChillerRecord> = mutableListOf()
    private var adapter: ChillerRecordAdapter? = null
    private var isLoading = false
    private var errorMessage: String? = null
    
    // User info
    private lateinit var sharedPrefs: SharedPreferences
    private var userName: String = ""
    private var userDept: String = ""
    
    // Chiller specific data
    private var maxTemp: Double = 0.0
    private var maxTempLoaded = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chiller_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        loadChillerData()
        loadTariff()
    }
    
    private fun initViews() {
        customToolbar = findViewById(R.id.custom_toolbar)
        backButton = customToolbar.findViewById(R.id.back_button)
        toolbarTitle = customToolbar.findViewById(R.id.toolbar_title)
        recordsContainer = findViewById(R.id.chiller_records_container)
        progressBar = findViewById(R.id.progress_bar)
        errorText = findViewById(R.id.error_text)
        retryButton = findViewById(R.id.retry_button)
        addButton = findViewById(R.id.add_button)
        chillerChartView = findViewById(R.id.chiller_chart_view)
        
        sharedPrefs = getSharedPreferences("ems_user_prefs", MODE_PRIVATE)
        userName = sharedPrefs.getString("username", "") ?: ""
        userDept = sharedPrefs.getString("dept", "") ?: ""
        
        retryButton.setOnClickListener {
            loadChillerData()
        }
        
        addButton.setOnClickListener {
            showAddRecordDialog()
        }
    }
    
    private fun loadParameters() {
        // Get parameters from intent
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
        // Set title
        toolbarTitle.text = utilityName.ifEmpty { "Chiller" }
        
        // Set back button click listener
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun generateAllDatesInMonth(): List<Date> {
        if (records.isEmpty()) return emptyList()
        
        val calendar = Calendar.getInstance()
        val firstRecord = records.first()
        calendar.time = firstRecord.date
        
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
    
    private fun loadChillerData() {
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
            .url("https://emshotels.net/apiUtility/chiller_read.php")
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
                            
                            println("Chiller READ Response:")
                            println("Data array length: ${dataArray.length()}")
                            
                            for (i in 0 until dataArray.length()) {
                                val recordData = dataArray.getJSONObject(i)
                                println("Record $i: $recordData")
                                val record = parseChillerRecord(recordData)
                                if (record != null) {
                                    records.add(record)
                                    println("Parsed record: date=${record.date}, temp=${record.tempRecord}")
                                } else {
                                    println("Failed to parse record $i")
                                }
                            }
                            
                            println("Total records parsed: ${records.size}")
                            
                            // Sort records by date (oldest first)
                            records.sortBy { it.date }
                            updateChart()
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            println("Chiller READ Error: $message")
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
    
    private fun parseChillerRecord(data: JSONObject): ChillerRecord? {
        return try {
            val tglString = data.getString("tgl")
            val recBy = data.getString("rec_by")
            
            val tempRecord = when {
                data.has("temp") && !data.isNull("temp") -> {
                    val temp = data.optString("temp", "")
                    when {
                        temp == "0" || temp == "-999" || temp == "-999.00" || temp.isEmpty() -> ""
                        else -> temp
                    }
                }
                else -> ""
            }
            
            val diff = when {
                tempRecord.isEmpty() -> ""
                data.has("selisih") && !data.isNull("selisih") -> {
                    val selisih = data.optString("selisih", "")
                    when {
                        selisih == "0" || selisih == "-999" || selisih == "-999.00" || selisih.isEmpty() -> ""
                        else -> selisih
                    }
                }
                else -> ""
            }
            
            val upDown = when {
                tempRecord.isEmpty() -> ""
                data.has("upDown") && !data.isNull("upDown") -> {
                    val upDownValue = data.optString("upDown", "")
                    when {
                        upDownValue == "0" || upDownValue == "-999" || upDownValue == "-999.00" || upDownValue.isEmpty() -> ""
                        else -> {
                            upDownValue.toIntOrNull()?.let { value ->
                                when {
                                    value > 0 -> "↑"
                                    value < 0 -> "↓"
                                    else -> ""
                                }
                            } ?: upDownValue
                        }
                    }
                }
                else -> ""
            }
            
            // Parse date
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatter.parse(tglString) ?: return null
            
            ChillerRecord(
                date = date,
                tempRecord = tempRecord,
                diff = diff,
                upDown = upDown,
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
            .url("https://emshotels.net/apiUtility/chiller_tarif.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
            }
            
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.getBoolean("success")
                        
                        if (success) {
                            val data = jsonResponse.getJSONObject("data")
                            maxTemp = data.optDouble("maxTemp", 0.0)
                            maxTempLoaded = true
                            updateChart()
                        }
                    } catch (e: Exception) {
                    }
                }
            }
        })
    }
    
    private fun updateUI() {
        when {
            isLoading -> {
                progressBar.visibility = View.VISIBLE
                recordsContainer.visibility = View.GONE
                errorText.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            errorMessage != null -> {
                progressBar.visibility = View.GONE
                recordsContainer.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
                errorText.text = errorMessage
            }
            records.isEmpty() -> {
                progressBar.visibility = View.GONE
                recordsContainer.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.GONE
                errorText.text = "No data available"
            }
            else -> {
                progressBar.visibility = View.GONE
                recordsContainer.visibility = View.VISIBLE
                errorText.visibility = View.GONE
                retryButton.visibility = View.GONE
                updateRecordsDisplay()
                chillerChartView.setData(records, maxTemp)
            }
        }
    }
    
    private fun updateChart() {
        chillerChartView.setData(records, maxTemp)
    }
    
    private fun updateRecordsDisplay() {
        recordsContainer.removeAllViews()
        
        val allDates = generateAllDatesInMonth()
        val inflater = LayoutInflater.from(this)
        
        println("updateRecordsDisplay:")
        println("Total records from server: ${records.size}")
        println("Total dates to display: ${allDates.size}")
        println("Records dates: ${records.map { it.date }}")
        
        allDates.forEach { date ->
            val record = records.find { 
                val recordCalendar = Calendar.getInstance()
                recordCalendar.time = it.date
                val dateCalendar = Calendar.getInstance()
                dateCalendar.time = date
                recordCalendar.get(Calendar.DAY_OF_MONTH) == dateCalendar.get(Calendar.DAY_OF_MONTH)
            }
            
            val itemView = inflater.inflate(R.layout.item_chiller_record, recordsContainer, false)
            
            // Set date
            val dateText = itemView.findViewById<TextView>(R.id.date_text)
            val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
            dateText.text = dateFormat.format(date)
            
            // Set temperature record
            val tempText = itemView.findViewById<TextView>(R.id.temp_text)
            val tempDisplay = when {
                record == null || record.tempRecord.isEmpty() || record.tempRecord == "0" || record.tempRecord == "0.00" -> "-"
                else -> record.tempRecord
            }
            tempText.text = tempDisplay
            
            // Set temperature color based on maxTemp
            if (maxTemp > 0 && tempDisplay != "-") {
                val temp = tempDisplay.toDoubleOrNull() ?: 0.0
                if (temp >= maxTemp) {
                    tempText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                } else {
                    tempText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                }
            } else {
                tempText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
            
            // Set diff
            val diffText = itemView.findViewById<TextView>(R.id.diff_text)
            val diffDisplay = when {
                record == null || record.tempRecord.isEmpty() || record.tempRecord == "0" || record.tempRecord == "0.00" -> "-"
                record.diff.isEmpty() || record.diff == "0" || record.diff == "0.00" -> "-"
                else -> record.diff
            }
            diffText.text = diffDisplay
            
            // Set up/down
            val upDownText = itemView.findViewById<TextView>(R.id.up_down_text)
            val upDownDisplay = when {
                record == null || record.tempRecord.isEmpty() || record.tempRecord == "0" || record.tempRecord == "0.00" -> "-"
                record.upDown.isEmpty() || record.upDown == "0" -> "-"
                else -> record.upDown
            }
            upDownText.text = upDownDisplay
            
            // Set rec by
            val recByText = itemView.findViewById<TextView>(R.id.rec_by_text)
            recByText.text = if (record == null || record.recBy.isEmpty() || record.recBy == "0") "-" else record.recBy
            
            // Set double-click to delete
            var lastClickTime = 0L
            itemView.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 500 && record != null) { // 500ms double click threshold
                    showDeleteConfirmDialog(record)
                }
                lastClickTime = currentTime
            }
            
            // Hide delete button
            val deleteButton = itemView.findViewById<View>(R.id.delete_button)
            deleteButton.visibility = View.GONE
            
            recordsContainer.addView(itemView)
        }
    }
    
    private fun showAddRecordDialog() {
        // Check if user is from Engineering department
        if (userDept.lowercase() != "engineering") {
            showAccessDeniedAlert()
            return
        }
        
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_chiller_record)
        
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val tempInput = dialog.findViewById<EditText>(R.id.temp_input)
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
            val tempValue = tempInput.text.toString().trim()
            if (tempValue.isNotEmpty()) {
                saveNewRecord(calendar.time, tempValue)
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveNewRecord(date: Date, tempValue: String) {
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", dateString)
            put("temp", tempValue)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }
        
        // Debug logging
        println("Chiller POST Request:")
        println("propID: $propId")
        println("codeName: $codeName")
        println("tgl: $dateString")
        println("temp: $tempValue")
        println("rec_by: ${if (userName.isEmpty()) "User" else userName}")
        println("Request Body: $requestBody")
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/chiller_post.php")
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
                        println("Chiller POST Response:")
                        println("Status Code: ${response.code}")
                        println("Response Body: $responseBody")
                        
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.getBoolean("success")
                        
                        if (success) {
                            println("Chiller POST Success - Refreshing data")
                            loadChillerData() // Refresh data
                        } else {
                            val message = jsonResponse.optString("message", "Save failed")
                            println("Chiller POST Failed: $message")
                            showErrorAlert(message)
                        }
                    } catch (e: Exception) {
                        println("Chiller POST Error: ${e.message}")
                        showErrorAlert("Failed to parse response: ${e.message}")
                    }
                }
            }
        })
    }
    
    private fun showDeleteConfirmDialog(record: ChillerRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteChillerRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteChillerRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/chiller_delete.php")
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
                            loadChillerData() // Refresh data
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