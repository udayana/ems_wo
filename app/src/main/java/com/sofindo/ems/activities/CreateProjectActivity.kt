package com.sofindo.ems.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.JobItem
import com.sofindo.ems.models.MaterialItem
import com.sofindo.ems.adapters.JobsAdapter
import com.sofindo.ems.adapters.MaterialsAdapter
import com.sofindo.ems.adapters.PhotoGalleryAdapter
import com.sofindo.ems.services.UserService
import com.sofindo.ems.services.NotificationService
import com.sofindo.ems.services.OfflineProjectService
import com.sofindo.ems.services.SyncService
import com.sofindo.ems.database.PendingProject
import com.sofindo.ems.utils.PermissionUtils
import com.sofindo.ems.utils.NetworkUtils
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.utils.resizeCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.result.contract.ActivityResultContracts

class CreateProjectActivity : AppCompatActivity() {
    
    // UI Components - Basic Info
    private lateinit var etProjectName: EditText
    private lateinit var spinnerTo: Spinner
    private lateinit var etLocation: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerPriority: Spinner
    private lateinit var layoutLocationSuggestions: LinearLayout
    
    // Jobs Section
    private lateinit var recyclerViewJobs: RecyclerView
    private lateinit var btnAddJob: ImageButton
    
    // Materials Section
    private lateinit var recyclerViewMaterials: RecyclerView
    private lateinit var btnAddMaterial: ImageButton
    private lateinit var tvTotalAmount: TextView
    
    // Action Buttons
    private lateinit var btnCancel: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    
    // Data
    private var toList = mutableListOf<String>()
    private var lokasiList = mutableListOf<String>()
    private var categoryList = mutableListOf<String>()
    private var locationSuggestions = mutableListOf<String>()
    private val priorityList = listOf("Low", "Medium", "High")
    
    // State
    private var currentPropID: String? = null
    private var username: String? = null
    private var userDept: String? = null
    private var jobs = mutableListOf<JobItem>()
    private var photos = mutableListOf<MutableList<File>>() // photos[jobIndex] = list of files
    private var materials = mutableListOf<MaterialItem>()
    
    private var selectedTo = "Engineering"
    private var selectedCategory = "General"
    private var selectedPriority = "Low"
    
    private var isUploading = false
    private var saveDraftHandler: Handler? = null
    private var saveDraftRunnable: Runnable? = null
    
    // Draft key
    private val draftKey = "create_project_draft"
    private lateinit var sharedPreferences: SharedPreferences
    
    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleCameraResult()
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
            showSnackbar("Camera permission is required", false)
        }
    }
    
    // Photo selection state
    private var selectedJobIndexForPhoto = 0
    private var cameraPhotoFile: File? = null
    private var cameraPhotoUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_project)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        
        sharedPreferences = getSharedPreferences("ems_user_prefs", Context.MODE_PRIVATE)
        saveDraftHandler = Handler(Looper.getMainLooper())
        
        setupToolbar()
        initViews()
        setupListeners()
        initializeData()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Create Project"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun initViews() {
        etProjectName = findViewById(R.id.et_project_name)
        spinnerTo = findViewById(R.id.spinner_to)
        etLocation = findViewById(R.id.et_location)
        spinnerCategory = findViewById(R.id.spinner_category)
        spinnerPriority = findViewById(R.id.spinner_priority)
        layoutLocationSuggestions = findViewById(R.id.layout_location_suggestions)
        
        recyclerViewJobs = findViewById(R.id.recycler_view_jobs)
        btnAddJob = findViewById(R.id.btn_add_job)
        
        recyclerViewMaterials = findViewById(R.id.recycler_view_materials)
        btnAddMaterial = findViewById(R.id.btn_add_material)
        tvTotalAmount = findViewById(R.id.tv_total_amount)
        
        btnCancel = findViewById(R.id.btn_cancel)
        btnSubmit = findViewById(R.id.btn_submit)
        progressBar = findViewById(R.id.progress_bar)
        tvProgress = findViewById(R.id.tv_progress)
        
        // Setup RecyclerViews
        recyclerViewJobs.layoutManager = LinearLayoutManager(this)
        recyclerViewMaterials.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupListeners() {
        btnCancel.setOnClickListener {
            finish()
        }
        
        btnSubmit.setOnClickListener {
            hideKeyboard()
            submitProject()
        }
        
        btnAddJob.setOnClickListener {
            addNewJobRow()
        }
        
        btnAddMaterial.setOnClickListener {
            addMaterial()
        }
        
        // Project Name - capitalize first letter of each word
        etProjectName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
                    val text = s.toString()
                    val capitalized = text.split(" ").joinToString(" ") { word ->
                        word.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    if (text != capitalized) {
                        etProjectName.removeTextChangedListener(this)
                        etProjectName.setText(capitalized)
                        etProjectName.setSelection(capitalized.length)
                        etProjectName.addTextChangedListener(this)
                    }
                }
                saveDraft()
            }
        })
        
        // Location autocomplete
        etLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterLocations(s?.toString() ?: "")
                saveDraft()
            }
        })
        
        spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    selectedTo = toList[position]
                    saveDraft()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    selectedCategory = categoryList[position]
                    saveDraft()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPriority = priorityList[position]
                saveDraft()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                currentPropID = user?.propID
                username = user?.username ?: user?.email
                userDept = UserService.getCurrentDept() ?: user?.dept
                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    loadDraft() // Load draft first
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
                
                // Set departments
                toList.clear()
                toList.add("To:") // Placeholder
                toList.addAll(departmentsResult)
                if (!toList.contains("Engineering")) {
                    toList.add("Engineering")
                }
                
                // Set categories
                categoryList.clear()
                categoryList.add("Category") // Placeholder
                var categories = mutableListOf("General")
                categories.addAll(categoriesResult.filter { it != "General" })
                categoryList.addAll(categories)
                
                // Set locations
                lokasiList.clear()
                lokasiList.addAll(locationsResult)
                
                setupSpinners()
                
                // Initialize jobs if empty
                if (jobs.isEmpty()) {
                    jobs.add(JobItem(description = ""))
                    photos.add(mutableListOf())
                }
                
                updateJobsAdapter()
                updateMaterialsAdapter()
                updateTotalAmount()
            } catch (e: Exception) {
                showSnackbar("Failed to load master data: ${e.message}", false)
            }
        }
    }
    
    private fun setupSpinners() {
        val placeholderColor = resources.getColor(android.R.color.darker_gray, null)
        val normalColor = resources.getColor(R.color.black, null)
        
        // To Spinner
        val toAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, toList) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
        }
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTo.adapter = toAdapter
        
        // Category Spinner
        val categoryAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categoryList) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(if (position == 0) placeholderColor else normalColor)
                return view
            }
        }
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter
        
        // Priority Spinner
        val priorityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorityList)
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
        
        // Set defaults
        spinnerTo.post {
            val engineeringIndex = toList.indexOf("Engineering")
            spinnerTo.setSelection(if (engineeringIndex > 0) engineeringIndex else 1)
        }
        
        spinnerCategory.post {
            val generalIndex = categoryList.indexOf("General")
            spinnerCategory.setSelection(if (generalIndex > 0) generalIndex else 1)
        }
        
        spinnerPriority.post {
            val lowIndex = priorityList.indexOf("Low")
            spinnerPriority.setSelection(if (lowIndex >= 0) lowIndex else 0)
        }
    }
    
    private fun filterLocations(query: String) {
        if (query.isEmpty()) {
            locationSuggestions.clear()
            layoutLocationSuggestions.visibility = View.GONE
            return
        }
        
        if (lokasiList.contains(query)) {
            locationSuggestions.clear()
            layoutLocationSuggestions.visibility = View.GONE
            return
        }
        
        locationSuggestions.clear()
        locationSuggestions.addAll(
            lokasiList.filter {
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
            val textView = TextView(this).apply {
                text = location
                setPadding(32, 16, 32, 16)
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = resources.getDrawable(R.drawable.suggestions_background, null)
                setOnClickListener {
                    etLocation.setText(location)
                    layoutLocationSuggestions.visibility = View.GONE
                    hideKeyboard()
                }
            }
            layoutLocationSuggestions.addView(textView)
        }
    }
    
    // Jobs Section
    private fun addNewJobRow() {
        if (jobs.size >= 20) {
            showSnackbar("Maximum 20 jobs reached", false)
            return
        }
        
        // Check if last row is filled
        val lastJob = jobs.lastOrNull()
        val lastIndex = jobs.size - 1
        val jobFilled = !lastJob?.description?.trim().isNullOrEmpty()
        val photoFilled = lastIndex < photos.size && photos[lastIndex].isNotEmpty()
        
        if (!jobFilled && !photoFilled) {
            showSnackbar("Please fill the current job or add a photo first", false)
            return
        }
        
        jobs.add(JobItem(description = ""))
        photos.add(mutableListOf())
        updateJobsAdapter()
        saveDraft()
    }
    
    private fun updateJobsAdapter() {
        val adapter = recyclerViewJobs.adapter as? JobsAdapter
        if (adapter != null) {
            adapter.updateJobs(jobs)
            adapter.photos = photos
            adapter.notifyDataSetChanged()
        } else {
            val newAdapter = JobsAdapter(
                jobs = jobs,
                onPhotoClick = { jobIndex ->
                    selectedJobIndexForPhoto = jobIndex
                    if (jobIndex < photos.size && photos[jobIndex].isNotEmpty()) {
                        showPhotoGallery(jobIndex)
                    } else {
                        showImageSourceDialog()
                    }
                },
                onJobDescriptionChange = { jobIndex, newDescription ->
                    if (jobIndex < jobs.size) {
                        jobs[jobIndex].description = newDescription
                        saveDraft()
                    }
                },
                onRemoveJob = { jobIndex ->
                    removeJob(jobIndex)
                },
                onAddPhoto = { jobIndex ->
                    selectedJobIndexForPhoto = jobIndex
                    showImageSourceDialog()
                }
            )
            newAdapter.photos = photos
            recyclerViewJobs.adapter = newAdapter
        }
    }
    
    private fun removeJob(jobIndex: Int) {
        if (jobIndex < jobs.size) {
            jobs.removeAt(jobIndex)
            if (jobIndex < photos.size) {
                photos.removeAt(jobIndex)
            }
            if (jobs.isEmpty()) {
                jobs.add(JobItem(description = ""))
                photos.add(mutableListOf())
            }
            updateJobsAdapter()
            saveDraft()
        }
    }
    
    // Materials Section
    private fun addMaterial() {
        materials.add(MaterialItem(materialName = "", quantity = "", unitPrice = "", unit = "pcs"))
        updateMaterialsAdapter()
        updateTotalAmount()
        saveDraft()
    }
    
    private fun removeMaterial(material: MaterialItem) {
        materials.removeAll { it.id == material.id }
        updateMaterialsAdapter()
        updateTotalAmount()
        saveDraft()
    }
    
    private fun updateMaterialsAdapter() {
        val adapter = recyclerViewMaterials.adapter as? MaterialsAdapter
        if (adapter != null) {
            adapter.updateMaterials(materials)
            adapter.notifyDataSetChanged()
        } else {
            recyclerViewMaterials.adapter = MaterialsAdapter(
                materials = materials,
                onMaterialChange = { material ->
                    updateTotalAmount()
                    saveDraft()
                },
                onRemoveMaterial = { material ->
                    removeMaterial(material)
                }
            )
        }
    }
    
    private fun updateTotalAmount() {
        val total = materials.sumOf { it.amount }
        val symbols = DecimalFormatSymbols(Locale("id", "ID"))
        symbols.currencySymbol = "Rp "
        val formatter = DecimalFormat("#,##0.00", symbols)
        tvTotalAmount.text = "Total: Rp ${formatter.format(total)}"
        tvTotalAmount.visibility = if (materials.isNotEmpty()) View.VISIBLE else View.GONE
    }
    
    // Photo handling
    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(this)
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
        if (PermissionUtils.isCameraPermissionGranted(this)) {
            openCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun openCamera() {
        cameraPhotoFile = createImageFile()
        cameraPhotoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            cameraPhotoFile!!
        )
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val resInfoList = packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            grantUriPermission(
                packageName,
                cameraPhotoUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        
        cameraLauncher.launch(intent)
    }
    
    private fun openGallery() {
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        galleryLauncher.launch(intent)
    }
    
    private fun handleCameraResult() {
        lifecycleScope.launch {
            val file = cameraPhotoFile
            if (file != null && file.exists() && file.length() > 0) {
                // Resize + crop ke 480x480 sebelum upload
                val resizedFile = withContext(Dispatchers.IO) {
                    resizeCrop(file, size = 480, quality = 90)
                }
                addPhotoToJob(selectedJobIndexForPhoto, resizedFile)
            } else {
                @Suppress("DEPRECATION")
                val thumb = intent.getParcelableExtra<Bitmap>("data")
                thumb?.let {
                    try {
                        val fallback = createImageFile()
                        FileOutputStream(fallback).use { out ->
                            it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        // Resize + crop ke 480x480 sebelum upload
                        val resizedFile = withContext(Dispatchers.IO) {
                            resizeCrop(fallback, size = 480, quality = 90)
                        }
                        addPhotoToJob(selectedJobIndexForPhoto, resizedFile)
                    } catch (e: Exception) {
                        showSnackbar("Failed to save camera image: ${e.message}", false)
                    }
                } ?: showSnackbar("Camera file not found", false)
            }
        }
    }
    
    private fun handleGalleryImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                val tempFile = createImageFile()
                val outputStream = FileOutputStream(tempFile)
                
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.close()
                
                // Resize + crop ke 480x480 sebelum upload
                val resizedFile = withContext(Dispatchers.IO) {
                    resizeCrop(tempFile, size = 480, quality = 90)
                }
                addPhotoToJob(selectedJobIndexForPhoto, resizedFile)
            } catch (e: Exception) {
                showSnackbar("Failed to load image: ${e.message}", false)
            }
        }
    }
    
    private fun addPhotoToJob(jobIndex: Int, photoFile: File) {
        while (photos.size <= jobIndex) {
            photos.add(mutableListOf())
        }
        photos[jobIndex].add(photoFile)
        updateJobsAdapter()
        saveDraft()
    }
    
    private fun showPhotoGallery(jobIndex: Int) {
        if (jobIndex >= photos.size || photos[jobIndex].isEmpty()) {
            return
        }
        
        val photoFiles = photos[jobIndex]
        val photoUris = photoFiles.map { FileProvider.getUriForFile(this, "${packageName}.fileprovider", it) }
        
        // Show dialog with photos
        val dialogView = layoutInflater.inflate(R.layout.dialog_photo_gallery, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_view_photos)
        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        
        val adapter = PhotoGalleryAdapter(
            photoUris = photoUris,
            onRemovePhoto = { photoIndex ->
                // Remove photo
                if (jobIndex < photos.size && photoIndex < photos[jobIndex].size) {
                    photos[jobIndex].removeAt(photoIndex)
                    updateJobsAdapter()
                    saveDraft()
                }
            }
        )
        recyclerView.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Photo ${jobIndex + 1}")
            .setView(dialogView)
            .setPositiveButton("Add Photo") { _, _ ->
                selectedJobIndexForPhoto = jobIndex
                showImageSourceDialog()
            }
            .setNegativeButton("Done", null)
            .create()
        
        dialog.show()
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    // === KEMBALI KE HELPER LAMA: resize proporsional, sisi terpanjang = maxSide ===
    private fun resizeJpegInPlace(file: File, maxSide: Int = 420, quality: Int = 90): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return file

        val sample = calculateInSampleSize(srcW, srcH, maxSide)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return file

        val longest = maxOf(bmp.width, bmp.height)
        val scale = longest.toFloat() / maxSide
        val finalBmp = if (scale > 1f) {
            val w = (bmp.width / scale).toInt()
            val h = (bmp.height / scale).toInt()
            Bitmap.createScaledBitmap(bmp, w, h, true)
        } else bmp

        FileOutputStream(file, false).use { out ->
            finalBmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
        }

        if (finalBmp !== bmp) bmp.recycle()
        return file
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, reqMaxSide: Int): Int {
        var inSampleSize = 1
        val longest = maxOf(srcW, srcH)
        while (longest / inSampleSize > reqMaxSide * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
    
    // Draft save/load
    private fun saveDraft() {
        saveDraftRunnable?.let { saveDraftHandler?.removeCallbacks(it) }
        
        saveDraftRunnable = Runnable {
            performSaveDraft()
        }
        
        saveDraftHandler?.postDelayed(saveDraftRunnable!!, 500)
    }
    
    private fun performSaveDraft() {
        try {
            val draftData = JSONObject().apply {
                put("projectName", etProjectName.text.toString())
                put("lokasi", etLocation.text.toString())
                put("selectedTo", selectedTo)
                put("selectedCategory", selectedCategory)
                put("selectedPriority", selectedPriority)
                
                // Save jobs
                val jobsArray = JSONArray()
                jobs.forEach { job ->
                    jobsArray.put(JSONObject().apply {
                        put("id", job.id)
                        put("description", job.description)
                    })
                }
                put("jobs", jobsArray.toString())
                
                // Save materials
                val materialsArray = JSONArray()
                materials.forEach { material ->
                    materialsArray.put(JSONObject().apply {
                        put("id", material.id)
                        put("materialName", material.materialName)
                        put("quantity", material.quantity)
                        put("unitPrice", material.unitPrice)
                        put("unit", material.unit)
                    })
                }
                put("materials", materialsArray.toString())
                
                // Save photo counts
                val photoCounts = JSONArray()
                photos.forEach { photoList ->
                    photoCounts.put(photoList.size)
                }
                put("photoCounts", photoCounts.toString())
            }
            
            sharedPreferences.edit()
                .putString(draftKey, draftData.toString())
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("CreateProjectActivity", "Failed to save draft: ${e.message}")
        }
    }
    
    private fun loadDraft() {
        try {
            val draftJson = sharedPreferences.getString(draftKey, null) ?: return
            
            val draftData = JSONObject(draftJson)
            
            etProjectName.setText(draftData.optString("projectName", ""))
            etLocation.setText(draftData.optString("lokasi", ""))
            selectedTo = draftData.optString("selectedTo", "Engineering")
            selectedCategory = draftData.optString("selectedCategory", "General")
            selectedPriority = draftData.optString("selectedPriority", "Low")
            
            // Load jobs
            val jobsJson = draftData.optString("jobs", "[]")
            val jobsArray = JSONArray(jobsJson)
            jobs.clear()
            for (i in 0 until jobsArray.length()) {
                val jobObj = jobsArray.getJSONObject(i)
                jobs.add(JobItem(
                    id = jobObj.optString("id", UUID.randomUUID().toString()),
                    description = jobObj.optString("description", "")
                ))
            }
            
            // Load materials
            val materialsJson = draftData.optString("materials", "[]")
            val materialsArray = JSONArray(materialsJson)
            materials.clear()
            for (i in 0 until materialsArray.length()) {
                val materialObj = materialsArray.getJSONObject(i)
                materials.add(MaterialItem(
                    id = materialObj.optString("id", UUID.randomUUID().toString()),
                    materialName = materialObj.optString("materialName", ""),
                    quantity = materialObj.optString("quantity", ""),
                    unitPrice = materialObj.optString("unitPrice", ""),
                    unit = materialObj.optString("unit", "pcs")
                ))
            }
            
            // Initialize photos array (photos themselves are not saved in draft)
            val photoCountsJson = draftData.optString("photoCounts", "[]")
            val photoCountsArray = JSONArray(photoCountsJson)
            photos.clear()
            for (i in 0 until maxOf(jobs.size, photoCountsArray.length())) {
                photos.add(mutableListOf())
            }
            
            if (jobs.isEmpty()) {
                jobs.add(JobItem(description = ""))
                photos.add(mutableListOf())
            }
        } catch (e: Exception) {
            android.util.Log.e("CreateProjectActivity", "Failed to load draft: ${e.message}")
            // Reset form if draft load fails
            resetForm()
        }
    }
    
    private fun clearDraft() {
        sharedPreferences.edit().remove(draftKey).apply()
    }
    
    private fun resetForm() {
        etProjectName.text.clear()
        etLocation.text.clear()
        jobs.clear()
        jobs.add(JobItem(description = ""))
        photos.clear()
        photos.add(mutableListOf())
        materials.clear()
        selectedTo = "Engineering"
        selectedCategory = "General"
        selectedPriority = "Low"
    }
    
    // Submit Project
    private fun submitProject() {
        // Validation
        val projectName = etProjectName.text.toString().trim()
        val location = etLocation.text.toString().trim()
        
        if (projectName.isEmpty()) {
            showSnackbar("Project name is required", false)
            return
        }
        
        if (location.isEmpty()) {
            showSnackbar("Location is required", false)
            return
        }
        
        val validJobs = jobs.filter { !it.description.trim().isEmpty() }
        if (validJobs.isEmpty()) {
            showSnackbar("At least one job description is required", false)
            return
        }
        
        if (currentPropID.isNullOrEmpty() || username.isNullOrEmpty() || userDept.isNullOrEmpty()) {
            showSnackbar("User data not found", false)
            return
        }
        
        isUploading = true
        btnSubmit.isEnabled = false
        btnSubmit.text = "Submitting..."
        progressBar.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Uploading..."
        
        lifecycleScope.launch {
            try {
                // Prepare data
                val jobDescriptions = validJobs.map { it.description }
                val jobsJson = JSONArray(jobDescriptions).toString()
                
                val materialsData = materials.map { material ->
                    mapOf(
                        "material_name" to material.materialName,
                        "quantity" to material.quantity,
                        "unit_price" to material.unitPrice,
                        "unit" to material.unit,
                        "amount" to material.amount.toString()
                    )
                }
                val materialsJson = JSONArray(materialsData.map { JSONObject(it) }).toString()
                
                // Prepare photos and job indices
                var allPhotos: List<Pair<Int, File>> = emptyList()
                for (jobIndex in jobs.indices) {
                    if (jobIndex < photos.size) {
                        photos[jobIndex].forEach { photoFile ->
                            allPhotos = allPhotos + (jobIndex to photoFile)
                        }
                    }
                }
                
                // Create multipart request
                val actionBody = "create".toRequestBody("text/plain".toMediaTypeOrNull())
                val propIDBody = currentPropID!!.toRequestBody("text/plain".toMediaTypeOrNull())
                val projectNameBody = projectName.toRequestBody("text/plain".toMediaTypeOrNull())
                val priorityBody = selectedPriority.toRequestBody("text/plain".toMediaTypeOrNull())
                val lokasiBody = location.toRequestBody("text/plain".toMediaTypeOrNull())
                val categoryBody = selectedCategory.toRequestBody("text/plain".toMediaTypeOrNull())
                val orderByBody = username!!.toRequestBody("text/plain".toMediaTypeOrNull())
                val deptBody = userDept!!.toRequestBody("text/plain".toMediaTypeOrNull())
                val wotoBody = selectedTo.toRequestBody("text/plain".toMediaTypeOrNull())
                val statusBody = "new".toRequestBody("text/plain".toMediaTypeOrNull())
                val jobsBody = jobsJson.toRequestBody("text/plain".toMediaTypeOrNull())
                val materialsBody = materialsJson.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val jobIndexParts = allPhotos.mapIndexed { index, (jobIndex, _) ->
                    MultipartBody.Part.createFormData("job_index[$index]", jobIndex.toString())
                }
                
                val photoParts = allPhotos.mapIndexed { index, (_, photoFile) ->
                    val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val timestamp = System.currentTimeMillis()
                    val filename = "PROJECT_${currentPropID}_${timestamp}_$index.jpg"
                    MultipartBody.Part.createFormData("photos[$index]", filename, requestFile)
                }
                
                // Check internet connection
                val hasInternet = withContext(Dispatchers.IO) {
                    NetworkUtils.hasServerConnection()
                }
                
                if (!hasInternet) {
                    // No internet: Save to offline queue
                    saveProjectOffline(projectName, location, validJobs, jobsJson, materialsJson, materials, allPhotos)
                    return@launch
                }
                
                val result = withContext(Dispatchers.IO) {
                    try {
                        RetrofitClient.apiService.createProject(
                            action = actionBody,
                            propID = propIDBody,
                            projectName = projectNameBody,
                            priority = priorityBody,
                            lokasi = lokasiBody,
                            category = categoryBody,
                            orderBy = orderByBody,
                            dept = deptBody,
                            woto = wotoBody,
                            status = statusBody,
                            jobs = jobsBody,
                            materials = materialsBody,
                            photoFiles = photoParts,
                            jobIndexParts = jobIndexParts
                        )
                    } catch (e: Exception) {
                        mapOf("error" to (e.message ?: "Unknown error"))
                    }
                }
                
                if (result["status"] == "success" || result["success"] == true) {
                    clearDraft()
                    showSnackbar("Project created successfully!", true)
                    
                    // Send notification to target department (like iOS and work order)
                    NotificationService.sendProjectNotification(
                        projectName = projectName,
                        location = location,
                        targetDepartment = selectedTo
                    )
                    
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    // Submit failed: Save to offline queue
                    val errorMsg = result["error"]?.toString() ?: result["message"]?.toString() ?: "Unknown error"
                    saveProjectOffline(projectName, location, validJobs, jobsJson, materialsJson, materials, allPhotos, errorMsg)
                }
            } catch (e: Exception) {
                // Network error: Save to offline queue
                saveProjectOffline(
                    etProjectName.text.toString().trim(),
                    etLocation.text.toString().trim(),
                    jobs.filter { !it.description.trim().isEmpty() },
                    "",
                    "",
                    materials,
                    emptyList(),
                    e.message
                )
            } finally {
                isUploading = false
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit"
                progressBar.visibility = View.GONE
                tvProgress.visibility = View.GONE
            }
        }
    }
    
    private suspend fun saveProjectOffline(
        projectName: String,
        location: String,
        validJobs: List<JobItem>,
        jobsJson: String,
        materialsJson: String,
        materials: List<MaterialItem>,
        allPhotos: List<Pair<Int, File>>,
        errorMsg: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // Prepare jobs JSON if not provided
            val finalJobsJson = jobsJson.ifEmpty {
                val jobDescriptions = validJobs.map { it.description }
                JSONArray(jobDescriptions).toString()
            }
            
            // Prepare materials JSON if not provided
            val finalMaterialsJson = materialsJson.ifEmpty {
                val materialsData = materials.map { material ->
                    mapOf(
                        "material_name" to material.materialName,
                        "quantity" to material.quantity,
                        "unit_price" to material.unitPrice,
                        "unit" to material.unit,
                        "amount" to material.amount.toString()
                    )
                }
                JSONArray(materialsData.map { JSONObject(it) }).toString()
            }
            
            // Copy photos to persistent location
            val offlineDir = File(getExternalFilesDir(null), "offline_photos")
            if (!offlineDir.exists()) {
                offlineDir.mkdirs()
            }
            
            val photoPaths = mutableListOf<String>()
            val jobIndices = mutableListOf<Int>()
            
            allPhotos.forEach { (jobIndex, photoFile) ->
                if (photoFile.exists()) {
                    val persistentFile = File(offlineDir, "PROJECT_${currentPropID}_${System.currentTimeMillis()}_${photoPaths.size}.jpg")
                    photoFile.copyTo(persistentFile, overwrite = true)
                    photoPaths.add(persistentFile.absolutePath)
                    jobIndices.add(jobIndex)
                }
            }
            
            val photoPathsJson = JSONArray(photoPaths).toString()
            val jobIndexJson = JSONArray(jobIndices).toString()
            
            // Create pending project
            val pendingProject = PendingProject(
                propID = currentPropID ?: "",
                projectName = projectName,
                lokasi = location,
                category = selectedCategory,
                priority = selectedPriority,
                orderBy = username ?: "",
                dept = userDept ?: "",
                woto = selectedTo,
                status = "new",
                jobsJson = finalJobsJson,
                materialsJson = finalMaterialsJson,
                photoPathsJson = photoPathsJson,
                jobIndexJson = jobIndexJson,
                requestType = "create",
                lastError = errorMsg ?: "No internet connection"
            )
            
            // Save to database
            OfflineProjectService.addPendingProject(pendingProject)
            
            withContext(Dispatchers.Main) {
                clearDraft()
                showSnackbar("Project saved offline. Will upload when internet is available.", true)
                setResult(Activity.RESULT_OK)
                finish()
            }
            
            // Schedule sync when internet is available
            SyncService.scheduleSync(this@CreateProjectActivity)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showSnackbar("Failed to save offline: ${e.message}", false)
            }
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
    
    private fun showSnackbar(message: String, isSuccess: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        if (isSuccess) {
            snackbar.setBackgroundTint(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            snackbar.setBackgroundTint(resources.getColor(android.R.color.holo_red_dark, null))
        }
        snackbar.show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        saveDraftRunnable?.let { saveDraftHandler?.removeCallbacks(it) }
    }
}

// Adapter classes will be in separate files or here
// For now, I'll create placeholder adapters

