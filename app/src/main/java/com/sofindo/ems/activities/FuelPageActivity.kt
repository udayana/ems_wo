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
import com.sofindo.ems.adapters.FuelRecordAdapter
import com.sofindo.ems.models.FuelRecord
import com.sofindo.ems.views.FuelChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FuelPageActivity : AppCompatActivity() {
    
    private lateinit var customToolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var addButton: FloatingActionButton
    private lateinit var fuelChart: FuelChartView
    
    // Cost breakdown views
    private lateinit var totalFuelText: TextView
    private lateinit var fuelPriceText: TextView
    private lateinit var totalCostText: TextView
    
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
    private var records: MutableList<FuelRecord> = mutableListOf()
    private var adapter: FuelRecordAdapter? = null
    private var isLoading = false
    private var errorMessage: String? = null
    
    // User info
    private lateinit var sharedPrefs: SharedPreferences
    private var userName: String = ""
    private var userDept: String = ""
    
    // Fuel specific data
    private var fuelPrice: Double = 0.0
    private var fuelPriceLoaded = false
    
    // Double click delete functionality
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuel_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        setupRecyclerView()
        loadFuelData()
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
        fuelChart = findViewById(R.id.fuel_chart)
        
        // Cost breakdown views
        totalFuelText = findViewById(R.id.total_fuel_text)
        fuelPriceText = findViewById(R.id.fuel_price_text)
        totalCostText = findViewById(R.id.total_cost_text)
        
        sharedPrefs = getSharedPreferences("ems_user_prefs", MODE_PRIVATE)
        userName = sharedPrefs.getString("username", "") ?: ""
        userDept = sharedPrefs.getString("dept", "") ?: ""
        
        retryButton.setOnClickListener {
            loadFuelData()
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
        toolbarTitle.text = utilityName.ifEmpty { "Fuel" }
        
        // Set back button click listener
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = FuelRecordAdapter(records, { record ->
            showDeleteConfirmDialog(record)
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    
    private fun loadFuelData() {
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
            .url("https://emshotels.net/apiUtility/fuel_read.php")
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
                                val record = parseFuelRecord(recordData)
                                if (record != null) {
                                    records.add(record)
                                }
                            }
                            
                            // Sort records by date (oldest first)
                            records.sortBy { it.date }
                            updateChart()
                            updateCostBreakdown()
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
    
    private fun parseFuelRecord(data: JSONObject): FuelRecord? {
        return try {
            val tglString = data.getString("tgl")
            val recBy = data.getString("rec_by")
            
            val newFuel = when {
                data.has("newFuel") && !data.isNull("newFuel") -> {
                    val fuel = data.optString("newFuel", "")
                    when {
                        fuel == "0" || fuel == "-999" || fuel == "-999.00" || fuel.isEmpty() -> ""
                        else -> fuel
                    }
                }
                else -> ""
            }
            
            val totalFuel = when {
                data.has("totfuel") && !data.isNull("totfuel") -> {
                    val total = data.optString("totfuel", "")
                    when {
                        total == "0" || total == "-999" || total == "-999.00" || total.isEmpty() -> ""
                        else -> total
                    }
                }
                else -> ""
            }
            
            val totalCost = when {
                data.has("priceTotal") && !data.isNull("priceTotal") -> {
                    val cost = data.optString("priceTotal", "")
                    when {
                        cost == "0" || cost == "-999" || cost == "-999.00" || cost.isEmpty() -> ""
                        else -> cost
                    }
                }
                else -> ""
            }
            
            // Parse date
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatter.parse(tglString) ?: return null
            
            FuelRecord(
                date = date,
                newFuel = newFuel,
                totalFuel = totalFuel,
                totalCost = totalCost,
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
            .url("https://emshotels.net/apiUtility/fuel_tarif.php")
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
                            fuelPrice = data.optDouble("price", 0.0)
                            fuelPriceLoaded = true
                            updateCostBreakdown()
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
            }
        }
    }
    
    private fun updateChart() {
        fuelChart.setData(records)
    }
    
    private fun updateCostBreakdown() {
        val totals = computeTotals()
        
        totalFuelText.text = "${totals.first.toInt()} ltr"
        fuelPriceText.text = formatIDR(totals.second)
        totalCostText.text = formatIDR(totals.third)
    }
    
    private fun computeTotals(): Triple<Double, Double, Double> {
        val totalFuel = records.sumOf { it.newFuel.toDoubleOrNull() ?: 0.0 }
        val totalCost = records.sumOf { it.totalCost.toDoubleOrNull() ?: 0.0 }
        
        return Triple(totalFuel, fuelPrice, totalCost)
    }
    
    private fun formatIDR(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
    
    private fun showAddRecordDialog() {
        // Check if user is from Engineering department
        if (userDept.lowercase() != "engineering") {
            showAccessDeniedAlert()
            return
        }
        
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_fuel_record)
        
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val fuelInput = dialog.findViewById<EditText>(R.id.fuel_input)
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
            val fuelValue = fuelInput.text.toString().trim()
            if (fuelValue.isNotEmpty()) {
                saveNewRecord(calendar.time, fuelValue)
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveNewRecord(date: Date, fuelValue: String) {
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("newFuel", fuelValue)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/fuel_post.php")
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
                            loadFuelData() // Refresh data
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
    
    private fun showDeleteConfirmDialog(record: FuelRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteFuelRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteFuelRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/fuel_delete.php")
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
                            loadFuelData() // Refresh data
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