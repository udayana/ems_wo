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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.adapters.PDAMRecordAdapter
import com.sofindo.ems.models.PDAMRecord
import com.sofindo.ems.views.PdamChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PdampageActivity : AppCompatActivity() {
    
    private lateinit var customToolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var addButton: FloatingActionButton
    private lateinit var pdamChart: PdamChartView
    private lateinit var breakdownContainer: LinearLayout
    
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
    private var records: MutableList<PDAMRecord> = mutableListOf()
    private var adapter: PDAMRecordAdapter? = null
    private var isLoading = false
    private var errorMessage: String? = null
    
    // User info
    private lateinit var sharedPrefs: SharedPreferences
    private var userName: String = ""
    private var userDept: String = ""
    
    // Tariff data (PDAM has 3 blocks only)
    private var tariffLoaded = false
    private var t_blok1: Int = 0
    private var t_blok2: Int = 0
    private var t_blok3: Int = 0
    private var t_trf1: Double = 0.0
    private var t_trf2: Double = 0.0
    private var t_trf3: Double = 0.0
    private var t_waterfix: Double = 0.0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdam_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        setupRecyclerView()
        loadPDAMData()
        loadTariff()
    }
    
    private fun initViews() {
        customToolbar = findViewById(R.id.custom_toolbar)
        backButton = customToolbar.findViewById(R.id.back_button)
        toolbarTitle = customToolbar.findViewById(R.id.toolbar_title)
        recyclerView = findViewById(R.id.recycler_view)
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_text)
        retryButton = findViewById(R.id.retry_button)
        addButton = findViewById(R.id.add_button)
        pdamChart = findViewById(R.id.pdam_chart)
        breakdownContainer = findViewById(R.id.breakdown_container)
        
        sharedPrefs = getSharedPreferences("ems_user_prefs", MODE_PRIVATE)
        userName = sharedPrefs.getString("username", "") ?: ""
        userDept = sharedPrefs.getString("dept", "") ?: ""
        
        retryButton.setOnClickListener {
            loadPDAMData()
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
        toolbarTitle.text = utilityName.ifEmpty { "PDAM" }
        
        // Set back button click listener
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = PDAMRecordAdapter(records) { record ->
            showDeleteConfirmDialog(record)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadPDAMData() {
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
            .url("https://emshotels.net/apiUtility/pdam_read.php")
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
                                val record = parsePDAMRecord(recordData)
                                if (record != null) {
                                    records.add(record)
                                }
                            }
                            
                            // Sort records by date (oldest first)
                            records.sortBy { it.date }
                            updateBreakdown()
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
    
    private fun parsePDAMRecord(data: JSONObject): PDAMRecord? {
        return try {
            val tglString = data.getString("tgl")
            val recBy = data.getString("rec_by")
            
            val recMeter = when {
                data.has("recMeter") && !data.isNull("recMeter") -> {
                    data.optString("recMeter", "")
                }
                else -> ""
            }
            
            val consume = when {
                data.has("consume") && !data.isNull("consume") -> {
                    data.optString("consume", "0")
                }
                else -> "0"
            }
            
            val cost = when {
                data.has("cost") && !data.isNull("cost") -> {
                    data.optString("cost", "0")
                }
                else -> "0"
            }
            
            // Parse date
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatter.parse(tglString) ?: return null
            
            PDAMRecord(
                date = date,
                meterRecord = recMeter,
                consume = consume,
                estimateCost = cost,
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
            .url("https://emshotels.net/apiUtility/pdam_tarif.php")
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
                            
                            t_blok1 = data.optInt("blok1", 0)
                            t_blok2 = data.optInt("blok2", 0)
                            t_blok3 = data.optInt("blok3", 0)
                            t_trf1 = data.optDouble("trfB1", 0.0)
                            t_trf2 = data.optDouble("trfB2", 0.0)
                            t_trf3 = data.optDouble("trfB3", 0.0)
                            t_waterfix = data.optDouble("waterfixcost", 0.0)
                            
                            tariffLoaded = true
                            updateBreakdown()
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
                loadingContainer.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                errorContainer.visibility = View.GONE
            }
            errorMessage != null -> {
                loadingContainer.visibility = View.GONE
                recyclerView.visibility = View.GONE
                errorContainer.visibility = View.VISIBLE
                errorText.text = errorMessage
            }
            records.isEmpty() -> {
                loadingContainer.visibility = View.GONE
                recyclerView.visibility = View.GONE
                errorContainer.visibility = View.VISIBLE
                errorText.text = "No data available"
                retryButton.visibility = View.GONE
            }
            else -> {
                loadingContainer.visibility = View.GONE
                errorContainer.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter?.notifyDataSetChanged()
                updateChart()
            }
        }
    }
    
    private fun updateChart() {
        pdamChart.setData(records)
    }
    
    private fun updateBreakdown() {
        breakdownContainer.removeAllViews()
        
        val totals = computeTotals()
        
        // Add breakdown views
        addBreakdownRow("Total Consume", "${totals.totalConsume.toInt()} m3")
        addBreakdownDivider()
        
        addBreakdownRow("Block 1 (0 - ${t_blok1})", formatIDR(totals.c1))
        addBreakdownRow("Block 2 (${t_blok1 + 1} - ${t_blok2})", 
            "${totals.q2.toInt()} x ${formatIDR(t_trf2)} = ${formatIDR(totals.c2)}")
        addBreakdownRow("Block 3 (> ${t_blok2})", 
            "${totals.q3.toInt()} x ${formatIDR(t_trf3)} = ${formatIDR(totals.c3)}")
        
        addBreakdownDivider()
        addBreakdownRow("Subtotal", formatIDR(totals.subtotal))
        addBreakdownRow("Fixed Cost", formatIDR(totals.fixed))
        addBreakdownDivider()
        addGrandTotalRow("Grand Total", formatIDR(totals.grandTotal))
    }
    
    private fun addBreakdownRow(label: String, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_breakdown_row, breakdownContainer, false)
        val labelText = row.findViewById<TextView>(R.id.label_text)
        val valueText = row.findViewById<TextView>(R.id.value_text)
        
        labelText.text = label
        valueText.text = value
        
        breakdownContainer.addView(row)
    }
    
    private fun addGrandTotalRow(label: String, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_breakdown_row, breakdownContainer, false)
        val labelText = row.findViewById<TextView>(R.id.label_text)
        val valueText = row.findViewById<TextView>(R.id.value_text)
        
        labelText.text = label
        labelText.textSize = 12f
        labelText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        valueText.text = value
        valueText.textSize = 12f
        valueText.setTypeface(null, android.graphics.Typeface.BOLD)
        valueText.setTextColor(getColor(R.color.primary_color))
        
        breakdownContainer.addView(row)
    }
    
    private fun addBreakdownDivider() {
        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.divider_height)
        )
        divider.setBackgroundColor(resources.getColor(R.color.divider_color, null))
        breakdownContainer.addView(divider)
    }
    
    private fun computeTotals(): BreakdownTotals {
        val total = records.sumOf { it.consume.toDoubleOrNull() ?: 0.0 }
        
        val tariff = if (tariffLoaded) {
            TariffData(t_blok1, t_blok2, t_blok3, t_trf1, t_trf2, t_trf3, t_waterfix)
        } else {
            TariffData(50, 100, 150, 1000.0, 1500.0, 2000.0, 0.0)
        }
        
        // PDAM calculation (3 blocks only)
        val q1 = tariff.blok1.toDouble() // Block 1: 0 - blok1 (minimum charge)
        
        val q2 = when {
            total > tariff.blok2 -> (tariff.blok2 - tariff.blok1).toDouble()
            total >= tariff.blok1 && total <= tariff.blok2 -> total - tariff.blok1
            else -> 0.0
        }
        
        val q3 = if (total > tariff.blok2) total - tariff.blok2 else 0.0
        
        val c1 = q1 * tariff.trfB1
        val c2 = q2 * tariff.trfB2
        val c3 = q3 * tariff.trfB3
        
        val subtotal = c1 + c2 + c3
        val grandTotal = subtotal + tariff.waterfix
        
        return BreakdownTotals(total, q1, q2, q3, c1, c2, c3, subtotal, tariff.waterfix, grandTotal)
    }
    
    private fun formatIDR(value: Double): String {
        val formatter = java.text.NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return formatter.format(value)
    }
    
    private fun showAddRecordDialog() {
        // Check if user is from Engineering department
        if (userDept.lowercase() != "engineering") {
            showAccessDeniedAlert()
            return
        }
        
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_pdam_record)
        
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val meterInput = dialog.findViewById<EditText>(R.id.meter_input)
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
            val meterValue = meterInput.text.toString().trim()
            if (meterValue.isNotEmpty()) {
                saveNewRecord(calendar.time, meterValue)
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveNewRecord(date: Date, meterValue: String) {
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("recMeter", meterValue)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/pdam_post.php")
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
                            loadPDAMData() // Refresh data
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
    
    private fun showDeleteConfirmDialog(record: PDAMRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deletePDAMRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deletePDAMRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/pdam_delete.php")
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
                            loadPDAMData() // Refresh data
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
    
    // Data classes
    data class TariffData(
        val blok1: Int,
        val blok2: Int,
        val blok3: Int,
        val trfB1: Double,
        val trfB2: Double,
        val trfB3: Double,
        val waterfix: Double
    )
    
    data class BreakdownTotals(
        val totalConsume: Double,
        val q1: Double,
        val q2: Double,
        val q3: Double,
        val c1: Double,
        val c2: Double,
        val c3: Double,
        val subtotal: Double,
        val fixed: Double,
        val grandTotal: Double
    )
}