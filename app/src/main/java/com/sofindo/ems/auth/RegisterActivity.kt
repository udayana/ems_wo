package com.sofindo.ems.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.ShapeAppearanceModel
import com.sofindo.ems.R
import com.sofindo.ems.adapters.PropertySearchAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.Property
import com.sofindo.ems.models.PropertySearchResponse
import com.sofindo.ems.models.PropertyValidationResponse
import com.sofindo.ems.services.LocationService
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {
    
    // Property Selection Views
    private lateinit var containerPropertySelection: FrameLayout
    private lateinit var layoutDetectingLocation: LinearLayout
    private lateinit var layoutSelectedProperty: LinearLayout
    private lateinit var tvSelectedProperty: TextView
    private lateinit var layoutManualPropertyInput: LinearLayout
    private lateinit var layoutNoPropertyFound: LinearLayout
    private lateinit var etPropertyName: EditText
    private lateinit var layoutNearbySuggestions: LinearLayout
    private lateinit var layoutSuggestionButtons: LinearLayout
    private lateinit var recyclerPropertySearchResults: RecyclerView
    private lateinit var layoutPropertyNotFoundAlert: LinearLayout
    private lateinit var tvPropertyNotFound: TextView
    private lateinit var layoutSearching: LinearLayout
    private lateinit var tvPropId: TextView // Hidden property ID
    
    // Form Views
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etHandphone: EditText
    private lateinit var etPassword: EditText
    private lateinit var spinnerDepartment: Spinner
    private lateinit var btnSignUp: MaterialButton
    private lateinit var tvError: TextView
    private lateinit var tvBackToLogin: TextView
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var toolbar: Toolbar
    
    // Location Service
    private lateinit var locationService: LocationService
    private var currentLocation: Location? = null
    
    // Property Data
    private var selectedProperty: Property? = null
    private var nearbyProperties: MutableList<Property> = mutableListOf()
    private var propertySearchResults: MutableList<Property> = mutableListOf()
    
    // State
    private var isLoading = false
    private var isPasswordVisible = false
    private var isDetectingLocation = false
    private var locationDetected = false
    private var selectedDepartment = "A & G"
    private var isSettingPropertyText = false // Flag to prevent setting text programmatically
    private var searchDebounceHandler: Handler? = null
    private var searchDebounceRunnable: Runnable? = null
    
    // Validation states
    private var isPropIdValid = false
    private var isNameValid = false
    private var isEmailValid = false
    private var isHandphoneValid = false
    private var isPasswordValid = false
    
    private val departments = listOf(
        "A & G",
        "Engineering", 
        "Housekeeping",
        "F&B",
        "Security",
        "IT",
        "Finance",
        "HR",
        "Sales",
        "Marketing"
    )
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        
        locationService = LocationService.getInstance(this)
        
        initViews()
        setupSpinner()
        setupListeners()
        setupRecyclerView()
        
        // Request location permission and detect location
        if (checkLocationPermission()) {
            detectLocationAndFindProperty()
        } else {
            requestLocationPermission()
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        containerPropertySelection = findViewById(R.id.container_property_selection)
        layoutDetectingLocation = findViewById(R.id.layout_detecting_location)
        layoutSelectedProperty = findViewById(R.id.layout_selected_property)
        tvSelectedProperty = findViewById(R.id.tv_selected_property)
        layoutManualPropertyInput = findViewById(R.id.layout_manual_property_input)
        layoutNoPropertyFound = findViewById(R.id.layout_no_property_found)
        etPropertyName = findViewById(R.id.et_property_name)
        layoutNearbySuggestions = findViewById(R.id.layout_nearby_suggestions)
        layoutSuggestionButtons = findViewById(R.id.layout_suggestion_buttons)
        recyclerPropertySearchResults = findViewById(R.id.recycler_property_search_results)
        layoutPropertyNotFoundAlert = findViewById(R.id.layout_property_not_found_alert)
        tvPropertyNotFound = findViewById(R.id.tv_property_not_found)
        layoutSearching = findViewById(R.id.layout_searching)
        tvPropId = findViewById(R.id.tv_prop_id)
        
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etHandphone = findViewById(R.id.et_handphone)
        etPassword = findViewById(R.id.et_password)
        spinnerDepartment = findViewById(R.id.spinner_department)
        btnSignUp = findViewById(R.id.btn_sign_up)
        tvError = findViewById(R.id.tv_error)
        tvBackToLogin = findViewById(R.id.tv_back_to_login)
        ivPasswordToggle = findViewById(R.id.iv_password_toggle)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Disable sign up button initially
        btnSignUp.isEnabled = false
        
        // Initialize validation states to false
        isPropIdValid = false
        isNameValid = false
        isEmailValid = false
        isHandphoneValid = false
        isPasswordValid = false
    }
    
    private fun setupRecyclerView() {
        try {
            recyclerPropertySearchResults.layoutManager = LinearLayoutManager(this)
            recyclerPropertySearchResults.adapter = PropertySearchAdapter(propertySearchResults) { property ->
                if (!isFinishing && !isDestroyed) {
                    selectProperty(property)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RegisterActivity", "setupRecyclerView error: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = adapter
        
        spinnerDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDepartment = departments[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDepartment = departments[0]
            }
        }
    }
    
    private fun setupListeners() {
        btnSignUp.setOnClickListener {
            if (!isLoading) {
                hideKeyboard()
                handleRegister()
            }
        }
        
        tvBackToLogin.setOnClickListener {
            finish()
        }
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
        
        etHandphone.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && etHandphone.text.isNullOrEmpty()) {
                etHandphone.setText("08")
                etHandphone.setSelection(2)
            }
        }
        
        // Property name search - AUTO SEARCH after 2-3 characters (like iOS)
        // Initialize Handler for debouncing
        searchDebounceHandler = Handler(Looper.getMainLooper())
        
        // Add TextWatcher for auto-search with debouncing
        etPropertyName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Cancel previous debounce
                searchDebounceRunnable?.let { searchDebounceHandler?.removeCallbacks(it) }
                
                // Check if we're setting text programmatically (to prevent loop)
                if (isSettingPropertyText) {
                    android.util.Log.d("SEARCH_DEBUG", "⏭️ Skipping search - isSettingPropertyText = true")
                    return
                }
                
                // Get current text
                val query = s?.toString()?.trim() ?: ""
                android.util.Log.d("SEARCH_DEBUG", "🔵 Text changed: '$query' (length: ${query.length})")
                
                if (query.length >= 2) {
                    // Schedule search after 500ms delay (debouncing)
                    searchDebounceRunnable = Runnable {
                        try {
                            if (!isFinishing && !isDestroyed && !isSettingPropertyText) {
                                val currentText = etPropertyName.text?.toString()?.trim() ?: ""
                                if (currentText.length >= 2 && currentText == query) {
                                    android.util.Log.e("SEARCH_DEBUG", "🔵 Auto-search triggered for: '$currentText'")
                                    searchPropertyByName(currentText)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SEARCH_DEBUG", "❌ Error in debounce runnable: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    searchDebounceHandler?.postDelayed(searchDebounceRunnable!!, 500)
                    android.util.Log.d("SEARCH_DEBUG", "⏱️ Debounce scheduled (500ms)")
                } else {
                    // Clear results if query is too short
                    try {
                        if (!isFinishing && !isDestroyed) {
                            runOnUiThread {
                                try {
                                    propertySearchResults.clear()
                                    (recyclerPropertySearchResults.adapter as? PropertySearchAdapter)?.notifyDataSetChanged()
                                    recyclerPropertySearchResults.visibility = View.GONE
                                    layoutPropertyNotFoundAlert.visibility = View.GONE
                                    layoutSearching.visibility = View.GONE
                                } catch (e: Exception) {
                                    android.util.Log.e("SEARCH_DEBUG", "❌ Error clearing results: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SEARCH_DEBUG", "❌ Error in clear results: ${e.message}")
                    }
                }
            }
            
            override fun afterTextChanged(s: Editable?) {
                // Do nothing
            }
        })
        
        
        // Form validation listeners
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateName(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmail(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etHandphone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateHandphone(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePassword(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
    }
    
    // MARK: - Location & Property Detection
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                detectLocationAndFindProperty()
            } else {
                // Permission denied, show manual input
                showManualInput()
            }
        }
    }
    
    private fun detectLocationAndFindProperty() {
        isDetectingLocation = true
        locationDetected = false
        nearbyProperties.clear()
        selectedProperty = null
        
        showDetectingLocation()
        
        android.util.Log.d("RegisterActivity", "=== Starting location detection ===")
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("RegisterActivity", "Calling locationService.getCurrentLocation()...")
                
                val location = withContext(Dispatchers.IO) {
                    try {
                        locationService.getCurrentLocation()
                    } catch (e: Exception) {
                        android.util.Log.e("RegisterActivity", "LocationService error: ${e.message}", e)
                        e.printStackTrace()
                        throw e
                    }
                }
                
                currentLocation = location
                locationDetected = true
                
                android.util.Log.d("RegisterActivity", "✓ Location obtained: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m")
                android.util.Log.d("RegisterActivity", "Calling findPropertyByLocation()...")
                
                findPropertyByLocation(location.latitude, location.longitude)
            } catch (e: SecurityException) {
                android.util.Log.e("RegisterActivity", "✗ SecurityException: ${e.message}", e)
                e.printStackTrace()
                isDetectingLocation = false
                locationDetected = false
                showManualInput()
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "✗ GPS Error: ${e.message}", e)
                e.printStackTrace()
                isDetectingLocation = false
                locationDetected = false
                // Try to use last known location anyway
                tryLastKnownLocation()
            }
        }
    }
    
    private fun tryLastKnownLocation() {
        android.util.Log.d("RegisterActivity", "=== Attempting to use last known location ===")
        
        lifecycleScope.launch {
            try {
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                var lastKnownLocation: Location? = null
                
                if (ContextCompat.checkSelfPermission(this@RegisterActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("RegisterActivity", "Permission granted, getting last known location...")
                    lastKnownLocation = try {
                        locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    } catch (e: SecurityException) {
                        android.util.Log.e("RegisterActivity", "SecurityException getting GPS location: ${e.message}")
                        null
                    }
                    
                    if (lastKnownLocation == null) {
                        android.util.Log.d("RegisterActivity", "GPS location null, trying network...")
                        lastKnownLocation = try {
                            locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        } catch (e: SecurityException) {
                            android.util.Log.e("RegisterActivity", "SecurityException getting Network location: ${e.message}")
                            null
                        }
                    }
            } else {
                    android.util.Log.w("RegisterActivity", "Location permission not granted")
                }
                
                if (lastKnownLocation != null) {
                    android.util.Log.d("RegisterActivity", "✓ Last known location found: lat=${lastKnownLocation.latitude}, lng=${lastKnownLocation.longitude}")
                    currentLocation = lastKnownLocation
                    locationDetected = true
                    findPropertyByLocation(lastKnownLocation.latitude, lastKnownLocation.longitude)
                } else {
                    android.util.Log.w("RegisterActivity", "✗ No last known location available")
                    android.util.Log.w("RegisterActivity", "Showing manual input - user can search property manually")
                    isDetectingLocation = false
                    locationDetected = false
                    showManualInput()
                }
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "✗ Error getting last known location: ${e.message}", e)
                e.printStackTrace()
                isDetectingLocation = false
                locationDetected = false
                showManualInput()
            }
        }
    }
    
    private fun findPropertyByLocation(latitude: Double, longitude: Double) {
        android.util.Log.d("RegisterActivity", "=== findPropertyByLocation called ===")
        android.util.Log.d("RegisterActivity", "Coordinates: lat=$latitude, lng=$longitude, radius=500m")
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("RegisterActivity", "Calling API: getPropertyByLocation...")
                android.util.Log.d("RegisterActivity", "API URL: https://emshotels.net/apiKu/get_property_by_location.php")
                android.util.Log.d("RegisterActivity", "Parameters: lat=$latitude, lng=$longitude, radius=500.0")
                
                val response = try {
                    RetrofitClient.apiService.getPropertyByLocation(
                        latitude = latitude,
                        longitude = longitude,
                        radius = 500.0
                    )
                } catch (e: Exception) {
                    android.util.Log.e("RegisterActivity", "✗ API Call Error: ${e.message}", e)
                    e.printStackTrace()
                    throw e
                }
                
                android.util.Log.d("RegisterActivity", "✓ API Response received")
                android.util.Log.d("RegisterActivity", "Response type: ${response.javaClass.simpleName}")
                android.util.Log.d("RegisterActivity", "Response content: $response")
                
                isDetectingLocation = false
                
                android.util.Log.d("RegisterActivity", "Parsing response...")
                val searchResponse = try {
                    PropertySearchResponse.fromMap(response)
                } catch (e: Exception) {
                    android.util.Log.e("RegisterActivity", "✗ Parse Error: ${e.message}", e)
                    e.printStackTrace()
                    throw e
                }
                
                android.util.Log.d("RegisterActivity", "✓ Parsed successfully")
                android.util.Log.d("RegisterActivity", "Success: ${searchResponse.success}")
                android.util.Log.d("RegisterActivity", "Properties count: ${searchResponse.properties?.size ?: 0}")
                android.util.Log.d("RegisterActivity", "Message: ${searchResponse.message}")
                
                if (searchResponse.properties.isNullOrEmpty()) {
                    android.util.Log.w("RegisterActivity", "No properties found near location")
                    nearbyProperties.clear()
                    showManualInput()
            } else {
                    val properties = searchResponse.properties ?: emptyList()
                    android.util.Log.d("RegisterActivity", "✓ Found ${properties.size} nearby properties")
                    nearbyProperties = properties.toMutableList()
                    showManualInput()
                    showNearbySuggestions()
                }
            } catch (e: retrofit2.HttpException) {
                android.util.Log.e("RegisterActivity", "✗ HTTP Error: ${e.code()}, ${e.message()}")
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    android.util.Log.e("RegisterActivity", "Error body: $errorBody")
                } catch (ex: Exception) {
                    android.util.Log.e("RegisterActivity", "Could not read error body: ${ex.message}")
                }
                e.printStackTrace()
                isDetectingLocation = false
                nearbyProperties.clear()
                showManualInput()
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "✗ Property Search Error: ${e.message}", e)
                android.util.Log.e("RegisterActivity", "Error type: ${e.javaClass.simpleName}")
                e.printStackTrace()
                isDetectingLocation = false
                nearbyProperties.clear()
                showManualInput()
            }
        }
    }
    
    private fun searchPropertyByName(query: String) {
        android.util.Log.e("SEARCH_DEBUG", "🔵 searchPropertyByName START - query: '$query'")
        
        try {
            if (isFinishing || isDestroyed) {
                android.util.Log.e("SEARCH_DEBUG", "❌ Activity finishing/destroyed - STOPPING search")
                return
            }
            
            android.util.Log.e("SEARCH_DEBUG", "✅ Activity OK, continuing search...")
            
            // Safe parsing - trim dan cek empty sebelum proses
            val trimmedQuery = try {
                query.trim()
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "Error trimming query: ${e.message}", e)
                return
            }
            
            if (trimmedQuery.isEmpty() || trimmedQuery.length < 2) {
                android.util.Log.d("RegisterActivity", "Query invalid: empty or too short (length: ${trimmedQuery.length})")
                propertySearchResults.clear()
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread {
                        try {
                            (recyclerPropertySearchResults.adapter as? PropertySearchAdapter)?.notifyDataSetChanged()
                            layoutPropertyNotFoundAlert.visibility = View.GONE
                            recyclerPropertySearchResults.visibility = View.GONE
                            layoutSearching.visibility = View.GONE
                        } catch (e: Exception) {
                            android.util.Log.e("RegisterActivity", "UI update error: ${e.message}", e)
                        }
                    }
                }
                return
            }
            
            android.util.Log.d("RegisterActivity", "Trimmed query: '$trimmedQuery' (length: ${trimmedQuery.length})")
            
            // Show loading indicator and hide suggestions
            try {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        try {
                            layoutSearching.visibility = View.VISIBLE
                            layoutPropertyNotFoundAlert.visibility = View.GONE
                            // Hide nearby suggestions when searching
                            layoutNearbySuggestions.visibility = View.GONE
                            layoutNoPropertyFound.visibility = View.GONE
                            // Hide recycler view while searching
                            recyclerPropertySearchResults.visibility = View.GONE
                            android.util.Log.e("SEARCH_DEBUG", "🔵 Hidden nearby suggestions, showing loading...")
                        } catch (e: Exception) {
                            android.util.Log.e("RegisterActivity", "Error showing loading: ${e.message}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "Error in runOnUiThread: ${e.message}", e)
            }
            
            // Store query in local variable for use in coroutine
            val finalQuery = trimmedQuery
            
            // Launch coroutine
            lifecycleScope.launch(Dispatchers.Main) {
                android.util.Log.e("SEARCH_DEBUG", "🔵 === COROUTINE START ===")
                android.util.Log.e("SEARCH_DEBUG", "isFinishing: $isFinishing, isDestroyed: $isDestroyed")
                
                try {
                    if (isFinishing || isDestroyed) {
                        android.util.Log.e("SEARCH_DEBUG", "❌ Activity finishing - STOPPING coroutine")
                        return@launch
                    }
                    
                    android.util.Log.e("SEARCH_DEBUG", "✅ Activity OK, calling API...")
                    android.util.Log.e("SEARCH_DEBUG", "🔵 API URL: https://emshotels.net/apiKu/search_property.php")
                    android.util.Log.e("SEARCH_DEBUG", "🔵 Query: '$finalQuery'")
                    
                    // Call API in IO thread
                    val response = withContext(Dispatchers.IO) {
                        android.util.Log.e("SEARCH_DEBUG", "🔵 === API CALL ON IO THREAD ===")
                        try {
                            android.util.Log.e("SEARCH_DEBUG", "🔵 Making API call...")
                            val result = RetrofitClient.apiService.searchProperty(finalQuery)
                            android.util.Log.e("SEARCH_DEBUG", "✅ API call successful")
                            android.util.Log.e("SEARCH_DEBUG", "Response type: ${result.javaClass.simpleName}")
                            android.util.Log.e("SEARCH_DEBUG", "Response size: ${result.size}")
                            result
                        } catch (e: Exception) {
                            android.util.Log.e("SEARCH_DEBUG", "❌❌❌ API CALL ERROR:")
                            android.util.Log.e("SEARCH_DEBUG", "   Message: ${e.message}")
                            android.util.Log.e("SEARCH_DEBUG", "   Type: ${e.javaClass.simpleName}")
                            android.util.Log.e("SEARCH_DEBUG", "   Stack: ${e.stackTraceToString()}")
                            e.printStackTrace()
                            throw e
                        }
                    }
                    
                    android.util.Log.e("SEARCH_DEBUG", "✅ Back to Main thread after API call")
                    android.util.Log.e("SEARCH_DEBUG", "isFinishing: $isFinishing, isDestroyed: $isDestroyed")
                    
                    if (isFinishing || isDestroyed) {
                        android.util.Log.e("SEARCH_DEBUG", "❌ Activity finishing after API call - STOPPING")
                        return@launch
                    }
                    
                    android.util.Log.e("SEARCH_DEBUG", "🔵 Response received, parsing...")
                    android.util.Log.e("SEARCH_DEBUG", "Response size: ${response.size}")
                    android.util.Log.e("SEARCH_DEBUG", "Response keys: ${response.keys}")
                    
                    // Parse response
                    val searchResponse = try {
                        android.util.Log.e("SEARCH_DEBUG", "🔵 Calling PropertySearchResponse.fromMap()...")
                        val parsed = PropertySearchResponse.fromMap(response)
                        android.util.Log.e("SEARCH_DEBUG", "✅ Parsed successfully")
                        android.util.Log.e("SEARCH_DEBUG", "Properties count: ${parsed.properties?.size ?: 0}")
                        parsed
                    } catch (e: Exception) {
                        android.util.Log.e("SEARCH_DEBUG", "❌❌❌ PARSE ERROR:")
                        android.util.Log.e("SEARCH_DEBUG", "   Message: ${e.message}")
                        android.util.Log.e("SEARCH_DEBUG", "   Type: ${e.javaClass.simpleName}")
                        android.util.Log.e("SEARCH_DEBUG", "   Stack: ${e.stackTraceToString()}")
                        e.printStackTrace()
                        // Hide loading on parse error
                        try {
                            if (!isFinishing && !isDestroyed) {
                                layoutSearching.visibility = View.GONE
                            }
                        } catch (ex: Exception) {
                            android.util.Log.e("SEARCH_DEBUG", "❌ Error hiding loading: ${ex.message}")
                        }
                        return@launch
                    }
                    
                    android.util.Log.e("SEARCH_DEBUG", "✅ After parsing, checking activity state...")
                    android.util.Log.e("SEARCH_DEBUG", "isFinishing: $isFinishing, isDestroyed: $isDestroyed")
                    
                    if (isFinishing || isDestroyed) {
                        android.util.Log.e("SEARCH_DEBUG", "❌ Activity finishing after parsing - STOPPING")
                        return@launch
                    }
                    
                    android.util.Log.e("SEARCH_DEBUG", "🔵 Updating UI...")
                    
                    // Update UI - already on Main thread
                    try {
                        if (isFinishing || isDestroyed) {
                            android.util.Log.e("SEARCH_DEBUG", "❌ Activity finishing before UI update - STOPPING")
                            return@launch
                        }
                        
                        android.util.Log.e("SEARCH_DEBUG", "🔵 Hiding loading indicator...")
                        layoutSearching.visibility = View.GONE
                        android.util.Log.e("SEARCH_DEBUG", "✅ Loading indicator hidden")
                        
                        if (searchResponse.properties.isNullOrEmpty()) {
                            android.util.Log.e("SEARCH_DEBUG", "⚠️ No properties found")
                            try {
                                propertySearchResults.clear()
                                val adapter = recyclerPropertySearchResults.adapter as? PropertySearchAdapter
                                if (adapter != null) {
                                    adapter.notifyDataSetChanged()
                                }
                                recyclerPropertySearchResults.visibility = View.GONE
                                tvPropertyNotFound.text = "'$finalQuery' yang anda masukkan belum ada pada database"
                                layoutPropertyNotFoundAlert.visibility = View.VISIBLE
                                // Ensure nearby suggestions are hidden when no search results
                                layoutNearbySuggestions.visibility = View.GONE
                                layoutNoPropertyFound.visibility = View.GONE
                                android.util.Log.e("SEARCH_DEBUG", "✅ UI updated (no results)")
                            } catch (e: Exception) {
                                android.util.Log.e("SEARCH_DEBUG", "❌ Error updating UI (no results): ${e.message}")
                                android.util.Log.e("SEARCH_DEBUG", "   Type: ${e.javaClass.simpleName}")
                                e.printStackTrace()
                                throw e
                            }
                        } else {
                            android.util.Log.e("SEARCH_DEBUG", "✅ Properties found: ${searchResponse.properties?.size}")
                            try {
                                // Ensure adapter exists
                                var adapter = recyclerPropertySearchResults.adapter as? PropertySearchAdapter
                                if (adapter == null) {
                                    android.util.Log.e("SEARCH_DEBUG", "🔵 Creating new adapter...")
                                    val properties = searchResponse.properties ?: emptyList()
                                    propertySearchResults.clear()
                                    propertySearchResults.addAll(properties)
                                    
                                    adapter = PropertySearchAdapter(propertySearchResults) { property ->
                                        try {
                                            if (!isFinishing && !isDestroyed) {
                                                selectProperty(property)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SEARCH_DEBUG", "❌ Select property error: ${e.message}")
                                        }
                                    }
                                    recyclerPropertySearchResults.adapter = adapter
                                    android.util.Log.e("SEARCH_DEBUG", "✅ Adapter created")
                                } else {
                                    android.util.Log.e("SEARCH_DEBUG", "🔵 Updating existing adapter...")
                                    val properties = searchResponse.properties ?: emptyList()
                                    try {
                                        // Update propertySearchResults first
                                        propertySearchResults.clear()
                                        propertySearchResults.addAll(properties)
                                        android.util.Log.e("SEARCH_DEBUG", "✅ propertySearchResults updated: ${propertySearchResults.size} items")
                                        // Then update adapter
                                        adapter.updateList(properties)
                                        android.util.Log.e("SEARCH_DEBUG", "✅ Adapter updated")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SEARCH_DEBUG", "❌ Update list error: ${e.message}")
                                        e.printStackTrace()
                                        // Retry with clear and add
                                        try {
                                            propertySearchResults.clear()
                                            propertySearchResults.addAll(properties)
                                            adapter.updateList(properties)
                                            android.util.Log.e("SEARCH_DEBUG", "✅ Adapter updated (retry)")
                                        } catch (retryEx: Exception) {
                                            android.util.Log.e("SEARCH_DEBUG", "❌ Retry also failed: ${retryEx.message}")
                                            retryEx.printStackTrace()
                                        }
                                    }
                                }
                                
                                android.util.Log.e("SEARCH_DEBUG", "🔵 Setting RecyclerView visibility...")
                                
                                // Check activity state again before setting visibility
                                if (isFinishing || isDestroyed) {
                                    android.util.Log.e("SEARCH_DEBUG", "❌ Activity finishing before setting RecyclerView visibility - STOPPING")
                                    return@launch
                                }
                                
                                try {
                                    // Ensure adapter is set and has data
                                    val finalAdapter = recyclerPropertySearchResults.adapter
                                    val itemCount = finalAdapter?.itemCount ?: 0
                                    android.util.Log.e("SEARCH_DEBUG", "🔵 Adapter item count: $itemCount")
                                    
                                    if (itemCount > 0) {
                                        recyclerPropertySearchResults.visibility = View.VISIBLE
                                        android.util.Log.e("SEARCH_DEBUG", "✅ RecyclerView visibility set to VISIBLE (${itemCount} items)")
                                    } else {
                                        android.util.Log.e("SEARCH_DEBUG", "⚠️ Adapter has no items, keeping RecyclerView hidden")
                                        recyclerPropertySearchResults.visibility = View.GONE
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("SEARCH_DEBUG", "❌ Error setting RecyclerView visibility: ${e.message}")
                                    e.printStackTrace()
                                    // Don't throw - continue
                                }
                                
                                try {
                                    layoutPropertyNotFoundAlert.visibility = View.GONE
                                    android.util.Log.e("SEARCH_DEBUG", "✅ PropertyNotFoundAlert hidden")
                                } catch (e: Exception) {
                                    android.util.Log.e("SEARCH_DEBUG", "❌ Error hiding PropertyNotFoundAlert: ${e.message}")
                                    // Don't throw - continue
                                }
                                
                                // Ensure nearby suggestions are hidden when showing search results
                                try {
                                    layoutNearbySuggestions.visibility = View.GONE
                                    layoutNoPropertyFound.visibility = View.GONE
                                    android.util.Log.e("SEARCH_DEBUG", "✅ Nearby suggestions hidden (showing search results)")
                                } catch (e: Exception) {
                                    android.util.Log.e("SEARCH_DEBUG", "❌ Error hiding nearby suggestions: ${e.message}")
                                    // Don't throw - continue
                                }
                                
                                android.util.Log.e("SEARCH_DEBUG", "✅ UI updated (with results)")
                            } catch (e: Exception) {
                                android.util.Log.e("SEARCH_DEBUG", "❌❌❌ ERROR UPDATING UI (with results):")
                                android.util.Log.e("SEARCH_DEBUG", "   Message: ${e.message}")
                                android.util.Log.e("SEARCH_DEBUG", "   Type: ${e.javaClass.simpleName}")
                                android.util.Log.e("SEARCH_DEBUG", "   Stack: ${e.stackTraceToString()}")
                                e.printStackTrace()
                                // Don't throw - just log and hide loading
                                try {
                                    if (!isFinishing && !isDestroyed) {
                                        layoutSearching.visibility = View.GONE
                                    }
                                } catch (ex: Exception) {
                                    android.util.Log.e("SEARCH_DEBUG", "❌ Error hiding loading: ${ex.message}")
                                }
                                return@launch
                            }
                        }
                        
                        android.util.Log.e("SEARCH_DEBUG", "✅✅✅ === COROUTINE SUCCESS ===")
                        
                        // Double check activity state after all UI updates
                        if (isFinishing || isDestroyed) {
                            android.util.Log.e("SEARCH_DEBUG", "⚠️ Activity finishing after coroutine success")
                        } else {
                            android.util.Log.e("SEARCH_DEBUG", "✅ Activity still OK after coroutine success")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SEARCH_DEBUG", "❌❌❌ UI UPDATE ERROR:")
                        android.util.Log.e("SEARCH_DEBUG", "   Message: ${e.message}")
                        android.util.Log.e("SEARCH_DEBUG", "   Type: ${e.javaClass.simpleName}")
                        android.util.Log.e("SEARCH_DEBUG", "   Stack: ${e.stackTraceToString()}")
                        e.printStackTrace()
                        try {
                            if (!isFinishing && !isDestroyed) {
                                layoutSearching.visibility = View.GONE
                            }
                        } catch (ex: Exception) {
                            android.util.Log.e("SEARCH_DEBUG", "❌ Error hiding loading: ${ex.message}")
                        }
                    }
                } catch (e: retrofit2.HttpException) {
                    android.util.Log.e("SEARCH_DEBUG", "❌❌❌ HTTP EXCEPTION:")
                    android.util.Log.e("SEARCH_DEBUG", "   Code: ${e.code()}")
                    android.util.Log.e("SEARCH_DEBUG", "   Message: ${e.message()}")
                    try {
                        val errorBody = e.response()?.errorBody()?.string()
                        android.util.Log.e("SEARCH_DEBUG", "   Error body: $errorBody")
                    } catch (ex: Exception) {
                        android.util.Log.e("SEARCH_DEBUG", "   Error reading error body: ${ex.message}")
                    }
                    e.printStackTrace()
                    
                    if (!isFinishing && !isDestroyed) {
                        try {
                            layoutSearching.visibility = View.GONE
                            propertySearchResults.clear()
                            recyclerPropertySearchResults.visibility = View.GONE
                            Toast.makeText(this@RegisterActivity, "Error: ${e.message()}", Toast.LENGTH_SHORT).show()
                        } catch (ex: Exception) {
                            android.util.Log.e("SEARCH_DEBUG", "❌ Error handling HTTP error: ${ex.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SEARCH_DEBUG", "❌❌❌❌ FATAL EXCEPTION IN COROUTINE:")
                    android.util.Log.e("SEARCH_DEBUG", "   Message: ${e.message}")
                    android.util.Log.e("SEARCH_DEBUG", "   Type: ${e.javaClass.simpleName}")
                    android.util.Log.e("SEARCH_DEBUG", "   Stack: ${e.stackTraceToString()}")
                    e.printStackTrace()
                    
                    if (!isFinishing && !isDestroyed) {
                        try {
                            layoutSearching.visibility = View.GONE
                            propertySearchResults.clear()
                            recyclerPropertySearchResults.visibility = View.GONE
                            Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } catch (ex: Exception) {
                            android.util.Log.e("SEARCH_DEBUG", "❌ Error handling exception: ${ex.message}")
                        }
                    }
                }
                
                android.util.Log.e("SEARCH_DEBUG", "🔵 === COROUTINE END ===")
            }
            
            android.util.Log.d("RegisterActivity", "=== searchPropertyByName END ===")
        } catch (e: Exception) {
            android.util.Log.e("RegisterActivity", "✗✗✗ FATAL ERROR in searchPropertyByName: ${e.message}", e)
            android.util.Log.e("RegisterActivity", "Fatal error type: ${e.javaClass.simpleName}")
            android.util.Log.e("RegisterActivity", "Stack trace: ${e.stackTraceToString()}")
            e.printStackTrace()
            
            // Try to hide loading
            try {
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread {
                        try {
                            layoutSearching.visibility = View.GONE
                            Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } catch (ex: Exception) {
                            android.util.Log.e("RegisterActivity", "Error showing error message: ${ex.message}", ex)
                        }
                    }
                }
            } catch (ex: Exception) {
                android.util.Log.e("RegisterActivity", "Error in error handler: ${ex.message}", ex)
            }
        }
    }
    
    private fun selectProperty(property: Property) {
        try {
            if (isFinishing || isDestroyed) {
                android.util.Log.w("RegisterActivity", "Cannot select property - activity finishing")
                return
            }
            
            android.util.Log.d("RegisterActivity", "Selecting property: ${property.nama} (${property.id})")
            
            // No pending search to cancel (simplified search)
            
            runOnUiThread {
                try {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    
                    // Set flag to prevent TextWatcher from triggering
                    isSettingPropertyText = true
                    
                    // Set selected property and validation
                    selectedProperty = property
                    isPropIdValid = true
                    tvPropId.text = property.id
                    
                    // Clear search results
                    propertySearchResults.clear()
                    (recyclerPropertySearchResults.adapter as? PropertySearchAdapter)?.notifyDataSetChanged()
                    
                    // Hide search results and suggestions
                    recyclerPropertySearchResults.visibility = View.GONE
                    layoutNearbySuggestions.visibility = View.GONE
                    layoutPropertyNotFoundAlert.visibility = View.GONE
                    layoutNoPropertyFound.visibility = View.GONE
                    layoutSearching.visibility = View.GONE
                    
                    // Set text without triggering TextWatcher
                    etPropertyName.setText(property.nama)
                    // Move cursor to end
                    etPropertyName.setSelection(property.nama.length)
                    
                    // Reset flag after setting text
                    isSettingPropertyText = false
                    
                    // Show selected property view
                    showSelectedProperty()
                    
                    updateSignUpButton()
                    hideKeyboard()
                } catch (e: Exception) {
                    android.util.Log.e("RegisterActivity", "selectProperty UI error: ${e.message}", e)
                    e.printStackTrace()
                    isSettingPropertyText = false // Reset flag on error
                }
            }
            
            // Validate location if manually selected (outside UI thread)
            if (locationDetected && currentLocation != null) {
                validatePropertyLocation(property)
            }
        } catch (e: Exception) {
            android.util.Log.e("RegisterActivity", "selectProperty error: ${e.message}", e)
            e.printStackTrace()
            isSettingPropertyText = false // Reset flag on error
        }
    }
    
    private fun validatePropertyLocation(property: Property) {
        val location = currentLocation ?: return
        
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.validatePropertyLocation(
                    aid = property.id,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radius = 500.0
                )
                
                val validationResponse = PropertyValidationResponse.fromMap(response)
                
                if (!validationResponse.isValid) {
                    val distance = validationResponse.distance ?: 0.0
                    val distanceText = if (distance >= 1000) {
                        "${String.format("%.1f", distance / 1000)} KM"
                    } else {
                        "${distance.toInt()} meter"
                    }
                    
                    val dialog = AlertDialog.Builder(this@RegisterActivity)
                        .setTitle("⚠️ Peringatan")
                        .setMessage("Property '${property.nama}' tidak sesuai dengan lokasi Anda saat ini.\n\nJarak: $distanceText\n\nApakah Anda yakin ingin melanjutkan?")
                        .setPositiveButton("Ya, Lanjutkan") { _, _ -> }
                        .setNegativeButton("Batal") { _, _ ->
                            selectedProperty = null
                            isPropIdValid = false
                            tvPropId.text = ""
                            etPropertyName.setText("")
                            showManualInput()
                            updateSignUpButton()
                        }
                        .create()
                    
                    // Show dialog first
                    dialog.show()
                    
                    // Set background color to light yellow after dialog is shown
                    dialog.window?.let { window ->
                        window.setBackgroundDrawableResource(android.R.color.transparent)
                        // Get the parent view and set background color
                        val parentView = window.decorView.findViewById<View>(android.R.id.content)
                        parentView?.setBackgroundColor(ContextCompat.getColor(this@RegisterActivity, R.color.light_yellow))
                        // Also set background for the dialog content area
                        val dialogView = dialog.findViewById<View>(androidx.appcompat.R.id.custom)
                        if (dialogView != null) {
                            dialogView.setBackgroundColor(ContextCompat.getColor(this@RegisterActivity, R.color.light_yellow))
                        }
                        // Set background for message area
                        val messageView = dialog.findViewById<TextView>(android.R.id.message)
                        messageView?.setBackgroundColor(ContextCompat.getColor(this@RegisterActivity, R.color.light_yellow))
                        // Set background for title area
                        val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
                        titleView?.setBackgroundColor(ContextCompat.getColor(this@RegisterActivity, R.color.light_yellow))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "Validation Error: ${e.message}", e)
            }
        }
    }
    
    // MARK: - UI State Management
    private fun showDetectingLocation() {
        layoutDetectingLocation.visibility = View.VISIBLE
        layoutSelectedProperty.visibility = View.GONE
        layoutManualPropertyInput.visibility = View.GONE
    }
    
    private fun showSelectedProperty() {
        selectedProperty?.let { property ->
            tvSelectedProperty.text = property.nama
            layoutDetectingLocation.visibility = View.GONE
            layoutSelectedProperty.visibility = View.VISIBLE
            layoutManualPropertyInput.visibility = View.GONE
        }
    }
    
    private fun showManualInput() {
        layoutDetectingLocation.visibility = View.GONE
        layoutSelectedProperty.visibility = View.GONE
        layoutManualPropertyInput.visibility = View.VISIBLE
        
        if (nearbyProperties.isEmpty()) {
            layoutNoPropertyFound.visibility = View.VISIBLE
        } else {
            layoutNoPropertyFound.visibility = View.GONE
        }
    }
    
    private fun showNearbySuggestions() {
        if (nearbyProperties.isNotEmpty()) {
            layoutNearbySuggestions.visibility = View.VISIBLE
            layoutSuggestionButtons.removeAllViews()
            
            nearbyProperties.take(2).forEach { property ->
                val button = MaterialButton(this).apply {
                    text = property.nama
                    setTextColor(ContextCompat.getColor(this@RegisterActivity, R.color.black))
                    backgroundTintList = ContextCompat.getColorStateList(this@RegisterActivity, R.color.orange)
                    setPadding(24, 16, 24, 16)
                    // Kotak, tidak rounded - set corner radius ke 0 menggunakan ShapeAppearanceModel
                    shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel.builder()
                        .setAllCornerSizes(0f)
                        .build()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = 8
                    }
                    setOnClickListener {
                        selectProperty(property)
                    }
                }
                layoutSuggestionButtons.addView(button)
            }
        } else {
            layoutNearbySuggestions.visibility = View.GONE
        }
    }
    
    // MARK: - Registration
    private fun handleRegister() {
        val propId = tvPropId.text?.toString()?.trim() ?: ""
        val name = etName.text?.toString()?.trim() ?: ""
        val email = etEmail.text?.toString()?.trim() ?: ""
        val handphone = etHandphone.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        
        val errors = mutableListOf<String>()
        
        if (!isPropIdValid || propId.isEmpty()) {
            errors.add("Property")
        }
        if (!isNameValid || name.isEmpty()) {
            errors.add("Nama")
        }
        if (!isEmailValid || email.isEmpty()) {
            errors.add("Email")
        }
        if (!isHandphoneValid || handphone.isEmpty()) {
            errors.add("Nomor telepon")
        }
        if (!isPasswordValid || password.isEmpty()) {
            errors.add("Password")
        }
        
        if (errors.isNotEmpty()) {
            showError("Silakan lengkapi semua field yang wajib diisi:\n\n• ${errors.joinToString("\n• ")}")
            return
        }
        
        registerWithAPI(propId, name, email, handphone, password)
    }
    
    private fun registerWithAPI(propId: String, name: String, email: String, handphone: String, password: String) {
        isLoading = true
        updateUI()
        
        lifecycleScope.launch {
            try {
                val result = RetrofitClient.apiService.registerUser(
                    aid = propId,
                    nama = name,
                    email = email,
                    telp = handphone,
                    dept = selectedDepartment,
                    password = password
                )
                
                if (result["success"] == true) {
                    @Suppress("UNCHECKED_CAST")
                    val packageInfo = result["package_info"] as? Map<String, Any>
                    val propName = result["prop_name"] as? String ?: selectedProperty?.nama ?: "Unknown Property"
                    showSuccessDialog("", packageInfo, propName)
                    } else {
                    val message = result["message"]?.toString() ?: "Registrasi gagal. Silakan coba lagi."
                    @Suppress("UNCHECKED_CAST")
                    val packageInfo = result["package_info"] as? Map<String, Any>
                    
                    if (packageInfo != null || message.contains("kuota") || message.contains("penuh") || message.contains("upgrade")) {
                        showPackageLimitDialog(message, result)
                    } else {
                        showError(message)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RegisterActivity", "Registration error: ${e.message}", e)
                showError("Registrasi gagal: ${e.message}")
            } finally {
                isLoading = false
                updateUI()
            }
        }
    }
    
    // MARK: - Validation
    private fun validateName(name: String) {
        val trimmed = name.trim()
        isNameValid = trimmed.isNotEmpty() && trimmed.length <= 15
    }
    
    private fun validateEmail(email: String) {
        val trimmed = email.trim()
        isEmailValid = trimmed.isNotEmpty() && isValidEmail(trimmed)
    }
    
    private fun validateHandphone(handphone: String) {
        val trimmed = handphone.trim()
        isHandphoneValid = trimmed.isNotEmpty() && trimmed.startsWith("08") && trimmed.length >= 10
    }
    
    private fun validatePassword(password: String) {
        isPasswordValid = password.isNotEmpty() && password.length >= 6
    }
    
    private fun isValidEmail(email: String): Boolean {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        return emailPattern.matcher(email).matches()
    }
    
    private fun updateSignUpButton() {
        val allValid = isPropIdValid && isNameValid && isEmailValid && isHandphoneValid && isPasswordValid
        btnSignUp.isEnabled = allValid && !isLoading
    }
    
    private fun hideKeyboard() {
        try {
            if (isFinishing || isDestroyed) {
                return
            }
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm != null) {
                val currentFocus = currentFocus
                if (currentFocus != null) {
                    try {
                        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
                        currentFocus.clearFocus()
                    } catch (e: Exception) {
                        android.util.Log.e("RegisterActivity", "Error hiding keyboard: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RegisterActivity", "Error in hideKeyboard: ${e.message}", e)
            // Don't throw - just log the error
        }
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    private fun updateUI() {
        if (isLoading) {
            btnSignUp.text = "Signing up..."
            btnSignUp.isEnabled = false
            hideError()
        } else {
            btnSignUp.text = "SIGN UP"
            updateSignUpButton()
        }
    }
    
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        
        if (isPasswordVisible) {
            etPassword.transformationMethod = null
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            etPassword.transformationMethod = PasswordTransformationMethod()
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
        }
        
        etPassword.setSelection(etPassword.text?.length ?: 0)
    }
    
    private fun showSuccessDialog(@Suppress("UNUSED_PARAMETER") message: String, packageInfo: Map<String, Any>?, propName: String) {
        val packageName = packageInfo?.get("package_name") as? String ?: "Unknown"
        
        val userLimit = when (val value = packageInfo?.get("user_limit")) {
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        
        val currentUsers = when (val value = packageInfo?.get("current_users")) {
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        
        val remainingUsers = when (val value = packageInfo?.get("remaining_users")) {
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        
        val pricing = packageInfo?.get("pricing") as? String ?: "N/A"
        
        @Suppress("UNCHECKED_CAST")
        val features = packageInfo?.get("features") as? Map<String, Any>
        val woFeature = features?.get("wo") as? Boolean ?: false
        val mntFeature = features?.get("mnt") as? Boolean ?: false
        val utilityFeature = features?.get("utility") as? Boolean ?: false
        
        val featuresText = buildString {
            append("Fitur yang tersedia:\n")
            if (woFeature) append("• Work Order ✓\n") else append("• Work Order ✗\n")
            if (mntFeature) append("• Maintenance ✓\n") else append("• Maintenance ✗\n")
            if (utilityFeature) append("• Utility ✓\n") else append("• Utility ✗\n")
        }
        
        val pricingText = if (pricing == "N/A") "" else "• Harga: $pricing\n"
        val fullMessage = "Property: $propName\n\n" +
                "Detail Paket $packageName:\n" +
                pricingText +
                "• Limit User: $userLimit\n" +
                "• User Aktif: $currentUsers\n" +
                "• Sisa Kuota: $remainingUsers\n\n" +
                featuresText
        
        val spannableString = android.text.SpannableString(fullMessage)
        val propertyStart = fullMessage.indexOf("Property: $propName")
        val propertyEnd = propertyStart + "Property: $propName".length
        
        if (propertyStart >= 0 && propertyEnd <= fullMessage.length) {
            spannableString.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                propertyStart,
                propertyEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        AlertDialog.Builder(this)
            .setTitle("✅ Registrasi Berhasil!")
            .setMessage(spannableString)
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showPackageLimitDialog(message: String, result: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val packageInfo = result["package_info"] as? Map<String, Any>
        val packageName = packageInfo?.get("package_name") as? String ?: "Unknown"
        
        val userLimit = when (val value = packageInfo?.get("user_limit")) {
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        
        val currentUsers = when (val value = packageInfo?.get("current_users")) {
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        
        val pricing = packageInfo?.get("pricing") as? String ?: "N/A"
        
        @Suppress("UNCHECKED_CAST")
        val features = packageInfo?.get("features") as? Map<String, Any>
        val woFeature = features?.get("wo") as? Boolean ?: false
        val mntFeature = features?.get("mnt") as? Boolean ?: false
        val utilityFeature = features?.get("utility") as? Boolean ?: false
        
        val featuresText = buildString {
            append("Fitur yang tersedia:\n")
            if (woFeature) append("• Work Order ✓\n") else append("• Work Order ✗\n")
            if (mntFeature) append("• Maintenance ✓\n") else append("• Maintenance ✗\n")
            if (utilityFeature) append("• Utility ✓\n") else append("• Utility ✗\n")
        }
        
        val pricingText = if (pricing == "N/A") "" else "• Harga: $pricing\n"
        AlertDialog.Builder(this)
            .setTitle("📊 Kuota User Penuh")
            .setMessage("$message\n\n" +
                    "Detail Paket $packageName:\n" +
                    pricingText +
                    "• Limit User: $userLimit\n" +
                    "• User Aktif: $currentUsers\n\n" +
                    featuresText + "\n" +
                    "Silakan hubungi administrator untuk upgrade paket atau hapus user yang tidak aktif.")
            .setPositiveButton("Hubungi Admin") { _, _ ->
                showContactAdminDialog()
            }
            .setNegativeButton("Tutup") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showContactAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("📞 Hubungi Administrator")
            .setMessage("Untuk upgrade paket atau bantuan teknis, silakan hubungi:\n\n" +
                    "📧 Email: admin@emshotels.net\n" +
                    "🌐 Website: www.emshotels.net\n\n" +
                    "Atau hubungi administrator properti Anda.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
}

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup debounce handler
        searchDebounceRunnable?.let { searchDebounceHandler?.removeCallbacks(it) }
        searchDebounceHandler = null
        searchDebounceRunnable = null
    }
}