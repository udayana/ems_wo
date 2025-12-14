package com.sofindo.ems.activities

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.adapters.KwhtrRecordAdapter
import com.sofindo.ems.models.KwhtrRecord
import com.sofindo.ems.views.KwhtrChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class KwhtrpageActivity : AppCompatActivity() {
    
    // UI Components
    private lateinit var toolbarTitle: TextView
    private lateinit var backButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: FloatingActionButton
    private lateinit var kwhtrChart: KwhtrChartView
    
    // Cost breakdown components
    private lateinit var totalConsumeText: TextView
    private lateinit var baseCostText: TextView
    private lateinit var ctCostText: TextView
    private lateinit var ppjCostText: TextView
    private lateinit var subtotalText: TextView
    private lateinit var fixedCostText: TextView
    private lateinit var grandTotalText: TextView
    
    // Loading/Error components
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    
    // Data
    private val records = mutableListOf<KwhtrRecord>()
    private var adapter: KwhtrRecordAdapter? = null
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
    
    // KWH TR specific data
    private var kwhtarif: Double = 0.0
    private var ct: Double = 0.0
    private var ppj: Double = 0.0
    private var fixcost: Double = 0.0
    private var tariffLoaded = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kwhtr_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupRecyclerView()
        loadKwhtrData()
        loadTariff()
    }
    
    private fun initViews() {
        toolbarTitle = findViewById(R.id.toolbar_title)
        backButton = findViewById(R.id.back_button)
        recyclerView = findViewById(R.id.kwhtr_records_recycler)
        addButton = findViewById(R.id.add_button)
        kwhtrChart = findViewById(R.id.kwhtr_chart)
        
        // Cost breakdown components
        totalConsumeText = findViewById(R.id.total_consume_text)
        baseCostText = findViewById(R.id.base_cost_text)
        ctCostText = findViewById(R.id.ct_cost_text)
        ppjCostText = findViewById(R.id.ppj_cost_text)
        subtotalText = findViewById(R.id.subtotal_text)
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
            loadKwhtrData()
        }
    }
    
    private fun loadParameters() {
        propId = intent.getStringExtra("prop_id") ?: ""
        codeName = intent.getStringExtra("code_name") ?: ""
        utilityName = intent.getStringExtra("utility_name") ?: ""
        satuan = intent.getStringExtra("satuan") ?: "kwh"
        
        toolbarTitle.text = utilityName
        
        // Debug logging
        android.util.Log.d("KwhtrpageActivity", "propId: '$propId'")
        android.util.Log.d("KwhtrpageActivity", "codeName: '$codeName'")
        android.util.Log.d("KwhtrpageActivity", "utilityName: '$utilityName'")
        android.util.Log.d("KwhtrpageActivity", "satuan: '$satuan'")
        android.util.Log.d("KwhtrpageActivity", "userName: '$userName'")
        android.util.Log.d("KwhtrpageActivity", "userDept: '$userDept'")
        
        if (propId.isEmpty() || codeName.isEmpty()) {
            showError("Missing required parameters")
            return
        }
    }
    
    private fun setupRecyclerView() {
        adapter = KwhtrRecordAdapter(records, { record ->
            showDeleteConfirmDialog(record)
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadKwhtrData() {
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
            .url("https://emshotels.net/apiUtility/kwhtr_read.php")
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
                                
                                android.util.Log.d("KwhtrpageActivity", "Total records from API: ${dataArray.length()}")
                                
                                for (i in 0 until dataArray.length()) {
                                    val recordData = dataArray.getJSONObject(i)
                                    val record = parseKwhtrRecord(recordData)
                                    if (record != null) {
                                        records.add(record)
                                        android.util.Log.d("KwhtrpageActivity", "Parsed record $i: ${record.date} - ${record.consume}")
                                    } else {
                                        android.util.Log.d("KwhtrpageActivity", "Failed to parse record $i: $recordData")
                                    }
                                }
                                
                                android.util.Log.d("KwhtrpageActivity", "Successfully parsed records: ${records.size}")
                                
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
    
    private fun parseKwhtrRecord(data: JSONObject): KwhtrRecord? {
        try {
            val tglString = data.getString("tgl")
            val recBy = data.optString("rec_by", "")
        
        val recMeter = when {
            data.has("recMeter") -> {
                when {
                    data.get("recMeter") is Int -> data.getInt("recMeter").toString()
                    data.get("recMeter") is String -> data.getString("recMeter")
                    else -> ""
                }
            }
            else -> ""
        }
        
        val consume = when {
            data.has("consume") -> {
                when {
                    data.get("consume") is Int -> data.getInt("consume").toString()
                    data.get("consume") is String -> data.getString("consume")
                    else -> "0"
                }
            }
            else -> "0"
        }
        
        val cost = when {
            data.has("cost") -> {
                when {
                    data.get("cost") is Int -> data.getInt("cost").toString()
                    data.get("cost") is String -> data.getString("cost")
                    else -> "0"
                }
            }
            else -> "0"
        }
        
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatter.parse(tglString) ?: return null
            
            return KwhtrRecord(
                date = date,
                meterRecord = recMeter,
                consume = consume,
                estimateCost = cost,
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
            .url("https://emshotels.net/apiUtility/kwhtr_tarif.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Use default values
                runOnUiThread {
                    kwhtarif = 1500.0
                    ct = 0.0
                    ppj = 0.0
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
                                    kwhtarif = data.optDouble("kwhtarif", 1500.0)
                                    ct = data.optDouble("ct", 0.0)
                                    ppj = data.optDouble("ppj", 0.0)
                                    fixcost = data.optDouble("fixcost", 0.0)
                                } else {
                                    // Default values
                                    kwhtarif = 1500.0
                                    ct = 0.0
                                    ppj = 0.0
                                    fixcost = 0.0
                                }
                            } else {
                                // Default values
                                kwhtarif = 1500.0
                                ct = 0.0
                                ppj = 0.0
                                fixcost = 0.0
                            }
                        } catch (e: Exception) {
                            // Default values
                            kwhtarif = 1500.0
                            ct = 0.0
                            ppj = 0.0
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
        
        totalConsumeText.text = "${totals.totalConsume.toInt()} kwh"
        baseCostText.text = formatIDR(totals.baseCost)
        ctCostText.text = formatIDR(totals.ctCost)
        ppjCostText.text = formatIDR(totals.ppjCost)
        subtotalText.text = formatIDR(totals.subtotal)
        fixedCostText.text = formatIDR(totals.fixedCost)
        grandTotalText.text = formatIDR(totals.grandTotal)
    }
    
    data class KwhtrTotals(
        val totalConsume: Double,
        val baseCost: Double,
        val ctCost: Double,
        val ppjCost: Double,
        val subtotal: Double,
        val fixedCost: Double,
        val grandTotal: Double
    )
    
    private fun computeTotals(): KwhtrTotals {
        val totalConsume = records.sumOf { it.consume.toDoubleOrNull() ?: 0.0 }
        
        val baseCost = totalConsume * kwhtarif
        val ctCost = baseCost * (ct / 100.0)
        val ppjCost = baseCost * (ppj / 100.0)
        val subtotal = baseCost + ctCost + ppjCost
        val grandTotal = subtotal + fixcost
        
        return KwhtrTotals(totalConsume, baseCost, ctCost, ppjCost, subtotal, fixcost, grandTotal)
    }
    
    private fun updateUI() {
        when {
            isLoading -> {
                loadingContainer.visibility = View.VISIBLE
                errorContainer.visibility = View.GONE
                recyclerView.visibility = View.GONE
            }
            errorMessage != null -> {
                loadingContainer.visibility = View.GONE
                errorContainer.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                errorText.text = errorMessage
            }
            else -> {
                loadingContainer.visibility = View.GONE
                errorContainer.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter?.notifyDataSetChanged()
                kwhtrChart.updateData(records)
                updateCostBreakdown()
            }
        }
    }
    
    private fun showAddRecordDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_kwhtr_record)
        
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val meterInput = dialog.findViewById<EditText>(R.id.meter_input)
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
            val meterValue = meterInput.text.toString().trim()
            
            if (meterValue.isEmpty()) {
                Toast.makeText(this, "Please enter meter reading", Toast.LENGTH_SHORT).show()
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
            
            saveKwhtrRecord(selectedDate, meterValue)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveKwhtrRecord(date: Date, meterRecord: String) {
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("recMeter", meterRecord)
            put("rec_by", userName.ifEmpty { "User" })
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/kwhtr_post.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KwhtrpageActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this@KwhtrpageActivity, "Record saved successfully", Toast.LENGTH_SHORT).show()
                                loadKwhtrData()
                            } else {
                                val message = json.optString("message", "Save failed")
                                Toast.makeText(this@KwhtrpageActivity, message, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@KwhtrpageActivity, "Invalid response from server", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
    
    private fun showDeleteConfirmDialog(record: KwhtrRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteKwhtrRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteKwhtrRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/kwhtr_delete.php")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@KwhtrpageActivity, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this@KwhtrpageActivity, "Record deleted successfully", Toast.LENGTH_SHORT).show()
                                loadKwhtrData()
                            } else {
                                val message = json.optString("message", "Delete failed")
                                Toast.makeText(this@KwhtrpageActivity, message, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@KwhtrpageActivity, "Invalid response from server", Toast.LENGTH_SHORT).show()
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
}