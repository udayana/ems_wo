package com.sofindo.ems.activities

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
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
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.adapters.GasRecordAdapter
import com.sofindo.ems.models.GasRecord
import com.sofindo.ems.views.GasChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class GaspageActivity : AppCompatActivity() {
    
    private lateinit var toolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var gasRecordsRecycler: RecyclerView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var gasChart: GasChartView
    private lateinit var totalGasValue: TextView
    private lateinit var gasPriceValue: TextView
    private lateinit var totalCostValue: TextView
    private lateinit var addButton: FloatingActionButton
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var httpClient: OkHttpClient
    
    private var records: MutableList<GasRecord> = mutableListOf()
    private var gasPrice: Double = 0.0
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
        setContentView(R.layout.activity_gas_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        setupRecyclerView()
        loadGasData()
        loadTariff()
        
        addButton.setOnClickListener {
            showAddRecordDialog()
        }
        
        retryButton.setOnClickListener {
            loadGasData()
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        backButton = toolbar.findViewById(R.id.back_button)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        gasRecordsRecycler = findViewById(R.id.gas_records_recycler)
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        errorMessage = findViewById(R.id.error_message)
        retryButton = findViewById(R.id.retry_button)
        gasChart = findViewById(R.id.gas_chart)
        totalGasValue = findViewById(R.id.total_gas_value)
        gasPriceValue = findViewById(R.id.gas_price_value)
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
        android.util.Log.d("GaspageActivity", "Parameters received:")
        android.util.Log.d("GaspageActivity", "propID: '$propID'")
        android.util.Log.d("GaspageActivity", "codeName: '$codeName'")
        android.util.Log.d("GaspageActivity", "utilityName: '$utilityName'")
        android.util.Log.d("GaspageActivity", "category: '$category'")
        android.util.Log.d("GaspageActivity", "satuan: '$satuan'")
        android.util.Log.d("GaspageActivity", "userName: '$userName'")
        android.util.Log.d("GaspageActivity", "userDept: '$userDept'")
        
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
        gasRecordsRecycler.layoutManager = LinearLayoutManager(this)
        gasRecordsRecycler.adapter = GasRecordAdapter(records) { record ->
            showDeleteConfirmDialog(record)
        }
    }
    
    private fun loadGasData() {
        showLoading(true)
        hideError()
        
        val url = "https://emshotels.net/apiUtility/gas_read.php"
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
                runOnUiThread {
                    showLoading(false)
                    showError("Network error: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    runOnUiThread {
                        showLoading(false)
                        if (it.isSuccessful) {
                            parseGasData(responseBody)
                        } else {
                            showError("Server error: ${it.code}")
                        }
                    }
                }
            }
        })
    }
    
    private fun parseGasData(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val success = json.getBoolean("success")
            
            if (success) {
                val dataArray = json.getJSONArray("data")
                records.clear()
                
                for (i in 0 until dataArray.length()) {
                    val recordData = dataArray.getJSONObject(i)
                    val record = parseGasRecord(recordData)
                    if (record != null) {
                        records.add(record)
                    }
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
    
    private fun parseGasRecord(data: JSONObject): GasRecord? {
        try {
            val tglString = data.getString("tgl")
            val recBy = data.getString("rec_by")
            
            val newGas = when {
                data.has("newGas") && !data.isNull("newGas") -> {
                    when {
                        data.get("newGas") is Int -> data.getInt("newGas").toString()
                        data.get("newGas") is String -> data.getString("newGas")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val totalGas = when {
                data.has("totGas") && !data.isNull("totGas") -> {
                    when {
                        data.get("totGas") is Int -> data.getInt("totGas").toString()
                        data.get("totGas") is String -> data.getString("totGas")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val totalCost = when {
                data.has("priceTotal") && !data.isNull("priceTotal") -> {
                    when {
                        data.get("priceTotal") is Double -> data.getDouble("priceTotal").toString()
                        data.get("priceTotal") is String -> data.getString("priceTotal")
                        else -> ""
                    }
                }
                else -> ""
            }
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(tglString) ?: return null
            
            return GasRecord(
                date = date,
                newGas = newGas,
                totalGas = totalGas,
                totalCost = totalCost,
                recBy = recBy
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun loadTariff() {
        val url = "https://emshotels.net/apiUtility/gas_tarif.php"
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
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    runOnUiThread {
                        parseTariff(responseBody)
                    }
                }
            }
        })
    }
    
    private fun parseTariff(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val success = json.getBoolean("success")
            
            if (success) {
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
                
                gasPrice = priceString.toDoubleOrNull() ?: 0.0
                tariffLoaded = true
                updateCostBreakdown()
            }
        } catch (e: Exception) {
            // Ignore tariff parsing errors
        }
    }
    
    private fun updateUI() {
        gasRecordsRecycler.adapter?.notifyDataSetChanged()
        updateChart()
        updateCostBreakdown()
    }
    
    private fun updateChart() {
        gasChart.updateData(records)
    }
    
    private fun updateCostBreakdown() {
        val totals = computeTotals()
        
        totalGasValue.text = "${totals.first.toInt()} tabung"
        gasPriceValue.text = formatIDR(totals.second)
        totalCostValue.text = formatIDR(totals.third)
    }
    
    private fun computeTotals(): Triple<Double, Double, Double> {
        val totalGas = records.sumOf { it.newGas.toDoubleOrNull() ?: 0.0 }
        val totalCost = records.sumOf { it.totalCost.toDoubleOrNull() ?: 0.0 }
        
        return Triple(totalGas, gasPrice, totalCost)
    }
    
    private fun showAddRecordDialog() {
        // Check if user is from Engineering department
        if (userDept.lowercase() != "engineering") {
            showAccessDeniedAlert()
            return
        }
        
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_gas_record)
        
        val datePicker = dialog.findViewById<TextView>(R.id.date_picker)
        val datePickerContainer = dialog.findViewById<LinearLayout>(R.id.date_picker_container)
        val gasInput = dialog.findViewById<EditText>(R.id.gas_input)
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
            val gasValue = gasInput.text.toString().trim()
            if (gasValue.isNotEmpty()) {
                saveNewRecord(calendar.time, gasValue)
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveNewRecord(date: Date, gasValue: String) {
        // Check if user is from Engineering department
        if (userDept.lowercase() != "engineering") {
            showAccessDeniedAlert()
            return
        }
        
        val url = "https://emshotels.net/apiUtility/gas_post.php"
        val requestBody = JSONObject().apply {
            put("propID", propID)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("newGas", gasValue)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        showLoading(true)
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    showError("Network error: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    runOnUiThread {
                        showLoading(false)
                        if (it.isSuccessful) {
                            loadGasData() // Refresh data
                        } else {
                            showError("Failed to save record")
                        }
                    }
                }
            }
        })
    }
    
    private fun showDeleteConfirmDialog(record: GasRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteGasRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteGasRecord(date: Date) {
        val url = "https://emshotels.net/apiUtility/gas_delete.php"
        val requestBody = JSONObject().apply {
            put("propID", propID)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        showLoading(true)
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    showError("Network error: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    runOnUiThread {
                        showLoading(false)
                        if (it.isSuccessful) {
                            loadGasData() // Refresh data
                        } else {
                            showError("Failed to delete record")
                        }
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
        gasRecordsRecycler.visibility = View.GONE
    }
    
    private fun hideError() {
        errorContainer.visibility = View.GONE
        gasRecordsRecycler.visibility = View.VISIBLE
    }
    
    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        gasRecordsRecycler.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun formatIDR(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
    
    private fun formatNumber(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(value)
    }
}