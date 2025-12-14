package com.sofindo.ems.activities

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.adapters.WaterTankRecordAdapter
import com.sofindo.ems.models.WaterTankRecord
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.views.WatertankChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class WatertankpageActivity : AppCompatActivity() {
    
    private lateinit var toolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var waterRecordsRecycler: RecyclerView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var waterChart: WatertankChartView
    private lateinit var totalWaterValue: TextView
    private lateinit var waterPriceValue: TextView
    private lateinit var totalCostValue: TextView
    private lateinit var addButton: FloatingActionButton
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var httpClient: OkHttpClient
    
    private var records: MutableList<WaterTankRecord> = mutableListOf()
    private var waterPrice: Double = 0.0
    private var tariffLoaded: Boolean = false
    
    // Parameters from intent
    private var propID: String = ""
    private var category: String = ""
    private var codeName: String = ""
    private var utilityName: String = ""
    private var satuan: String = ""
    private var userName: String = ""
    private var userDept: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_water_tank_page)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        
        initViews()
        loadParameters()
        setupToolbar()
        setupRecyclerView()
        loadWaterTankData()
        loadTariff()
        
        addButton.setOnClickListener {
            showAddRecordDialog()
        }
        
        retryButton.setOnClickListener {
            loadWaterTankData()
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        backButton = toolbar.findViewById(R.id.back_button)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        waterRecordsRecycler = findViewById(R.id.water_records_recycler)
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        errorMessage = findViewById(R.id.error_message)
        retryButton = findViewById(R.id.retry_button)
        waterChart = findViewById(R.id.water_chart)
        totalWaterValue = findViewById(R.id.total_water_value)
        waterPriceValue = findViewById(R.id.water_price_value)
        totalCostValue = findViewById(R.id.total_cost_value)
        addButton = findViewById(R.id.add_button)
        
        sharedPreferences = getSharedPreferences("ems_user_prefs", Context.MODE_PRIVATE)
        httpClient = OkHttpClient()
    }
    
    private fun loadParameters() {
        propID = intent.getStringExtra("prop_id") ?: ""
        category = intent.getStringExtra("category") ?: ""
        codeName = intent.getStringExtra("code_name") ?: ""
        utilityName = intent.getStringExtra("utility_name") ?: ""
        satuan = intent.getStringExtra("satuan") ?: ""
        userName = sharedPreferences.getString("username", "") ?: ""
        userDept = sharedPreferences.getString("dept", "") ?: ""
        
        // Debug logging
        android.util.Log.d("WatertankpageActivity", "Parameters received:")
        android.util.Log.d("WatertankpageActivity", "propID: '$propID'")
        android.util.Log.d("WatertankpageActivity", "codeName: '$codeName'")
        android.util.Log.d("WatertankpageActivity", "utilityName: '$utilityName'")
        
        toolbarTitle.text = utilityName
        
        // Validate required parameters
        if (propID.isEmpty()) {
            showError("propID is required")
            return
        }
        if (codeName.isEmpty()) {
            showError("codeName is required")
            return
        }
    }
    
    private fun setupToolbar() {
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        waterRecordsRecycler.layoutManager = LinearLayoutManager(this)
        waterRecordsRecycler.adapter = WaterTankRecordAdapter(records)
        // Draw continuous vertical separators across the whole list
        waterRecordsRecycler.addItemDecoration(com.sofindo.ems.views.RecyclerColumnDecoration(this))
    }
    
    private fun loadWaterTankData() {
        showLoading(true)
        hideError()
        
        // Get current month range
        val calendar = Calendar.getInstance()
        val now = Date()
        calendar.time = now
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.time
        
        calendar.time = now
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endOfMonth = calendar.time
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        val url = "https://emshotels.net/apiUtility/watertank_read.php"
        val requestBody = JSONObject().apply {
            put("propID", propID)
            put("codeName", codeName)
            put("startDate", dateFormat.format(startOfMonth))
            put("endDate", dateFormat.format(endOfMonth))
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    showError("Network error: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    runOnUiThread {
                        showLoading(false)
                        if (response.isSuccessful) {
                            try {
                                parseWaterTankData(responseBody, startOfMonth, endOfMonth)
                            } catch (e: Exception) {
                                android.util.Log.e("WatertankpageActivity", "Error parsing data", e)
                                showError("Failed to parse data: ${e.message}")
                            }
                        } else {
                            showError("Server error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showLoading(false)
                        android.util.Log.e("WatertankpageActivity", "Error reading response", e)
                        showError("Error reading response: ${e.message}")
                    }
                }
            }
        })
    }
    
    private fun parseWaterTankData(jsonString: String, startDate: Date, endDate: Date) {
        try {
            val json = JSONObject(jsonString)
            val success = json.getBoolean("success")
            
            if (success) {
                if (json.has("data") && !json.isNull("data")) {
                    val dataArray = json.getJSONArray("data")
                    val parsedRecords = mutableListOf<WaterTankRecord>()
                    
                    for (i in 0 until dataArray.length()) {
                        val recordData = dataArray.getJSONObject(i)
                        val record = parseWaterTankRecord(recordData)
                        if (record != null) {
                            parsedRecords.add(record)
                        }
                    }
                    
                    records.clear()
                    records.addAll(fillMissingDatesAndSort(parsedRecords, startDate, endDate))
                } else {
                    records.clear()
                    records.addAll(createEmptyRecordsForMonth(startDate, endDate))
                }
                
                updateUI()
            } else {
                val message = json.optString("message", "Unknown error")
                showError(message)
            }
        } catch (e: Exception) {
            showError("Failed to parse response: ${e.message}")
        }
    }
    
    private fun parseWaterTankRecord(data: JSONObject): WaterTankRecord? {
        try {
            val tglString = data.getString("tgl")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(tglString) ?: return null
            
            val lastMonthRest = when {
                data.has("lastMonthRest") && !data.isNull("lastMonthRest") -> {
                    when {
                        data.get("lastMonthRest") is Number -> data.get("lastMonthRest").toString()
                        data.get("lastMonthRest") is String -> data.getString("lastMonthRest")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val newWater = when {
                data.has("newWater") && !data.isNull("newWater") -> {
                    when {
                        data.get("newWater") is Number -> data.get("newWater").toString()
                        data.get("newWater") is String -> data.getString("newWater")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val totWater = when {
                data.has("totWater") && !data.isNull("totWater") -> {
                    when {
                        data.get("totWater") is Number -> data.get("totWater").toString()
                        data.get("totWater") is String -> data.getString("totWater")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val price = when {
                data.has("price") && !data.isNull("price") -> {
                    when {
                        data.get("price") is Number -> data.get("price").toString()
                        data.get("price") is String -> data.getString("price")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val priceTotal = when {
                data.has("priceTotal") && !data.isNull("priceTotal") -> {
                    when {
                        data.get("priceTotal") is Number -> data.get("priceTotal").toString()
                        data.get("priceTotal") is String -> data.getString("priceTotal")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val recBy = data.optString("rec_by", "")
            
            return WaterTankRecord(
                date = date,
                lastMonthRest = lastMonthRest,
                newWater = newWater,
                totWater = totWater,
                price = price,
                priceTotal = priceTotal,
                recBy = recBy
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun loadTariff() {
        val url = "https://emshotels.net/apiUtility/watertank_tarif.php"
        val requestBody = JSONObject().apply {
            put("propID", propID)
            put("codeName", codeName)
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Ignore tariff loading errors
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    runOnUiThread {
                        parseTariff(responseBody)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WatertankpageActivity", "Error loading tariff", e)
                }
            }
        })
    }
    
    private fun parseTariff(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val success = json.getBoolean("success")
            
            if (success && json.has("data") && !json.isNull("data")) {
                val data = json.getJSONObject("data")
                val priceString = when {
                    data.has("price") && !data.isNull("price") -> {
                        when {
                            data.get("price") is String -> data.getString("price")
                            data.get("price") is Number -> data.getDouble("price").toString()
                            else -> "0"
                        }
                    }
                    else -> "0"
                }
                
                waterPrice = priceString.toDoubleOrNull() ?: 0.0
                tariffLoaded = true
                updateCostBreakdown()
            }
        } catch (e: Exception) {
            // Ignore tariff parsing errors
        }
    }
    
    private fun updateUI() {
        waterRecordsRecycler.adapter?.notifyDataSetChanged()
        updateChart()
        updateCostBreakdown()
    }
    
    private fun updateChart() {
        waterChart.updateData(records)
    }
    
    private fun updateCostBreakdown() {
        val totalNewWater = records.sumOf { it.newWater.toDoubleOrNull() ?: 0.0 }
        val totalCost = records.sumOf { it.priceTotal.toDoubleOrNull() ?: 0.0 }
        
        totalWaterValue.text = "${totalNewWater.toInt()} m³"
        waterPriceValue.text = formatIDR(waterPrice)
        totalCostValue.text = formatIDR(totalCost)
    }
    
    private fun showAddRecordDialog() {
        // Check if user is from Engineering or Admin department
        if (userDept.lowercase() != "engineering" && userDept.lowercase() != "admin") {
            showAccessDeniedAlert()
            return
        }
        
        try {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_add_gas_record) // Reuse gas dialog layout
        
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val gasInput = dialog.findViewById<EditText>(R.id.gas_input)
        val saveButton = dialog.findViewById<Button>(R.id.save_button)
        val cancelButton = dialog.findViewById<Button>(R.id.cancel_button)
        
        // Null check
        if (datePicker == null || datePickerContainer == null || gasInput == null || saveButton == null || cancelButton == null) {
            android.util.Log.e("WatertankpageActivity", "Dialog views not found!")
            return
        }
        
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
            val waterValue = gasInput.text.toString().trim()
            if (waterValue.isNotEmpty()) {
                saveNewRecord(calendar.time, waterValue)
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("WatertankpageActivity", "Error showing dialog", e)
            showError("Failed to show dialog: ${e.message}")
        }
    }
    
    private fun saveNewRecord(date: Date, waterValue: String) {
        // Check if user is from Engineering or Admin department
        if (userDept.lowercase() != "engineering" && userDept.lowercase() != "admin") {
            showAccessDeniedAlert()
            return
        }
        
        // Try update first, then insert if update fails
        updateWaterRecord(date, waterValue) { updated ->
            if (updated) {
                loadWaterTankData()
            } else {
                insertWaterRecord(date, waterValue) {
                    loadWaterTankData()
                }
            }
        }
    }
    
    private fun updateWaterRecord(date: Date, waterValue: String, callback: (Boolean) -> Unit) {
        val url = "https://emshotels.net/apiUtility/watertank_update.php"
        val requestBody = JSONObject().apply {
            put("propID", propID)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("newWater", waterValue.toDoubleOrNull() ?: 0)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    callback(false)
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    runOnUiThread {
                        val success = response.isSuccessful
                        if (success && responseBody.contains("success")) {
                            val json = try {
                                JSONObject(responseBody)
                            } catch (e: Exception) {
                                null
                            }
                            callback(json?.getBoolean("success") ?: false)
                        } else {
                            callback(false)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        callback(false)
                    }
                }
            }
        })
    }
    
    private fun insertWaterRecord(date: Date, waterValue: String, callback: () -> Unit) {
        val url = "https://emshotels.net/apiUtility/watertank_post.php"
        val requestBody = JSONObject().apply {
            put("propID", propID)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("newWater", waterValue.toDoubleOrNull() ?: 0)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    callback()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.string() // Consume response body
                    runOnUiThread {
                        callback()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        callback()
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
    
    private fun showError(message: String) {
        errorMessage.text = message
        errorContainer.visibility = View.VISIBLE
        waterRecordsRecycler.visibility = View.GONE
    }
    
    private fun hideError() {
        errorContainer.visibility = View.GONE
        waterRecordsRecycler.visibility = View.VISIBLE
    }
    
    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        waterRecordsRecycler.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun formatIDR(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
    
    private fun fillMissingDatesAndSort(
        records: List<WaterTankRecord>,
        startDate: Date,
        endDate: Date
    ): List<WaterTankRecord> {
        val calendar = Calendar.getInstance()
        val result = mutableListOf<WaterTankRecord>()
        val existingRecords = records.associateBy { 
            val cal = Calendar.getInstance()
            cal.time = it.date
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.time
        }
        
        var current = startDate
        while (current.before(endDate) || current.equals(endDate)) {
            val cal = Calendar.getInstance()
            cal.time = current
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.time
            
            if (existingRecords.containsKey(dayStart)) {
                result.add(existingRecords[dayStart]!!)
            } else {
                result.add(
                    WaterTankRecord(
                        date = dayStart,
                        lastMonthRest = "",
                        newWater = "",
                        totWater = "",
                        price = "",
                        priceTotal = "",
                        recBy = ""
                    )
                )
            }
            
            cal.time = current
            cal.add(Calendar.DAY_OF_MONTH, 1)
            current = cal.time
        }
        
        return result
    }
    
    private fun createEmptyRecordsForMonth(startDate: Date, endDate: Date): List<WaterTankRecord> {
        val calendar = Calendar.getInstance()
        val result = mutableListOf<WaterTankRecord>()
        
        var current = startDate
        while (current.before(endDate) || current.equals(endDate)) {
            val cal = Calendar.getInstance()
            cal.time = current
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.time
            
            result.add(
                WaterTankRecord(
                    date = dayStart,
                    lastMonthRest = "",
                    newWater = "",
                    totWater = "",
                    price = "",
                    priceTotal = "",
                    recBy = ""
                )
            )
            
            cal.time = current
            cal.add(Calendar.DAY_OF_MONTH, 1)
            current = cal.time
        }
        
        return result
    }
}
