package com.sofindo.ems.activities

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.models.KwhtmRecord
import com.sofindo.ems.views.KwhtmChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class KwhtmpageActivity : AppCompatActivity() {
    
    // UI Components
    private lateinit var toolbarTitle: TextView
    private lateinit var backButton: ImageButton
    private lateinit var recordsContainer: LinearLayout
    private lateinit var addButton: FloatingActionButton
    private lateinit var kwhtmChart: KwhtmChartView
    
    // Cost breakdown components
    private lateinit var totalConsumeLWBPText: TextView
    private lateinit var totalConsumeWBPText: TextView
    private lateinit var costLWBPText: TextView
    private lateinit var costWBPText: TextView
    private lateinit var subtotalText: TextView
    private lateinit var ppjCostText: TextView
    private lateinit var fixedCostText: TextView
    private lateinit var grandTotalText: TextView
    
    // Loading/Error components
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    
    // Data
    private val records = mutableListOf<KwhtmRecord>()
    private var isLoading = false
    private var errorMessage: String? = null
    
    // Parameters
    private var propId = ""
    private var codeName = ""
    private var utilityName = ""
    private var satuan = ""
    
    // User info
    private lateinit var sharedPreferences: SharedPreferences
    private var userName = ""
    private var userDept = ""
    
    // KWH TM specific data
    private var trfLWBP: Double = 1000.0
    private var trfWBP: Double = 1500.0
    private var trfKVARH: Double = 800.0
    private var ct: Double = 1.0
    private var ppj: Double = 10.0
    private var fixcost: Double = 0.0
    private var tariffLoaded = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kwhtm_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        loadKwhtmData()
        loadTariff()
    }
    
    private fun initViews() {
        toolbarTitle = findViewById(R.id.toolbar_title)
        backButton = findViewById(R.id.back_button)
        recordsContainer = findViewById(R.id.kwhtm_records_container)
        addButton = findViewById(R.id.add_button)
        kwhtmChart = findViewById(R.id.kwhtm_chart)
        
        // Cost breakdown components
        totalConsumeLWBPText = findViewById(R.id.total_consume_lwbp_text)
        totalConsumeWBPText = findViewById(R.id.total_consume_wbp_text)
        costLWBPText = findViewById(R.id.cost_lwbp_text)
        costWBPText = findViewById(R.id.cost_wbp_text)
        subtotalText = findViewById(R.id.subtotal_text)
        ppjCostText = findViewById(R.id.ppj_cost_text)
        fixedCostText = findViewById(R.id.fixed_cost_text)
        grandTotalText = findViewById(R.id.grand_total_text)
        
        // Loading/Error components
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_text)
        retryButton = findViewById(R.id.retry_button)
        
        // Initialize shared preferences
        sharedPreferences = getSharedPreferences("ems_user_prefs", Context.MODE_PRIVATE)
        userName = sharedPreferences.getString("username", "") ?: ""
        userDept = sharedPreferences.getString("dept", "") ?: ""
        
        // Set up click listeners
        backButton.setOnClickListener {
            finish()
        }
        
        addButton.setOnClickListener {
            showAddRecordDialog()
        }
        
        retryButton.setOnClickListener {
            loadKwhtmData()
        }
    }
    
    private fun loadParameters() {
        propId = intent.getStringExtra("prop_id") ?: ""
        codeName = intent.getStringExtra("code_name") ?: ""
        utilityName = intent.getStringExtra("utility_name") ?: ""
        satuan = intent.getStringExtra("satuan") ?: "kwh"
        
        toolbarTitle.text = utilityName
        
        // Debug logging
        android.util.Log.d("KwhtmpageActivity", "propId: '$propId'")
        android.util.Log.d("KwhtmpageActivity", "codeName: '$codeName'")
        android.util.Log.d("KwhtmpageActivity", "utilityName: '$utilityName'")
        android.util.Log.d("KwhtmpageActivity", "satuan: '$satuan'")
        android.util.Log.d("KwhtmpageActivity", "userName: '$userName'")
        android.util.Log.d("KwhtmpageActivity", "userDept: '$userDept'")
        
        if (propId.isEmpty() || codeName.isEmpty()) {
            showError("Missing required parameters")
            return
        }
    }
    
    private fun createRecordView(record: KwhtmRecord): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.item_kwhtm_record, recordsContainer, false)
        
        val dateText = view.findViewById<TextView>(R.id.date_text)
        val recLWBPText = view.findViewById<TextView>(R.id.rec_lwbp_text)
        val recWBPText = view.findViewById<TextView>(R.id.rec_wbp_text)
        val consumeLWBPText = view.findViewById<TextView>(R.id.consume_lwbp_text)
        val consumeWBPText = view.findViewById<TextView>(R.id.consume_wbp_text)
        val totalCostText = view.findViewById<TextView>(R.id.total_cost_text)
        val recByText = view.findViewById<TextView>(R.id.rec_by_text)
        
        val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
        
        dateText.text = dateFormat.format(record.date)
        recLWBPText.text = if (record.recLWBP.isEmpty() || record.recLWBP == "0") "-" else record.recLWBP
        recWBPText.text = if (record.recWBP.isEmpty() || record.recWBP == "0") "-" else record.recWBP
        consumeLWBPText.text = if (record.consumeLWBP.isEmpty() || record.consumeLWBP == "0") "-" else record.consumeLWBP
        consumeWBPText.text = if (record.consumeWBP.isEmpty() || record.consumeWBP == "0") "-" else record.consumeWBP
        totalCostText.text = calculateTotalCost(record)
        recByText.text = if (record.recBy.isEmpty()) "-" else record.recBy
        
        // Double click to delete
        var clickCount = 0
        var lastClickTime = 0L
        
        view.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            
            if (clickCount == 1 && (currentTime - lastClickTime) < 500) {
                // Double click detected
                showDeleteConfirmDialog(record)
                clickCount = 0
            } else {
                clickCount = 1
                lastClickTime = currentTime
            }
        }
        
        // Long click to delete (alternative)
        view.setOnLongClickListener {
            showDeleteConfirmDialog(record)
            true
        }
        
        return view
    }
    
    private fun loadKwhtmData() {
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
            .url("https://emshotels.net/apiUtility/kwhtm_read.php")
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
                response.use { resp ->
                    val responseBody = resp.body?.string() ?: ""
                    
                    runOnUiThread {
                        isLoading = false
                        
                        try {
                            val json = JSONObject(responseBody)
                            val success = json.optBoolean("success", false)
                            
                            if (success) {
                                val dataArray = json.getJSONArray("data")
                                records.clear()
                                
                                android.util.Log.d("KwhtmpageActivity", "Total records from API: ${dataArray.length()}")
                                
                                for (i in 0 until dataArray.length()) {
                                    val recordData = dataArray.getJSONObject(i)
                                    val record = parseKwhtmRecord(recordData)
                                    if (record != null) {
                                        records.add(record)
                                        android.util.Log.d("KwhtmpageActivity", "Parsed record $i: ${record.date} - LWBP: ${record.consumeLWBP}, WBP: ${record.consumeWBP}")
                                    } else {
                                        android.util.Log.d("KwhtmpageActivity", "Failed to parse record $i: $recordData")
                                    }
                                }
                                
                                android.util.Log.d("KwhtmpageActivity", "Successfully parsed records: ${records.size}")
                                
                                updateUI()
                                errorMessage = null
                            } else {
                                val message = json.optString("message", "Unknown error")
                                errorMessage = message
                            }
                        } catch (e: Exception) {
                            errorMessage = "Failed to parse response: ${e.message}"
                        }
                        
                        updateUI()
                    }
                }
            }
        })
    }
    
    private fun parseKwhtmRecord(data: JSONObject): KwhtmRecord? {
        try {
            val tglString = data.getString("tgl")
            val recBy = data.optString("rec_by", "")
        
            val recLWBP = when {
                data.has("rec_lwbp") -> {
                    when {
                        data.get("rec_lwbp") is Int -> data.getInt("rec_lwbp").toString()
                        data.get("rec_lwbp") is String -> data.getString("rec_lwbp")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val recWBP = when {
                data.has("rec_wbp") -> {
                    when {
                        data.get("rec_wbp") is Int -> data.getInt("rec_wbp").toString()
                        data.get("rec_wbp") is String -> data.getString("rec_wbp")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val recKVARH = when {
                data.has("rec_kvarh") -> {
                    when {
                        data.get("rec_kvarh") is Int -> data.getInt("rec_kvarh").toString()
                        data.get("rec_kvarh") is String -> data.getString("rec_kvarh")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val consumeLWBP = when {
                data.has("konsum_lwbp") -> {
                    when {
                        data.get("konsum_lwbp") is Int -> data.getInt("konsum_lwbp").toString()
                        data.get("konsum_lwbp") is String -> data.getString("konsum_lwbp")
                        else -> "0"
                    }
                }
                else -> "0"
            }
            
            val consumeWBP = when {
                data.has("konsum_wbp") -> {
                    when {
                        data.get("konsum_wbp") is Int -> data.getInt("konsum_wbp").toString()
                        data.get("konsum_wbp") is String -> data.getString("konsum_wbp")
                        else -> "0"
                    }
                }
                else -> "0"
            }
            
            val consumeKVARH = when {
                data.has("konsum_kvarh") -> {
                    when {
                        data.get("konsum_kvarh") is Int -> data.getInt("konsum_kvarh").toString()
                        data.get("konsum_kvarh") is String -> data.getString("konsum_kvarh")
                        else -> "0"
                    }
                }
                else -> "0"
            }
            
            val costLWBP = when {
                data.has("cost_lwbp") -> {
                    when {
                        data.get("cost_lwbp") is Int -> data.getInt("cost_lwbp").toString()
                        data.get("cost_lwbp") is String -> data.getString("cost_lwbp")
                        else -> "0"
                    }
                }
                else -> "0"
            }
            
            val costWBP = when {
                data.has("cost_wbp") -> {
                    when {
                        data.get("cost_wbp") is Int -> data.getInt("cost_wbp").toString()
                        data.get("cost_wbp") is String -> data.getString("cost_wbp")
                        else -> "0"
                    }
                }
                else -> "0"
            }
            
            val costKVARH = when {
                data.has("cost_kvarh") -> {
                    when {
                        data.get("cost_kvarh") is Int -> data.getInt("cost_kvarh").toString()
                        data.get("cost_kvarh") is String -> data.getString("cost_kvarh")
                        else -> "0"
                    }
                }
                else -> "0"
            }
            
            val totalCost = when {
                data.has("total_cost") -> {
                    when {
                        data.get("total_cost") is Int -> data.getInt("total_cost").toString()
                        data.get("total_cost") is String -> data.getString("total_cost")
                        else -> "0"
                    }
                }
                else -> "0"
            }
        
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatter.parse(tglString) ?: return null
            
            return KwhtmRecord(
                date = date,
                recLWBP = recLWBP,
                recWBP = recWBP,
                recKVARH = recKVARH,
                consumeLWBP = consumeLWBP,
                consumeWBP = consumeWBP,
                consumeKVARH = consumeKVARH,
                costLWBP = costLWBP,
                costWBP = costWBP,
                costKVARH = costKVARH,
                totalCost = totalCost,
                recBy = recBy
            )
        } catch (e: Exception) {
            return null
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
            .url("https://emshotels.net/apiUtility/kwhtm_tarif.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Use default values
                runOnUiThread {
                    trfLWBP = 1000.0
                    trfWBP = 1500.0
                    trfKVARH = 800.0
                    ct = 1.0
                    ppj = 10.0
                    fixcost = 0.0
                    tariffLoaded = true
                    updateCostBreakdown()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val responseBody = resp.body?.string() ?: ""
                    
                    runOnUiThread {
                        try {
                            val json = JSONObject(responseBody)
                            val success = json.optBoolean("success", false)
                            
                            if (success) {
                                val data = json.optJSONObject("data")
                                if (data != null) {
                                    trfLWBP = data.optDouble("trf_lwbp", 1000.0)
                                    trfWBP = data.optDouble("trf_wbp", 1500.0)
                                    trfKVARH = data.optDouble("trf_kvarh", 800.0)
                                    ct = data.optDouble("ct", 1.0)
                                    ppj = data.optDouble("ppj", 10.0)
                                    fixcost = data.optDouble("fixcost", 0.0)
                                } else {
                                    // Default values
                                    trfLWBP = 1000.0
                                    trfWBP = 1500.0
                                    trfKVARH = 800.0
                                    ct = 1.0
                                    ppj = 10.0
                                    fixcost = 0.0
                                }
                            } else {
                                // Default values
                                trfLWBP = 1000.0
                                trfWBP = 1500.0
                                trfKVARH = 800.0
                                ct = 1.0
                                ppj = 10.0
                                fixcost = 0.0
                            }
                        } catch (e: Exception) {
                            // Default values
                            trfLWBP = 1000.0
                            trfWBP = 1500.0
                            trfKVARH = 800.0
                            ct = 1.0
                            ppj = 10.0
                            fixcost = 0.0
                        }
                        
                        tariffLoaded = true
                        updateCostBreakdown()
                    }
                }
            }
        })
    }
    
    private fun updateCostBreakdown() {
        val totals = computeTotals()
        
        totalConsumeLWBPText.text = "${totals.totalLWBP.toInt()} kwh"
        totalConsumeWBPText.text = "${totals.totalWBP.toInt()} kwh"
        costLWBPText.text = formatIDR(totals.costLWBP)
        costWBPText.text = formatIDR(totals.costWBP)
        subtotalText.text = formatIDR(totals.subtotal)
        ppjCostText.text = formatIDR(totals.ppjCost)
        fixedCostText.text = formatIDR(totals.fixedCost)
        grandTotalText.text = formatIDR(totals.grandTotal)
    }
    
    data class KwhtmTotals(
        val totalLWBP: Double,
        val totalWBP: Double,
        val totalKVARH: Double,
        val costLWBP: Double,
        val costWBP: Double,
        val costKVARH: Double,
        val subtotal: Double,
        val ppjCost: Double,
        val fixedCost: Double,
        val grandTotal: Double
    )
    
    private fun computeTotals(): KwhtmTotals {
        val totalLWBP = records.sumOf { it.consumeLWBP.toDoubleOrNull() ?: 0.0 }
        val totalWBP = records.sumOf { it.consumeWBP.toDoubleOrNull() ?: 0.0 }
        val totalKVARH = records.sumOf { it.consumeKVARH.toDoubleOrNull() ?: 0.0 }
        
        val costLWBP = totalLWBP * trfLWBP
        val costWBP = totalWBP * trfWBP
        val costKVARH = totalKVARH * trfKVARH
        val subtotal = costLWBP + costWBP
        val ppjCost = subtotal * (ppj / 100.0)
        val grandTotal = subtotal + ppjCost + fixcost
        
        return KwhtmTotals(totalLWBP, totalWBP, totalKVARH, costLWBP, costWBP, costKVARH, subtotal, ppjCost, fixcost, grandTotal)
    }
    
    private fun calculateTotalCost(record: KwhtmRecord): String {
        val consumeLWBP = record.consumeLWBP.toDoubleOrNull() ?: 0.0
        val consumeWBP = record.consumeWBP.toDoubleOrNull() ?: 0.0
        
        val costLWBP = consumeLWBP * trfLWBP
        val costWBP = consumeWBP * trfWBP
        val subtotal = costLWBP + costWBP
        val ppjCost = subtotal * (ppj / 100.0)
        val grandTotal = subtotal + ppjCost + fixcost
        
        return formatIDRWithoutSymbol(grandTotal)
    }
    
    private fun updateUI() {
        when {
            isLoading -> {
                loadingContainer.visibility = View.VISIBLE
                errorContainer.visibility = View.GONE
                recordsContainer.visibility = View.GONE
            }
            errorMessage != null -> {
                loadingContainer.visibility = View.GONE
                errorContainer.visibility = View.VISIBLE
                recordsContainer.visibility = View.GONE
                errorText.text = errorMessage
            }
            else -> {
                loadingContainer.visibility = View.GONE
                errorContainer.visibility = View.GONE
                recordsContainer.visibility = View.VISIBLE
                updateRecordsDisplay()
                kwhtmChart.updateData(records)
                updateCostBreakdown()
            }
        }
    }
    
    private fun updateRecordsDisplay() {
        recordsContainer.removeAllViews()
        
        // Sort records by date
        val sortedRecords = records.sortedBy { it.date }
        
        for (record in sortedRecords) {
            val recordView = createRecordView(record)
            recordsContainer.addView(recordView)
        }
    }
    
    private fun showAddRecordDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_kwhtm_record)
        
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val lwbpInput = dialog.findViewById<EditText>(R.id.lwbp_input)
        val wbpInput = dialog.findViewById<EditText>(R.id.wbp_input)
        val cancelButton = dialog.findViewById<Button>(R.id.cancel_button)
        val saveButton = dialog.findViewById<Button>(R.id.save_button)
        
        // Set current date
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        datePicker.text = dateFormat.format(calendar.time)
        
        var selectedDate = calendar.time
        
        datePickerContainer.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val newCalendar = Calendar.getInstance()
                    newCalendar.set(year, month, dayOfMonth)
                    selectedDate = newCalendar.time
                    datePicker.text = dateFormat.format(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            val lwbpValue = lwbpInput.text.toString().trim()
            val wbpValue = wbpInput.text.toString().trim()
            
            if (lwbpValue.isEmpty() || wbpValue.isEmpty()) {
                Toast.makeText(this, "Please enter both LWBP and WBP readings", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Check if user is from Engineering department
            if (userDept.lowercase() != "engineering") {
                dialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Access Denied")
                    .setMessage("You are not allowed")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            
            saveKwhtmRecord(selectedDate, lwbpValue, wbpValue)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveKwhtmRecord(date: Date, lwbpRecord: String, wbpRecord: String) {
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("rec_lwbp", lwbpRecord)
            put("rec_wbp", wbpRecord)
            put("rec_kvarh", "0")
            put("rec_by", userName.ifEmpty { "User" })
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/kwhtm_post.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KwhtmpageActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val responseBody = resp.body?.string() ?: ""
                    
                    runOnUiThread {
                        try {
                            val json = JSONObject(responseBody)
                            val success = json.optBoolean("success", false)
                            
                            if (success) {
                                Toast.makeText(this@KwhtmpageActivity, "Record saved successfully", Toast.LENGTH_SHORT).show()
                                loadKwhtmData()
                            } else {
                                val message = json.optString("message", "Save failed")
                                Toast.makeText(this@KwhtmpageActivity, message, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@KwhtmpageActivity, "Invalid response from server", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
    
    private fun showDeleteConfirmDialog(record: KwhtmRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteKwhtmRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteKwhtmRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/kwhtm_delete.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KwhtmpageActivity, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val responseBody = resp.body?.string() ?: ""
                    
                    runOnUiThread {
                        try {
                            val json = JSONObject(responseBody)
                            val success = json.optBoolean("success", false)
                            
                            if (success) {
                                Toast.makeText(this@KwhtmpageActivity, "Record deleted successfully", Toast.LENGTH_SHORT).show()
                                loadKwhtmData()
                            } else {
                                val message = json.optString("message", "Delete failed")
                                Toast.makeText(this@KwhtmpageActivity, message, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@KwhtmpageActivity, "Invalid response from server", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
    
    private fun showError(message: String) {
        errorMessage = message
        updateUI()
    }
    
    private fun formatIDR(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
    
    private fun formatIDRWithoutSymbol(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
}
