package com.sofindo.ems.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.sofindo.ems.MainActivity
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TambahWOFragment : Fragment() {
    
    // UI Components
    private lateinit var etJob: EditText
    private lateinit var etLocation: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerDepartment: Spinner
    private lateinit var spinnerPriority: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var btnPhoto: Button
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var layoutPhotoSection: LinearLayout
    private lateinit var layoutLocationSuggestions: LinearLayout
    
    // Data
    private var categories = mutableListOf<String>()
    private var departments = mutableListOf<String>()
    private var allLocations = mutableListOf<String>()
    private var locationSuggestions = mutableListOf<String>()
    private val priorities = listOf("Low", "Medium", "High")
    
    // State
    private var currentPropID: String? = null
    private var username: String? = null
    private var selectedImageFile: File? = null
    private var isLoading = false
    private var isLoadingData = true
    private var isUploading = false
    private var uploadProgress = 0f
    
    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            result.data?.extras?.getParcelable<Bitmap>("data")?.let { bitmap ->
                // Handle camera result with bitmap
                try {
                    selectedImageFile = createImageFile()
                    val outputStream = FileOutputStream(selectedImageFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.close()
                    updatePhotoUI()
                } catch (e: Exception) {
                    showSnackbar("Failed to save camera image: ${e.message}", false)
                }
            }
        }
    }
    
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleGalleryImage(uri)
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showSnackbar("Camera permission is required to take photos", false)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tambah_wo, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        initializeData()
    }
    
    private fun initViews(view: View) {
        etJob = view.findViewById(R.id.et_job)
        etLocation = view.findViewById(R.id.et_location)
        spinnerCategory = view.findViewById(R.id.spinner_category)
        spinnerDepartment = view.findViewById(R.id.spinner_department)
        spinnerPriority = view.findViewById(R.id.spinner_priority)
        btnSubmit = view.findViewById(R.id.btn_submit)
        btnCancel = view.findViewById(R.id.btn_cancel)
        btnPhoto = view.findViewById(R.id.btn_photo)
        ivPhotoPreview = view.findViewById(R.id.iv_photo_preview)
        progressBar = view.findViewById(R.id.progress_bar)
        tvProgress = view.findViewById(R.id.tv_progress)
        layoutPhotoSection = view.findViewById(R.id.layout_photo_section)
        layoutLocationSuggestions = view.findViewById(R.id.layout_location_suggestions)
    }
    
    private fun setupListeners() {
        btnSubmit.setOnClickListener {
            submitWorkOrder()
        }
        
        btnCancel.setOnClickListener {
            clearForm()
            navigateToOutbox()
        }
        
        btnPhoto.setOnClickListener {
            showImageSourceDialog()
        }
        
        // Location autocomplete
        etLocation.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterLocations(s?.toString() ?: "")
            }
        })
    }
    
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                currentPropID = user?.propID
                username = user?.username ?: user?.email
                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    loadMasterData()
                } else {
                    showSnackbar("User data not found", false)
                }
            } catch (e: Exception) {
                showSnackbar("Failed to load data: ${e.message}", false)
            }
        }
    }
    
    private fun loadMasterData() {
        lifecycleScope.launch {
            try {
                // Load data with timeout handling
                val categoriesResult = withContext(Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.getCategories(currentPropID!!)
                    } catch (e: Exception) {
                        listOf("General", "Maintenance", "Housekeeping", "Security")
                    }
                }
                
                val departmentsResult = withContext(Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.getDepartments(currentPropID!!)
                    } catch (e: Exception) {
                        listOf("Engineering", "Housekeeping", "Security", "Front Office")
                    }
                }
                
                val locationsResult = withContext(Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.getLocations(currentPropID!!)
                    } catch (e: Exception) {
                        listOf("Lobby", "Room", "Kitchen", "Pool", "Garden")
                    }
                }
                
                if (isAdded) {
                    // Set categories
                    categories.clear()
                    categories.addAll(categoriesResult)
                    if (!categories.contains("General")) {
                        categories.add(0, "General")
                    }
                    
                    // Set departments
                    departments.clear()
                    departments.addAll(departmentsResult)
                    
                    // Set locations
                    allLocations.clear()
                    allLocations.addAll(locationsResult)
                    
                    setupSpinners()
                    isLoadingData = false
                    updateUI()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    showSnackbar("Failed to load master data: ${e.message}", false)
                    // Set fallback data
                    categories = mutableListOf("General", "Maintenance", "Housekeeping", "Security")
                    departments = mutableListOf("Engineering", "Housekeeping", "Security", "Front Office")
                    allLocations = mutableListOf("Lobby", "Room", "Kitchen", "Pool", "Garden")
                    setupSpinners()
                    isLoadingData = false
                    updateUI()
                }
            }
        }
    }
    
    private fun setupSpinners() {
        // Category Spinner
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter
        
        // Department Spinner
        val departmentAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, departments)
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = departmentAdapter
        
        // Priority Spinner
        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, priorities)
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
        
        // Set defaults
        if (categories.isNotEmpty()) {
            spinnerCategory.setSelection(0) // General
        }
        
        // Set department spinner default to first item (user must select destination department)
        if (departments.isNotEmpty()) {
            spinnerDepartment.setSelection(0)
        }
        
        spinnerPriority.setSelection(0) // Low
    }
    
    private fun filterLocations(query: String) {
        if (query.isEmpty()) {
            locationSuggestions.clear()
            layoutLocationSuggestions.visibility = View.GONE
            return
        }
        
        locationSuggestions.clear()
        locationSuggestions.addAll(
            allLocations.filter { 
                it.lowercase().contains(query.lowercase()) 
            }
        )
        
        if (locationSuggestions.isNotEmpty()) {
            setupLocationSuggestions()
            layoutLocationSuggestions.visibility = View.VISIBLE
        } else {
            layoutLocationSuggestions.visibility = View.GONE
        }
    }
    
    private fun setupLocationSuggestions() {
        layoutLocationSuggestions.removeAllViews()
        
        locationSuggestions.forEach { location ->
            val textView = TextView(requireContext()).apply {
                text = location
                setPadding(32, 16, 32, 16)
                setTextColor(resources.getColor(R.color.black, null))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = resources.getDrawable(R.drawable.suggestions_background, null)
                setOnClickListener {
                    etLocation.setText(location)
                    layoutLocationSuggestions.visibility = View.GONE
                }
            }
            layoutLocationSuggestions.addView(textView)
        }
    }
    
    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Take Photo From")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> openGallery()
                }
            }
            .show()
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
    
    private fun handleImageResult() {
        // Handle camera result
        try {
            selectedImageFile = createImageFile()
            updatePhotoUI()
        } catch (e: Exception) {
            showSnackbar("Failed to process camera image: ${e.message}", false)
        }
    }
    
    private fun handleGalleryImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            selectedImageFile = createImageFile()
            val outputStream = FileOutputStream(selectedImageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.close()
            
            updatePhotoUI()
        } catch (e: Exception) {
            showSnackbar("Failed to load image: ${e.message}", false)
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    private fun updatePhotoUI() {
        selectedImageFile?.let { file ->
            try {
                Glide.with(this)
                    .load(file)
                    .placeholder(R.drawable.photo_preview_background)
                    .error(R.drawable.photo_preview_background)
                    .into(ivPhotoPreview)
                
                            ivPhotoPreview.visibility = View.VISIBLE
            btnPhoto.text = "📷 Upload"
            } catch (e: Exception) {
                showSnackbar("Failed to display image preview: ${e.message}", false)
            }
        }
    }
    
    private fun submitWorkOrder() {
        val job = etJob.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val category = spinnerCategory.selectedItem.toString()
        val selectedDepartment = spinnerDepartment.selectedItem.toString()  // Department yang dipilih user
        val priority = spinnerPriority.selectedItem.toString()
        
        // Validation
        if (job.isEmpty()) {
            showSnackbar("Job description is required", false)
            return
        }
        
        if (location.isEmpty()) {
            showSnackbar("Location is required", false)
            return
        }
        
        if (currentPropID.isNullOrEmpty() || username.isNullOrEmpty()) {
            showSnackbar("User data not found", false)
            return
        }
        
        showLoading()
        
        lifecycleScope.launch {
            try {
                // Get user's department from UserService
                val userDept = UserService.getCurrentDept()
                if (userDept.isNullOrEmpty()) {
                    showSnackbar("User department not found", false)
                    hideLoading()
                    return@launch
                }
                
                val workOrderData = mapOf<String, String>(
                    "propID" to currentPropID!!,
                    "job" to job,
                    "lokasi" to location,
                    "category" to category,
                    "dept" to userDept,  // Department user yang login (otomatis)
                    "priority" to priority,
                    "orderBy" to username!!,
                    "woto" to selectedDepartment  // Department yang dipilih user di spinner "To:"
                )
                
                // Simulate upload progress if image exists
                if (selectedImageFile != null) {
                    for (i in 0..100 step 10) {
                        uploadProgress = i / 100f
                        updateProgressUI()
                        kotlinx.coroutines.delay(100)
                    }
                }
                
                val result = if (selectedImageFile != null) {
                    // Submit with image - like Flutter
                    try {
                        submitWithImage(workOrderData, selectedImageFile!!)
                    } catch (uploadError: Exception) {
                        // If upload fails, try without image - like Flutter
                        showSnackbar("Image upload failed, submitting without image...", false)
                        
                        val resultMap = mutableMapOf<String, Any>()
                        try {
                            // Try again without image
                            val response = RetrofitClient.apiService.submitWorkOrder(
                                propID = currentPropID!!,
                                orderBy = username!!,
                                job = job,
                                lokasi = location,
                                category = category,
                                dept = userDept,  // Department user yang login (otomatis)
                                priority = priority,
                                woto = selectedDepartment  // Department yang dipilih user di spinner "To:"
                            )
                            resultMap.putAll(response)
                        } catch (e: Exception) {
                            resultMap["error"] = e.message ?: "Unknown error"
                        }
                        resultMap
                    }
                } else {
                    // Submit without image - menggunakan field yang sama dengan Flutter
                    val resultMap = mutableMapOf<String, Any>()
                    try {
                        // Menggunakan submit_wo.php dengan field yang sama dengan Flutter
                        val response = RetrofitClient.apiService.submitWorkOrder(
                            propID = currentPropID!!,
                            orderBy = username!!,
                            job = job,
                            lokasi = location,
                            category = category,
                            dept = userDept,  // Department user yang login (otomatis)
                            priority = priority,
                            woto = selectedDepartment  // Department yang dipilih user di spinner "To:"
                        )
                        resultMap.putAll(response)
                    } catch (e: Exception) {
                        resultMap["error"] = e.message ?: "Unknown error"
                    }
                    resultMap
                }
                
                if (isAdded) {
                    if (result["status"] == "success" || result["id"] != null || result["success"] == true) {
                        showSnackbar("Work order added successfully!", true)
                        clearForm()
                        navigateToOutbox()
                    } else {
                        val errorMessage = result["error"]?.toString() ?: result["message"]?.toString() ?: "Failed to add work order"
                        showSnackbar(errorMessage, false)
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    showSnackbar("Failed to submit work order: ${e.message}", false)
                }
            } finally {
                hideLoading()
            }
        }
    }
    
    private suspend fun submitWithImage(workOrderData: Map<String, String>, imageFile: File): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if file exists and is readable (like Flutter)
                if (!imageFile.exists()) {
                    return@withContext mapOf<String, Any>("error" to "Image file not found")
                }

                // Get file size and check if it's reasonable (max 10MB) - like Flutter
                val fileSize = imageFile.length()
                if (fileSize > 10 * 1024 * 1024) {
                    return@withContext mapOf<String, Any>("error" to "Image file too large (max 10MB)")
                }

                // Create multipart request - sama dengan Flutter
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("propID", workOrderData["propID"]!!)
                    .addFormDataPart("job", workOrderData["job"]!!)
                    .addFormDataPart("lokasi", workOrderData["lokasi"]!!)
                    .addFormDataPart("category", workOrderData["category"]!!)
                    .addFormDataPart("dept", workOrderData["dept"]!!)
                    .addFormDataPart("priority", workOrderData["priority"]!!)
                    .addFormDataPart("orderBy", workOrderData["orderBy"]!!)
                    .addFormDataPart("woto", workOrderData["woto"]!!)
                    .addFormDataPart("photo", "image_${System.currentTimeMillis()}.jpg", imageFile.asRequestBody("image/*".toMediaTypeOrNull()))
                    .build()
                
                // Make request using OkHttp with timeout - like Flutter
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url("https://emshotels.net/apiKu/submit_wo.php")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"
                
                // Parse response - like Flutter
                try {
                    val jsonObject = org.json.JSONObject(responseBody)
                    val result = mutableMapOf<String, Any>()
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        result[key] = jsonObject.get(key)
                    }
                    result
                } catch (e: Exception) {
                    mapOf<String, Any>("error" to "Invalid response format")
                }
            } catch (e: Exception) {
                mapOf<String, Any>("error" to (e.message ?: "Upload failed"))
            }
        }
    }
    
    private fun clearForm() {
        etJob.text.clear()
        etLocation.text.clear()
        spinnerCategory.setSelection(0)
        // Department spinner tidak perlu di-reset karena user harus memilih department tujuan
        spinnerPriority.setSelection(0)
        selectedImageFile = null
        ivPhotoPreview.visibility = View.GONE
        btnPhoto.text = "📷 Upload"
    }
    
    private fun navigateToOutbox() {
        (activity as? MainActivity)?.switchToTab(1) // Switch to Outbox tab
    }
    
    private fun showLoading() {
        isLoading = true
        btnSubmit.isEnabled = false
        btnCancel.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE
    }
    
    private fun hideLoading() {
        isLoading = false
        btnSubmit.isEnabled = true
        btnCancel.isEnabled = true
        progressBar.visibility = View.GONE
        tvProgress.visibility = View.GONE
        uploadProgress = 0f
    }
    
    private fun updateProgressUI() {
        progressBar.progress = (uploadProgress * 100).toInt()
        tvProgress.text = "Uploading... ${(uploadProgress * 100).toInt()}%"
    }
    
    private fun updateUI() {
        if (isLoadingData) {
            // Show loading state
            progressBar.visibility = View.VISIBLE
            tvProgress.text = "Loading form data..."
        } else {
            // Show form
            progressBar.visibility = View.GONE
            tvProgress.visibility = View.GONE
        }
    }
    
    private fun showSnackbar(message: String, isSuccess: Boolean) {
        val snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        if (isSuccess) {
            snackbar.setBackgroundTint(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            snackbar.setBackgroundTint(resources.getColor(android.R.color.holo_red_dark, null))
        }
        snackbar.show()
    }
}
