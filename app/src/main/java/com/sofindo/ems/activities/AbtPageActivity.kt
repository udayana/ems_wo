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
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.adapters.ABTRecordAdapter
import com.sofindo.ems.models.ABTRecord
import com.sofindo.ems.views.AbtChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AbtPageActivity : AppCompatActivity() {
    
    private lateinit var customToolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var addButton: FloatingActionButton
    private lateinit var breakdownContainer: LinearLayout
    private lateinit var abtChartView: AbtChartView
    
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
    private var records: MutableList<ABTRecord> = mutableListOf()
    private var adapter: ABTRecordAdapter? = null
    private var isLoading = false
    private var errorMessage: String? = null
    
    // User info
    private lateinit var sharedPrefs: SharedPreferences
    private var userName: String = ""
    private var userDept: String = ""
    
    // Tariff data
    private var tariffLoaded = false
    private var t_blok1: Int = 0
    private var t_blok2: Int = 0
    private var t_blok3: Int = 0
    private var t_blok4: Int = 0
    private var t_trf1: Double = 0.0
    private var t_trf2: Double = 0.0
    private var t_trf3: Double = 0.0
    private var t_trf4: Double = 0.0
    private var t_trf5: Double = 0.0
    private var t_prst: Double = 100.0
    private var t_waterfix: Double = 0.0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abt_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        setupRecyclerView()
        loadABTData()
        loadTariff()
    }
    
    private fun initViews() {
        customToolbar = findViewById(R.id.custom_toolbar)
        backButton = customToolbar.findViewById(R.id.back_button)
        toolbarTitle = customToolbar.findViewById(R.id.toolbar_title)
        recyclerView = findViewById(R.id.recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        errorText = findViewById(R.id.error_text)
        retryButton = findViewById(R.id.retry_button)
        addButton = findViewById(R.id.add_button)
        breakdownContainer = findViewById(R.id.breakdown_container)
        abtChartView = findViewById(R.id.abt_chart_view)
        
        sharedPrefs = getSharedPreferences("ems_user_prefs", MODE_PRIVATE)
        userName = sharedPrefs.getString("username", "") ?: ""
        userDept = sharedPrefs.getString("dept", "") ?: ""
        
        
        retryButton.setOnClickListener {
            loadABTData()
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
        toolbarTitle.text = utilityName.ifEmpty { "ABT-Sumur" }
        
        // Set back button click listener
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ABTRecordAdapter(records) { record ->
            showDeleteConfirmDialog(record)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadABTData() {
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
            .url("https://emshotels.net/apiUtility/abt_read.php")
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
                                val record = parseABTRecord(recordData)
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
    
    private fun parseABTRecord(data: JSONObject): ABTRecord? {
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
            
            ABTRecord(
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
            .url("https://emshotels.net/apiUtility/abt_tarif.php")
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
                            t_blok4 = data.optInt("blok4", 0)
                            t_trf1 = data.optDouble("trfB1", 0.0)
                            t_trf2 = data.optDouble("trfB2", 0.0)
                            t_trf3 = data.optDouble("trfB3", 0.0)
                            t_trf4 = data.optDouble("trfB4", 0.0)
                            t_trf5 = data.optDouble("trfB5", 0.0)
                            t_prst = data.optDouble("prst", 100.0)
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
                progressBar.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                errorText.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            errorMessage != null -> {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
                errorText.text = errorMessage
            }
            records.isEmpty() -> {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.GONE
                errorText.text = "No data available"
            }
            else -> {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                errorText.visibility = View.GONE
                retryButton.visibility = View.GONE
                adapter?.notifyDataSetChanged()
                abtChartView.setData(records)
            }
        }
    }
    
    private fun updateBreakdown() {
        breakdownContainer.removeAllViews()
        
        val totals = computeTotals()
        
        // Add breakdown views
        addBreakdownRow("Total Consume", "${totals.totalConsume.toInt()} m3")
        addBreakdownDivider()
        
        addBreakdownRow("Block 1 (minimum charge: ${t_blok1})", formatIDR(totals.c1))
        addBreakdownRow("Block 2 (${t_blok1 + 1}-${t_blok2})", 
            "${totals.q2.toInt()} x ${formatIDR(t_trf2)} = ${formatIDR(totals.c2)}")
        addBreakdownRow("Block 3 (${t_blok2 + 1}-${t_blok3})", 
            "${totals.q3.toInt()} x ${formatIDR(t_trf3)} = ${formatIDR(totals.c3)}")
        addBreakdownRow("Block 4 (${t_blok3 + 1}-${t_blok4})", 
            "${totals.q4.toInt()} x ${formatIDR(t_trf4)} = ${formatIDR(totals.c4)}")
        addBreakdownRow("Block 5 (>${t_blok4})", 
            "${totals.q5.toInt()} x ${formatIDR(t_trf5)} = ${formatIDR(totals.c5)}")
        
        addBreakdownDivider()
        addBreakdownRow("Subtotal (${t_prst.toInt()}% applied)", formatIDR(totals.subtotal))
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
            TariffData(t_blok1, t_blok2, t_blok3, t_blok4, t_trf1, t_trf2, t_trf3, t_trf4, t_trf5, t_prst, t_waterfix)
        } else {
            TariffData(50, 100, 150, 200, 1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 100.0, 0.0)
        }
        
        val q1 = tariff.blok1.toDouble()
        val q2 = maxOf(0.0, minOf(maxOf(total - tariff.blok1, 0.0), (tariff.blok2 - tariff.blok1).toDouble()))
        val q3 = maxOf(0.0, minOf(maxOf(total - tariff.blok2, 0.0), (tariff.blok3 - tariff.blok2).toDouble()))
        val q4 = maxOf(0.0, minOf(maxOf(total - tariff.blok3, 0.0), (tariff.blok4 - tariff.blok3).toDouble()))
        val q5 = maxOf(0.0, total - tariff.blok4)
        
        val perc = tariff.prst / 100.0
        val c1 = q1 * tariff.trfB1 * perc
        val c2 = q2 * tariff.trfB2 * perc
        val c3 = q3 * tariff.trfB3 * perc
        val c4 = q4 * tariff.trfB4 * perc
        val c5 = q5 * tariff.trfB5 * perc
        
        val subtotal = c1 + c2 + c3 + c4 + c5
        val grandTotal = subtotal + tariff.waterfix
        
        return BreakdownTotals(total, q1, q2, q3, q4, q5, c1, c2, c3, c4, c5, subtotal, tariff.waterfix, grandTotal)
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
        dialog.setContentView(R.layout.dialog_add_record)
        
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
            .url("https://emshotels.net/apiUtility/abt_post.php")
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
                            loadABTData() // Refresh data
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
    
    private fun showDeleteConfirmDialog(record: ABTRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteABTRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteABTRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/abt_delete.php")
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
                            loadABTData() // Refresh data
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
        val blok4: Int,
        val trfB1: Double,
        val trfB2: Double,
        val trfB3: Double,
        val trfB4: Double,
        val trfB5: Double,
        val prst: Double,
        val waterfix: Double
    )
    
    data class BreakdownTotals(
        val totalConsume: Double,
        val q1: Double,
        val q2: Double,
        val q3: Double,
        val q4: Double,
        val q5: Double,
        val c1: Double,
        val c2: Double,
        val c3: Double,
        val c4: Double,
        val c5: Double,
        val subtotal: Double,
        val fixed: Double,
        val grandTotal: Double
    )
}
