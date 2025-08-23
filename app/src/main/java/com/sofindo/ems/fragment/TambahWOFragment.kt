package com.sofindo.ems.fragment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.adapter.LocationRecyclerAdapter
import com.sofindo.ems.api.ApiService
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.LocationSuggestion
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TambahWOFragment(private val onTabChanged: ((Int) -> Unit)? = null) : Fragment() {

    private lateinit var departmentSpinner: Spinner
    private lateinit var locationEditText: EditText
    private lateinit var locationSuggestionsList: RecyclerView
    private lateinit var jobEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var prioritySpinner: Spinner
    private lateinit var photoButton: Button
    private lateinit var removePhotoButton: ImageButton
    private lateinit var photoPreview: ImageView
    private lateinit var cancelButton: Button
    private lateinit var submitButton: Button
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var uploadProgressText: TextView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var formContainer: ScrollView

    private var selectedImageFile: File? = null
    private var categories = mutableListOf<String>()
    private var departments = mutableListOf<String>()
    private var locationSuggestions = mutableListOf<LocationSuggestion>()
    private var priorities = mutableListOf("Low", "Medium", "High")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog()
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { saveImageToFile(it) }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUri(uri)
            }
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
        setupTouchListener()
        loadMasterData()
    }

    private fun initViews(view: View) {
        departmentSpinner = view.findViewById(R.id.departmentSpinner)
        locationEditText = view.findViewById(R.id.locationEditText)
        locationSuggestionsList = view.findViewById(R.id.locationSuggestionsList)
        jobEditText = view.findViewById(R.id.jobEditText)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        prioritySpinner = view.findViewById(R.id.prioritySpinner)
        photoButton = view.findViewById(R.id.photoButton)
        removePhotoButton = view.findViewById(R.id.removePhotoButton)
        photoPreview = view.findViewById(R.id.photoPreview)
        cancelButton = view.findViewById(R.id.cancelButton)
        submitButton = view.findViewById(R.id.submitButton)
        uploadProgressBar = view.findViewById(R.id.uploadProgressBar)
        uploadProgressText = view.findViewById(R.id.uploadProgressText)
        loadingContainer = view.findViewById(R.id.loadingContainer)
        formContainer = view.findViewById(R.id.formContainer)
        
        // Configure RecyclerView for proper scrolling (like Bootstrap list-group)
        locationSuggestionsList.layoutManager = LinearLayoutManager(context)
        locationSuggestionsList.setHasFixedSize(true)
    }

    private fun setupListeners() {
        photoButton.setOnClickListener {
            checkCameraPermissionAndTakePhoto()
        }

        removePhotoButton.setOnClickListener {
            selectedImageFile = null
            photoPreview.visibility = View.GONE
            removePhotoButton.visibility = View.GONE
            photoButton.text = "Photo"
        }

        cancelButton.setOnClickListener {
            clearForm()
            onTabChanged?.invoke(1) // Go to Outbox tab
        }

        submitButton.setOnClickListener {
            submitWorkOrder()
        }

        // Location autocomplete (same as Flutter)
        locationEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString() ?: ""
                android.util.Log.d("LocationFilter", "Text changed to: '$query'")
                filterLocations(query)
            }
        })
        
        // Hide suggestions when focus is lost
        locationEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // Delay hiding to allow for item selection
                locationEditText.postDelayed({
                    if (!locationSuggestionsList.hasFocus()) {
                        locationSuggestionsList.visibility = View.GONE
                    }
                }, 200)
            }
        }

        // Priority spinner default to "Low"
        prioritySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Handle selection if needed
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupTouchListener() {
        // Hide suggestions when touching outside
        val rootView = view?.findViewById<View>(R.id.formContainer)
        rootView?.setOnTouchListener { _, event ->
            if (locationSuggestionsList.visibility == View.VISIBLE) {
                val outRect = android.graphics.Rect()
                locationSuggestionsList.getGlobalVisibleRect(outRect)
                
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    locationSuggestionsList.visibility = View.GONE
                    locationEditText.clearFocus()
                }
            }
            false
        }
    }

    private fun loadMasterData() {
        loadingContainer.visibility = View.VISIBLE
        formContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Get propID first
                val propID = UserService.getCurrentPropID(requireContext())
                
                if (propID.isNullOrEmpty()) {
                    throw Exception("PropID not available")
                }

                // Load data parallel dengan timeout (same as Flutter)
                val categoriesDeferred = async { loadCategoriesWithTimeout(propID) }
                val departmentsDeferred = async { loadDepartmentsWithTimeout(propID) }
                val locationsDeferred = async { loadLocationSuggestionsWithTimeout(propID) }

                categories = categoriesDeferred.await().toMutableList()
                departments = departmentsDeferred.await().toMutableList()
                locationSuggestions = locationsDeferred.await().toMutableList()

                // Add "General" to categories if not exists (same as Flutter)
                if (!categories.contains("General")) {
                    categories.add(0, "General")
                }

                setupSpinners()
                
                loadingContainer.visibility = View.GONE
                formContainer.visibility = View.VISIBLE

            } catch (e: Exception) {
                // Use fallback data (same as Flutter)
                categories = mutableListOf("General", "Maintenance", "Housekeeping", "Security")
                departments = mutableListOf("Engineering", "Housekeeping", "Security", "Front Office")
                locationSuggestions = mutableListOf(
                    LocationSuggestion("Lobby"),
                    LocationSuggestion("Room 101"),
                    LocationSuggestion("Room 102"),
                    LocationSuggestion("Room 201"),
                    LocationSuggestion("Room 202"),
                    LocationSuggestion("Kitchen"),
                    LocationSuggestion("Pool"),
                    LocationSuggestion("Garden"),
                    LocationSuggestion("Parking"),
                    LocationSuggestion("Reception"),
                    LocationSuggestion("Restaurant"),
                    LocationSuggestion("Spa"),
                    LocationSuggestion("Gym"),
                    LocationSuggestion("Conference Room A"),
                    LocationSuggestion("Conference Room B"),
                    LocationSuggestion("Bar"),
                    LocationSuggestion("Library"),
                    LocationSuggestion("Business Center"),
                    LocationSuggestion("Laundry"),
                    LocationSuggestion("Storage")
                )
                
                setupSpinners()
                
                loadingContainer.visibility = View.GONE
                formContainer.visibility = View.VISIBLE
                
                Toast.makeText(context, "Using offline data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadCategoriesWithTimeout(propID: String): List<String> {
        return try {
            withTimeout(8000) {
                RetrofitClient.apiService.getCategories(propID)
            }
        } catch (e: Exception) {
            listOf("General", "Maintenance", "Housekeeping", "Security")
        }
    }

    private suspend fun loadDepartmentsWithTimeout(propID: String): List<String> {
        return try {
            withTimeout(8000) {
                RetrofitClient.apiService.getDepartments(propID)
            }
        } catch (e: Exception) {
            listOf("Engineering", "Housekeeping", "Security", "Front Office")
        }
    }

    private suspend fun loadLocationSuggestionsWithTimeout(propID: String): List<LocationSuggestion> {
        return try {
            withTimeout(8000) {
                val locations = RetrofitClient.apiService.getLocations(propID)
                locations.map { location ->
                    LocationSuggestion(location)
                }
            }
        } catch (e: Exception) {
            listOf(
                LocationSuggestion("Lobby"),
                LocationSuggestion("Room 101"),
                LocationSuggestion("Room 102"),
                LocationSuggestion("Room 201"),
                LocationSuggestion("Room 202"),
                LocationSuggestion("Kitchen"),
                LocationSuggestion("Pool"),
                LocationSuggestion("Garden"),
                LocationSuggestion("Parking"),
                LocationSuggestion("Reception"),
                LocationSuggestion("Restaurant"),
                LocationSuggestion("Spa"),
                LocationSuggestion("Gym"),
                LocationSuggestion("Conference Room A"),
                LocationSuggestion("Conference Room B"),
                LocationSuggestion("Bar"),
                LocationSuggestion("Library"),
                LocationSuggestion("Business Center"),
                LocationSuggestion("Laundry"),
                LocationSuggestion("Storage")
            )
        }
    }

    private fun filterLocations(query: String) {
        if (query.isEmpty()) {
            locationSuggestionsList.visibility = View.GONE
            return
        }

        val filteredSuggestions = locationSuggestions.filter { suggestion ->
            suggestion.primaryName.lowercase().contains(query.lowercase())
        }

        // Debug: Log the filtering results
        android.util.Log.d("LocationFilter", "Query: '$query', Found: ${filteredSuggestions.size} items")
        android.util.Log.d("LocationFilter", "Total available items: ${locationSuggestions.size}")
        filteredSuggestions.forEach { suggestion ->
            android.util.Log.d("LocationFilter", "Filtered: ${suggestion.primaryName}")
        }

        if (filteredSuggestions.isNotEmpty()) {
            val adapter = LocationRecyclerAdapter(filteredSuggestions) { selectedSuggestion ->
                // Handle item click
                locationEditText.setText(selectedSuggestion.primaryName)
                locationEditText.setSelection(locationEditText.text.length)
                locationSuggestionsList.visibility = View.GONE
            }
            
            locationSuggestionsList.adapter = adapter
            
            locationSuggestionsList.visibility = View.VISIBLE
            
            // Debug: Log adapter info
            android.util.Log.d("LocationFilter", "RecyclerView adapter count: ${adapter.itemCount}")
            android.util.Log.d("LocationFilter", "RecyclerView visibility: ${locationSuggestionsList.visibility}")
        } else {
            locationSuggestionsList.visibility = View.GONE
        }
    }

    private fun setupSpinners() {
        // Department spinner with custom adapter for black text
        val departmentAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, departments) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    setBackgroundResource(R.drawable.location_suggestions_background)
                    setPadding(32, 16, 32, 16)
                }
                return view
            }
        }
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        departmentSpinner.adapter = departmentAdapter
        
        // Set default to "Engineering"
        val engineeringIndex = departments.indexOf("Engineering")
        if (engineeringIndex != -1) {
            departmentSpinner.setSelection(engineeringIndex)
        }

        // Category spinner with custom adapter for black text
        val categoryAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    setBackgroundResource(R.drawable.location_suggestions_background)
                    setPadding(32, 16, 32, 16)
                }
                return view
            }
        }
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter
        
        // Set default to "General"
        val generalIndex = categories.indexOf("General")
        if (generalIndex != -1) {
            categorySpinner.setSelection(generalIndex)
        }

        // Priority spinner with custom adapter for black text
        val priorityAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, priorities) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    setBackgroundResource(R.drawable.location_suggestions_background)
                    setPadding(32, 16, 32, 16)
                }
                return view
            }
        }
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = priorityAdapter
        
        // Set default to "Low"
        val lowIndex = priorities.indexOf("Low")
        if (lowIndex != -1) {
            prioritySpinner.setSelection(lowIndex)
        }
    }

    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                showImageSourceDialog()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Take Photo From")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhotoFromCamera()
                    1 -> pickPhotoFromGallery()
                }
            }
            .show()
    }

    private fun takePhotoFromCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }

    private fun pickPhotoFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun saveImageToFile(bitmap: Bitmap) {
        try {
            val file = File(requireContext().cacheDir, "work_order_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            
            // Get device screen width for optimal image size
            val screenWidth = getDeviceScreenWidth()
            val maxImageSize = screenWidth.coerceAtMost(1200) // Cap at 1200px for very large screens
            
            // Resize and compress bitmap
            val resizedBitmap = resizeBitmap(bitmap, maxImageSize, maxImageSize)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            outputStream.flush()
            outputStream.close()
            
            selectedImageFile = file
            displaySelectedImage(resizedBitmap)
            
        } catch (e: IOException) {
            Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                saveImageToFile(bitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = maxWidth
            newHeight = (maxWidth / aspectRatio).toInt()
        } else {
            newHeight = maxHeight
            newWidth = (maxHeight * aspectRatio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun getDeviceScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+)
            val windowMetrics = requireActivity().windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            return bounds.width()
        } else {
            // For older Android versions
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }
    }
    
    private fun displaySelectedImage(bitmap: Bitmap) {
        photoPreview.setImageBitmap(bitmap)
        photoPreview.visibility = View.VISIBLE
        removePhotoButton.visibility = View.VISIBLE
        photoButton.text = "Photo Selected"
    }

    private fun submitWorkOrder() {
        // Validate form
        if (locationEditText.text.toString().trim().isEmpty()) {
            locationEditText.error = "Location is required"
            return
        }
        
        if (jobEditText.text.toString().trim().isEmpty()) {
            jobEditText.error = "Job description is required"
            return
        }

        submitButton.isEnabled = false
        uploadProgressBar.visibility = View.VISIBLE
        uploadProgressText.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Get propID and user from session
                val propID = UserService.getCurrentPropID(requireContext())
                val user = UserService.getCurrentUser(requireContext())

                if (propID.isNullOrEmpty()) {
                    Toast.makeText(context, "Error: PropID not available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val workOrderData = mapOf(
                    "propID" to propID,
                    "job" to jobEditText.text.toString().trim(),
                    "lokasi" to locationEditText.text.toString().trim(),
                    "category" to (categorySpinner.selectedItem?.toString() ?: "General"),
                    "dept" to (departmentSpinner.selectedItem?.toString() ?: "Engineering"),
                    "priority" to (prioritySpinner.selectedItem?.toString() ?: "Low"),
                    "orderBy" to (user?.username ?: user?.email ?: ""),
                    "woto" to (departmentSpinner.selectedItem?.toString() ?: "Engineering")
                )

                // Simulate upload progress if image exists
                if (selectedImageFile != null) {
                    for (i in 0..100 step 10) {
                        uploadProgressText.text = "Uploading... $i%"
                        delay(100)
                    }
                }

                // Submit work order menggunakan pattern yang sama dengan support ticket
                val result = submitWorkOrderToAPI(workOrderData, selectedImageFile)

                if (result["status"] == "success" || result["id"] != null) {
                    Toast.makeText(context, "Work order added successfully!", Toast.LENGTH_SHORT).show()
                    
                    clearForm()
                    onTabChanged?.invoke(1) // Go to Outbox tab
                    
                } else {
                    val errorMessage = result["error"] ?: result["message"] ?: "Failed to add work order"
                    Toast.makeText(context, errorMessage.toString(), Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to add work order: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                submitButton.isEnabled = true
                uploadProgressBar.visibility = View.GONE
                uploadProgressText.visibility = View.GONE
            }
        }
    }

    private suspend fun submitWorkOrderToAPI(
        workOrderData: Map<String, String>,
        imageFile: File?
    ): Map<String, Any> {
        return try {
            val result = if (imageFile != null) {
                // Submit with photo (same as Flutter MultipartRequest)
                val photoPart = MultipartBody.Part.createFormData(
                    "photo", 
                    "image_${System.currentTimeMillis()}.jpg", 
                    imageFile.asRequestBody("image/jpeg".toMediaType())
                )
                
                RetrofitClient.apiService.submitWorkOrderWithPhoto(
                    propID = (workOrderData["propID"] ?: "").toRequestBody("text/plain".toMediaType()),
                    job = (workOrderData["job"] ?: "").toRequestBody("text/plain".toMediaType()),
                    lokasi = (workOrderData["lokasi"] ?: "").toRequestBody("text/plain".toMediaType()),
                    category = (workOrderData["category"] ?: "").toRequestBody("text/plain".toMediaType()),
                    dept = (workOrderData["dept"] ?: "").toRequestBody("text/plain".toMediaType()),
                    priority = (workOrderData["priority"] ?: "").toRequestBody("text/plain".toMediaType()),
                    orderBy = (workOrderData["orderBy"] ?: "").toRequestBody("text/plain".toMediaType()),
                    woto = (workOrderData["woto"] ?: "").toRequestBody("text/plain".toMediaType()),
                    photo = photoPart
                )
            } else {
                // Submit without photo (FormUrlEncoded)
                RetrofitClient.apiService.submitWorkOrder(
                    propID = workOrderData["propID"] ?: "",
                    job = workOrderData["job"] ?: "",
                    lokasi = workOrderData["lokasi"] ?: "",
                    category = workOrderData["category"] ?: "",
                    dept = workOrderData["dept"] ?: "",
                    priority = workOrderData["priority"] ?: "",
                    orderBy = workOrderData["orderBy"] ?: "",
                    woto = workOrderData["woto"] ?: ""
                )
            }
            
            // Handle response (same as Flutter)
            if (result["status"] == "success" || result["id"] != null) {
                mapOf(
                    "status" to "success",
                    "message" to "Work order submitted successfully",
                    "id" to (result["id"] ?: System.currentTimeMillis().toString())
                )
            } else {
                mapOf(
                    "status" to "error",
                    "message" to (result["error"] ?: result["message"] ?: "Failed to submit work order")
                )
            }
            
        } catch (e: Exception) {
            mapOf(
                "status" to "error",
                "message" to (e.message ?: "Failed to submit work order")
            )
        }
    }
    
    private suspend fun uploadWorkOrderPhoto(woId: String, imageFile: File) {
        try {
            // Create RequestBody for woId using extension function
            val woIdRequestBody = woId.toRequestBody("text/plain".toMediaType())
            
            // Create MultipartBody.Part for photo using extension function
            val photoRequestBody = imageFile.asRequestBody("image/jpeg".toMediaType())
            val photoPart = MultipartBody.Part.createFormData("photo", "image.jpg", photoRequestBody)
            
            // Upload photo
            val result = RetrofitClient.apiService.uploadWorkOrderPhoto(woIdRequestBody, photoPart)
            
            android.util.Log.d("PhotoUpload", "Photo upload result: $result")
            
        } catch (e: Exception) {
            android.util.Log.e("PhotoUpload", "Failed to upload photo: ${e.message}")
            throw e
        }
    }

    private fun clearForm() {
        locationEditText.text.clear()
        jobEditText.text.clear()
        selectedImageFile = null
        photoPreview.visibility = View.GONE
        removePhotoButton.visibility = View.GONE
        photoButton.text = "Photo"
        
        // Reset spinners to default
        val engineeringIndex = departments.indexOf("Engineering")
        if (engineeringIndex != -1) {
            departmentSpinner.setSelection(engineeringIndex)
        }
        
        val generalIndex = categories.indexOf("General")
        if (generalIndex != -1) {
            categorySpinner.setSelection(generalIndex)
        }
        
        val lowIndex = priorities.indexOf("Low")
        if (lowIndex != -1) {
            prioritySpinner.setSelection(lowIndex)
        }
    }
}
