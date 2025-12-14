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
import com.sofindo.ems.adapters.FreezerRecordAdapter
import com.sofindo.ems.models.FreezerRecord
import com.sofindo.ems.views.TemperatureChartView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FreezerpageActivity : AppCompatActivity() {
    
    private lateinit var customToolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var addButton: FloatingActionButton
    private lateinit var temperatureChart: TemperatureChartView
    
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
    private var records: MutableList<FreezerRecord> = mutableListOf()
    private var adapter: FreezerRecordAdapter? = null
    private var isLoading = false
    private var errorMessage: String? = null
    
    // User info
    private lateinit var sharedPrefs: SharedPreferences
    private var userName: String = ""
    private var userDept: String = ""
    
    // Freezer specific data
    private var maxTemp: Double = 0.0
    private var maxTempLoaded = false
    
    // Double click delete functionality
    private var lastClickTime: Long = 0
    private val doubleClickDelay = 500L // 500ms delay for double click
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_freezer_page)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        initViews()
        loadParameters()
        setupToolbar()
        setupRecyclerView()
        loadFreezerData()
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
        temperatureChart = findViewById(R.id.temperature_chart)
        
        sharedPrefs = getSharedPreferences("ems_user_prefs", MODE_PRIVATE)
        userName = sharedPrefs.getString("username", "") ?: ""
        userDept = sharedPrefs.getString("dept", "") ?: ""
        
        retryButton.setOnClickListener {
            loadFreezerData()
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
        toolbarTitle.text = utilityName.ifEmpty { "Freezer" }
        
        // Set back button click listener
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = FreezerRecordAdapter(records, { record ->
            handleRecordClick(record)
        }, maxTemp)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun handleRecordClick(record: FreezerRecord) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < doubleClickDelay) {
            // Double click detected
            showDeleteConfirmDialog(record)
        } else {
            // Single click - could add other functionality here if needed
        }
        lastClickTime = currentTime
    }
    
    private fun loadFreezerData() {
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
            .url("https://emshotels.net/apiUtility/freezer_read.php")
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
                                val record = parseFreezerRecord(recordData)
                                if (record != null) {
                                    records.add(record)
                                }
                            }
                            
                            // Sort records by date (oldest first)
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
    
    private fun parseFreezerRecord(data: JSONObject): FreezerRecord? {
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
            
            FreezerRecord(
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
            .url("https://emshotels.net/apiUtility/freezer_tarif.php")
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
        temperatureChart.setData(records, maxTemp)
    }
    
    private fun showAddRecordDialog() {
        // Check if user is from Engineering department
        if (userDept.lowercase() != "engineering") {
            showAccessDeniedAlert()
            return
        }
        
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_freezer_record)
        
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
        val requestBody = JSONObject().apply {
            put("propID", propId)
            put("codeName", codeName)
            put("tgl", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
            put("temp", tempValue)
            put("rec_by", if (userName.isEmpty()) "User" else userName)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://emshotels.net/apiUtility/freezer_post.php")
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
                            loadFreezerData() // Refresh data
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
    
    private fun showDeleteConfirmDialog(record: FreezerRecord) {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(record.date)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Data $dateStr?")
            .setMessage("Data $dateStr will be deleted. Do you want to delete?")
            .setPositiveButton("Yes") { _, _ ->
                deleteFreezerRecord(record.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteFreezerRecord(date: Date) {
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
            .url("https://emshotels.net/apiUtility/freezer_delete.php")
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
                            loadFreezerData() // Refresh data
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