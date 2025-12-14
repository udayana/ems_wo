package com.sofindo.ems.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import com.sofindo.ems.activities.CreateProjectActivity
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
import com.sofindo.ems.services.NotificationService
import com.sofindo.ems.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


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
    private lateinit var btnCreateProject: Button
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
    private val priorities = mutableListOf("Priority", "Low", "Medium", "High")
    
    // State
    private var currentPropID: String? = null
    private var username: String? = null
    private var selectedImageFile: File? = null
    private var cameraPhotoFile: File? = null
    private var cameraPhotoUri: Uri? = null

    private var isLoadingData = true
    private var isSubmitting = false
    
    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val file = cameraPhotoFile
                if (file != null && file.exists() && file.length() > 0) {
                    // Resize in-place or return resized file (420px longest side, JPEG 90%)
                    selectedImageFile = resizeJpegInPlace(file, maxSide = 420, quality = 90)
                    updatePhotoUI()
                } else {
                    // Fallback: if some camera app ignored EXTRA_OUTPUT, try to use thumbnail if present
                    @Suppress("DEPRECATION")
                    result.data?.extras?.getParcelable<Bitmap>("data")?.let { thumb ->
                        try {
                            val fallback = createImageFile()
                            FileOutputStream(fallback).use { out ->
                                thumb.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            selectedImageFile = resizeJpegInPlace(fallback, maxSide = 420, quality = 90)
                            updatePhotoUI()
                        } catch (e: Exception) {
                            showSnackbar("Failed to save camera image: ${e.message}", false)
                        }
                    } ?: showSnackbar("Camera file not found", false)
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
        btnCreateProject = view.findViewById(R.id.btn_create_project)
        ivPhotoPreview = view.findViewById(R.id.iv_photo_preview)
        progressBar = view.findViewById(R.id.progress_bar)
        tvProgress = view.findViewById(R.id.tv_progress)
        layoutPhotoSection = view.findViewById(R.id.layout_photo_section)
        layoutLocationSuggestions = view.findViewById(R.id.layout_location_suggestions)
    }
    
    private fun setupListeners() {
        btnSubmit.setOnClickListener {
            hideKeyboard() // Hide keyboard before submitting
            submitWorkOrder()
        }
        
        btnCancel.setOnClickListener {
            clearForm()
            navigateToOutbox()
        }
        
        btnPhoto.setOnClickListener {
            hideKeyboard() // Hide keyboard before showing image dialog
            showImageSourceDialog()
        }
        
        btnCreateProject.setOnClickListener {
            hideKeyboard()
            navigateToCreateProject()
        }
        
        // Job text area - capitalize first letter of each sentence
        etJob.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s != null && s.isNotEmpty()) {
                    val text = s.toString()
                    val capitalizedText = capitalizeFirstLetterOfEachSentence(text)
                    if (text != capitalizedText) {
                        etJob.removeTextChangedListener(this)
                        etJob.setText(capitalizedText)
                        etJob.setSelection(capitalizedText.length)
                        etJob.addTextChangedListener(this)
                    }
                }
            }
        })
        
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
                        withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                            RetrofitClient.apiService.getCategories(currentPropID!!)
                        }
                    } catch (e: Exception) {
                        listOf("General", "Maintenance", "Housekeeping", "Security")
                    }
                }
                
                val departmentsResult = withContext(Dispatchers.IO) {
                    try {
                        withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                            RetrofitClient.apiService.getDepartments(currentPropID!!)
                        }
                    } catch (e: Exception) {
                        listOf("Engineering", "Housekeeping", "Security", "Front Office")
                    }
                }
                
                val locationsResult = withContext(Dispatchers.IO) {
                    try {
                        withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                            RetrofitClient.apiService.getLocations(currentPropID!!)
                        }
                    } catch (e: Exception) {
                        listOf("Lobby", "Room", "Kitchen", "Pool", "Garden")
                    }
                }
                
                if (isAdded) {
                    // Set categories with placeholder
                    categories.clear()
                    categories.add("Category") // Placeholder
                    categories.addAll(categoriesResult)
                    if (!categories.contains("General")) {
                        categories.add("General")
                    }
                    
                    // Set departments with placeholder
                    departments.clear()
                    departments.add("To:") // Placeholder
                    departments.addAll(departmentsResult)
                    
                    // Ensure "Engineering" exists in the list for default fallback
                    if (!departments.contains("Engineering")) {
                        departments.add("Engineering")
                    }
                    
                    // Ensure "General" exists in the list for default fallback
                    if (!categories.contains("General")) {
                        categories.add("General")
                    }
                    
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
                    categories = mutableListOf("Category", "General", "Maintenance", "Housekeeping", "Security")
                    departments = mutableListOf("To:", "Engineering", "Housekeeping", "Security", "Front Office")
                    allLocations = mutableListOf("Lobby", "Room", "Kitchen", "Pool", "Garden")
                    setupSpinners()
                    isLoadingData = false
                    updateUI()
                }
            }
        }
    }
    
    private fun setupSpinners() {
        // Cache colors for performance
        val placeholderColor = resources.getColor(android.R.color.darker_gray, null)
        val normalColor = resources.getColor(R.color.black, null)
        
        // Category Spinner with optimized adapter
        val categoryAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
        }
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter
        
        // Department Spinner with optimized adapter
        val departmentAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, departments) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
        }
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = departmentAdapter
        
        // Priority Spinner with optimized adapter
        val priorityAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, priorities) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
        }
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
        
        // Set defaults with post to ensure proper rendering
        spinnerCategory.post {
            if (categories.isNotEmpty()) {
                // Set default to "General" if available, otherwise first item
                val generalIndex = categories.indexOf("General")
                spinnerCategory.setSelection(if (generalIndex > 0) generalIndex else 1)
            }
        }
        
        spinnerDepartment.post {
            if (departments.isNotEmpty()) {
                // Set default to "Engineering" if available, otherwise first item after placeholder
                val engineeringIndex = departments.indexOf("Engineering")
                spinnerDepartment.setSelection(if (engineeringIndex > 0) engineeringIndex else 1)
            }
        }
        
        spinnerPriority.post {
            // Set default to "Low" if available, otherwise first item after placeholder
            val lowIndex = priorities.indexOf("Low")
            spinnerPriority.setSelection(if (lowIndex > 0) lowIndex else 1)
        }
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
            PermissionUtils.isCameraPermissionGranted(requireContext()) -> {
                openCamera()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun openCamera() {
        cameraPhotoFile = createImageFile()
        cameraPhotoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            cameraPhotoFile!!
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val resInfoList = requireContext().packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            requireContext().grantUriPermission(
                packageName,
                cameraPhotoUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        cameraLauncher.launch(intent)
    }
    
    private fun openGallery() {
        // Use Android Photo Picker - no permission required
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        galleryLauncher.launch(intent)
    }
    
    
    private fun handleGalleryImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Create temp file
                val tempFile = createImageFile()
                val outputStream = FileOutputStream(tempFile)
                
                // Copy image to temp file
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.close()
                
                // Get original size for logging
                val originalSize = tempFile.length()
                android.util.Log.d("TambahWOFragment", "Original image size: $originalSize bytes")
                
                selectedImageFile = resizeJpegInPlace(tempFile, maxSide = 420, quality = 90)
                
                // Log processed image info
                val processedSize = selectedImageFile?.length() ?: 0
                android.util.Log.d("TambahWOFragment", "Processed image size: $processedSize bytes")
                
                if (processedSize > 3 * 1024 * 1024) { // 3MB limit
                    showSnackbar("Image is too large after processing. Please try a different image.", false)
                    selectedImageFile = null
                    return@launch
                }
                
                updatePhotoUI()
                hideKeyboard()
            } catch (e: Exception) {
                android.util.Log.e("TambahWOFragment", "Failed to load image: ${e.message}", e)
                showSnackbar("Failed to load image: ${e.message}", false)
            }
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    /**
     * Get file size from URI without loading the entire file
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
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
        // Prevent double submit
        if (isSubmitting) {
            return
        }
        
        val job = etJob.text.toString().trim()
        val location = etLocation.text.toString().trim()
        
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
        
        // Check user active status before submitting
        checkUserActiveStatus()
    }
    
    private fun checkUserActiveStatus() {
        lifecycleScope.launch {
            try {
                // Get current user data to check active status
                val user = UserService.getCurrentUser()
                if (user == null) {
                    showSnackbar("User data not found", false)
                    return@launch
                }
                
                // Check if user is active by making API call
                val isActive = withContext(Dispatchers.IO) {
                    try {
                        withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                            val response = RetrofitClient.apiService.getUserProfile("get", user.id)
                            if ((response["success"] as? Boolean) == true) {
                                @Suppress("UNCHECKED_CAST")
                                val userData = response["data"] as? Map<String, Any>
                                (userData?.get("active") as? String) == "1"
                            } else {
                                false
                            }
                        }
                    } catch (e: Exception) {
                        false // Default to false if check fails
                    }
                }
                
                if (isActive) {
                    // User is active, proceed with work order submission
                    proceedWithWorkOrderSubmission()
                } else {
                    // User is not active, show dialog
                    showUserInactiveDialog()
                }
            } catch (e: Exception) {
                showSnackbar("Failed to check user status: ${e.message}", false)
            }
        }
    }
    
    private fun proceedWithWorkOrderSubmission() {
        val job = etJob.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val category = spinnerCategory.selectedItem.toString().let { 
            if (it == "Category") "General" else it 
        }
        val selectedDepartment = spinnerDepartment.selectedItem.toString().let { 
            if (it == "To:") "Engineering" else it 
        }
        val priority = spinnerPriority.selectedItem.toString().let { 
            if (it == "Priority") "Low" else it 
        }
        
        // Disable submit button and change text to prevent double submit
        isSubmitting = true
        btnSubmit.isEnabled = false
        btnSubmit.text = "Submitting..."
        
        lifecycleScope.launch {
            try {
                // Get user's department from UserService with timeout
                val userDept = withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                    UserService.getCurrentDept()
                }
                if (userDept.isNullOrEmpty()) {
                    showSnackbar("Failed to get user department", false)
                    resetSubmitButton()
                    return@launch
                }
                
                val workOrderData = mapOf<String, String>(
                    "propID" to currentPropID!!,
                    "job" to job,
                    "lokasi" to location,
                    "category" to category,
                    "dept" to userDept,  // Department of logged in user (automatic)
                    "priority" to priority,
                    "orderBy" to username!!,
                    "woto" to selectedDepartment,  // Department selected by user in "To:" spinner
                    "status" to "new"  // ✅ Set default status to "new" instead of empty
                )
                
                val result = if (selectedImageFile != null) {
                    // Submit with image - like Flutter
                    try {
                        val uploadResult = withTimeout(TimeUnit.MINUTES.toMillis(3)) {
                            submitWithImage(workOrderData, selectedImageFile!!)
                        }
                        uploadResult
                    } catch (uploadError: Exception) {
                        // If upload fails, try without image - like Flutter
                        val resultMap = mutableMapOf<String, Any>()
                        try {
                                                    // Try again without image
                        val response = withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                            RetrofitClient.apiService.submitWorkOrder(
                                propID = currentPropID!!,
                                orderBy = username!!,
                                job = job,
                                lokasi = location,
                                category = category,
                                dept = userDept,  // Department of logged in user (automatic)
                                priority = priority,
                                woto = selectedDepartment,  // Department selected by user in "To:" spinner
                                status = "new"  // ✅ Set default status to "new" instead of empty
                            )
                        }
                        resultMap.putAll(response)
                        } catch (e: Exception) {
                            resultMap["error"] = e.message ?: "Unknown error"
                        }
                        resultMap
                    }
                } else {
                    // Submit without image - using same fields as Flutter
                    val resultMap = mutableMapOf<String, Any>()
                    try {
                        // Using submit_wo.php with same fields as Flutter
                        val response = withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                            RetrofitClient.apiService.submitWorkOrder(
                                propID = currentPropID!!,
                                orderBy = username!!,
                                job = job,
                                lokasi = location,
                                category = category,
                                dept = userDept,  // Department of logged in user (automatic)
                                priority = priority,
                                woto = selectedDepartment,  // Department selected by user in "To:" spinner
                                status = "new"  // ✅ Set default status to "new" instead of empty
                            )
                        }
                        resultMap.putAll(response)
                    } catch (e: Exception) {
                        resultMap["error"] = e.message ?: "Unknown error"
                    }
                    resultMap
                }
                
                if (result["status"] == "success" || result["id"] != null || result["success"] == true) {
                    showSnackbar("Work order added successfully!", true)
                    
                    // Send notification to target department (fixed with better error handling)
                    sendWorkOrderNotification(job, location, selectedDepartment)
                    
                    clearForm()
                    navigateToOutbox()
                } else {
                    // Show error message for debugging
                    val errorMsg = result["error"]?.toString() ?: "Unknown error occurred"
                    showSnackbar("Submit failed: $errorMsg", false)
                }
            } catch (e: CancellationException) {
                // Handle timeout or cancellation
                showSnackbar("Request timeout. Please try again.", false)
            } catch (e: Exception) {
                // Show error message for debugging
                showSnackbar("Submit error: ${e.message}", false)
            } finally {
                // Re-enable submit button and restore text
                isSubmitting = false
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit"
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

                // Create multipart request - same as Flutter
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
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
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
        
        // Set default values instead of placeholder
        val generalIndex = categories.indexOf("General")
        spinnerCategory.setSelection(if (generalIndex > 0) generalIndex else 1)
        
        val engineeringIndex = departments.indexOf("Engineering")
        spinnerDepartment.setSelection(if (engineeringIndex > 0) engineeringIndex else 1)
        
        val lowIndex = priorities.indexOf("Low")
        spinnerPriority.setSelection(if (lowIndex > 0) lowIndex else 1)
        
        selectedImageFile = null
        ivPhotoPreview.visibility = View.GONE
        btnPhoto.text = "📷 Upload"
        
        // Reset submit button state
        resetSubmitButton()
    }
    
    private fun resetSubmitButton() {
        isSubmitting = false
        btnSubmit.isEnabled = true
        btnSubmit.text = "Submit"
    }
    
    private fun navigateToOutbox() {
        (activity as? MainActivity)?.switchToTab(1) // Switch to Outbox tab
    }
    
    private fun navigateToCreateProject() {
        val intent = Intent(context, com.sofindo.ems.activities.CreateProjectActivity::class.java)
        startActivity(intent)
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
    
    private fun showUserInactiveDialog() {
        // Create custom title with icon and colored text
        val titleView = TextView(requireContext()).apply {
            text = "🚫 Akses Dibatasi"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(48, 32, 48, 16)
            gravity = android.view.Gravity.CENTER
        }
        
        // Create custom message with better formatting
        val messageView = TextView(requireContext()).apply {
            text = "Untuk sementara anda belum bisa membuat workorder.\n\nTolong selesaikan dahulu administrasi (hubungi Admin)"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(resources.getColor(android.R.color.black, null))
            setPadding(48, 16, 48, 32)
            gravity = android.view.Gravity.CENTER
            setLineSpacing(4f, 1.2f)
        }
        
        AlertDialog.Builder(requireContext())
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Navigate back to outbox or main screen
                navigateToOutbox()
            }
            .setCancelable(false)
            .show()
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
    
    private fun sendWorkOrderNotification(job: String, location: String, targetDepartment: String) {
        try {
            // Log for debugging
            android.util.Log.d("TambahWOFragment", "Sending notification to department: $targetDepartment for job: $job")
            
            NotificationService.sendWorkOrderNotification(
                workOrderTitle = job,
                workOrderDescription = "Location: $location",
                targetDepartment = targetDepartment,
                onSuccess = {
                    // Notification sent successfully - run on main thread
                    activity?.runOnUiThread {
                        // Check if fragment is still attached and has view
                        if (isAdded && view != null) {
                            showSnackbar("Notification sent to $targetDepartment department", true)
                        }
                    }
                },
                onError = { error ->
                    // Notification failed, but work order was still submitted - run on main thread
                    activity?.runOnUiThread {
                        // Check if fragment is still attached and has view
                        if (isAdded && view != null) {
                            showSnackbar("Work order submitted, but notification failed: $error", false)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            // If notification service itself crashes, don't crash the app
            activity?.runOnUiThread {
                // Check if fragment is still attached and has view
                if (isAdded && view != null) {
                    showSnackbar("Work order submitted, notification service error: ${e.message}", false)
                }
            }
        }
    }
    
    private fun hideKeyboard() {
        try {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val currentFocus = activity?.currentFocus
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            }
        } catch (e: Exception) {
            // Ignore any errors
        }
    }
    
    /**
     * Capitalize the first letter of each sentence in the given text
     * Handles multiple sentence terminators: . ! ?
     */
    private fun capitalizeFirstLetterOfEachSentence(text: String): String {
        if (text.isEmpty()) return text
        
        val result = StringBuilder()
        var capitalizeNext = true
        
        for (i in text.indices) {
            val char = text[i]
            
            if (capitalizeNext && char.isLetter()) {
                result.append(char.uppercase())
                capitalizeNext = false
            } else {
                result.append(char)
            }
            
            // Check for sentence terminators
            if (char == '.' || char == '!' || char == '?') {
                capitalizeNext = true
            }
        }
        
        return result.toString()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up resources
        selectedImageFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
    // === ADD: resize JPEG in-place to 420px, quality 90% ===
private fun resizeJpegInPlace(file: java.io.File, maxSide: Int = 420, quality: Int = 90): java.io.File {
    // 1) read original dimensions without full load
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return file

    // 2) memory-efficient decode (don't make it too small)
    val sample = calculateInSampleSize(srcW, srcH, maxSide)
    val decodeOpts = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    var bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return file

    // 3) scale so longest side = maxSide (proportional)
    val longest = kotlin.math.max(bmp.width, bmp.height)
    val scale = longest.toFloat() / maxSide
    val finalBmp = if (scale > 1f) {
        val w = (bmp.width / scale).toInt()
        val h = (bmp.height / scale).toInt()
        android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
    } else bmp

    // 4) overwrite same file as JPEG 90%
    java.io.FileOutputStream(file, false).use { out ->
        finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        out.flush()
    }

    if (finalBmp !== bmp) bmp.recycle()
    return file
}


// === ADD: helper hitung inSampleSize yang “aman” ===
private fun calculateInSampleSize(srcW: Int, srcH: Int, reqMaxSide: Int): Int {
    var inSampleSize = 1
    val longest = kotlin.math.max(srcW, srcH)
    // safety measure so initial decode result is still > target (≈ < 2× target)
    while (longest / inSampleSize > reqMaxSide * 2) {
        inSampleSize *= 2
    }
    return inSampleSize
}

}
