package com.sofindo.ems.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import com.sofindo.ems.services.OfflineQueueService
import com.sofindo.ems.services. SyncService
import com.sofindo.ems.database.PendingWorkOrder
import com.sofindo.ems.utils.PermissionUtils
import com.sofindo.ems.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ChangePendingDoneActivity : AppCompatActivity() {
    
    private lateinit var etRemarks: EditText
    private lateinit var btnUploadPhoto: Button
    private lateinit var btnSubmit: Button
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var tvLoading: TextView
    private lateinit var loadingOverlay: View
    
    private var workOrder: Map<String, Any> = emptyMap()
    private var status: String = ""
    private var userName: String = ""
    private var selectedImageFile: File? = null
    private var isLoading = false
    private var photoDoneRequired: Int = 0 // 0 = optional, 1 = required
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleGalleryImage(uri)
            }
        }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val file = selectedImageFile
                if (file != null && file.exists() && file.length() > 0) {
                    val resizedFile = resizeJpegInPlace(file, maxSide = 420, quality = 90)
                    selectedImageFile = resizedFile
                    updatePhotoUI()
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.extras?.getParcelable<Bitmap>("data")?.let { thumb ->
                        try {
                            val fallback = createImageFile()
                            FileOutputStream(fallback).use { out ->
                                thumb.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            val resizedFile = resizeJpegInPlace(fallback, maxSide = 420, quality = 90)
                            selectedImageFile = resizedFile
                            updatePhotoUI()
                        } catch (e: Exception) {
                            Toast.makeText(this@ChangePendingDoneActivity, "Failed to save camera image: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(this@ChangePendingDoneActivity, "Camera file not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_pending_done)
        

        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        // Get data from intent
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        workOrder = (intent.getSerializableExtra("workOrder") as? Map<String, Any>) ?: emptyMap()
        status = intent.getStringExtra("status") ?: ""
        
        // Get current user directly
        lifecycleScope.launch {
            val user = UserService.getCurrentUser()
            userName = user?.username ?: ""
        }
        
        initViews()
        setupToolbar()
        setupPhotoSection()
        setupListeners()
        
        // Initialize submit button state
        updateSubmitButtonState()
        
        // Load admin setting if status is "done"
        if (status.lowercase() == "done") {
            loadAdminSetting()
        }
    }
    
    private fun initViews() {
        etRemarks = findViewById(R.id.et_remarks)
        btnUploadPhoto = findViewById(R.id.btn_upload_photo)
        btnSubmit = findViewById(R.id.btn_submit)
        ivPhotoPreview = findViewById(R.id.iv_photo_preview)
        tvLoading = findViewById(R.id.tv_loading)
        loadingOverlay = findViewById(R.id.loading_overlay)
    }
    
    private fun setupPhotoSection() {
        // Hide upload photo section if status is "pending"
        if (status.lowercase() == "pending") {
            btnUploadPhoto.visibility = View.GONE
            ivPhotoPreview.visibility = View.GONE
            
            // Reposition submit button to be below remarks
            val params = btnSubmit.layoutParams as android.widget.RelativeLayout.LayoutParams
            params.removeRule(android.widget.RelativeLayout.BELOW)
            params.addRule(android.widget.RelativeLayout.BELOW, R.id.et_remarks)
            // Convert 16dp to pixels
            val scale = resources.displayMetrics.density
            params.topMargin = (16 * scale + 0.5f).toInt()
            btnSubmit.layoutParams = params
        }
    }
    
    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "Set to ${status.uppercase()}"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_close)
        }
    }
    
    private fun setupListeners() {
        // Only set click listener for upload photo if status is "done"
        if (status.lowercase() != "pending") {
            btnUploadPhoto.setOnClickListener {
                showImageSourceDialog()
            }
        }
        
        btnSubmit.setOnClickListener {
            submitChange()
        }
        
        // Enable/disable submit button based on remarks and photo requirement
        etRemarks.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSubmitButtonState()
            }
        })
    }
    
    private fun loadAdminSetting() {
        lifecycleScope.launch {
            try {
                val propID = UserService.getCurrentPropID()
                if (propID.isNullOrEmpty()) {
                    // Default to optional if propID not available
                    photoDoneRequired = 0
                    updateSubmitButtonState()
                    return@launch
                }
                
                val response = RetrofitClient.apiService.getAdminSetting(propID)
                val success = response["success"] as? Boolean ?: false
                
                if (success) {
                    photoDoneRequired = (response["photoDone"] as? Number)?.toInt() ?: 0
                } else {
                    // Default to optional if API fails
                    photoDoneRequired = 0
                }
                
                updateSubmitButtonState()
            } catch (e: Exception) {
                // Default to optional if error
                photoDoneRequired = 0
                updateSubmitButtonState()
            }
        }
    }
    
    private fun updateSubmitButtonState() {
        val remarksNotEmpty = !etRemarks.text.isNullOrEmpty() && etRemarks.text.toString().trim().isNotEmpty()
        
        if (status.lowercase() == "done" && photoDoneRequired == 1) {
            // Photo required: enable only if remarks not empty AND photo selected
            btnSubmit.isEnabled = remarksNotEmpty && selectedImageFile != null
        } else {
            // Photo optional or status is pending: enable if remarks not empty
            btnSubmit.isEnabled = remarksNotEmpty
        }
    }
    
    
    
    
    private fun showImageSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select Photo Source")
            .setItems(arrayOf("Camera", "Photo Library")) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> openGallery()
                }
            }
            .show()
    }
    
    private fun checkCameraPermission() {
        when {
            PermissionUtils.isCameraPermissionGranted(this) -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun openCamera() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(null)
            selectedImageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                selectedImageFile!!
            )
            
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val resInfoList = packageManager.queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    photoURI,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        galleryLauncher.launch(intent)
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
                
                val resizedFile = resizeJpegInPlace(tempFile, maxSide = 420, quality = 90)
                selectedImageFile = resizedFile
                updatePhotoUI()
            } catch (e: Exception) {
                Toast.makeText(this@ChangePendingDoneActivity, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
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
                updateSubmitButtonState()
                hideKeyboard()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to display image preview: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun submitChange() {
        val remarks = etRemarks.text.toString().trim()
        
        if (remarks.isEmpty()) {
            Toast.makeText(this, "Please enter remarks", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate photo requirement for status "done"
        if (status.lowercase() == "done" && photoDoneRequired == 1 && selectedImageFile == null) {
            Toast.makeText(this, "Photo is required for this status", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isLoading) return
        
        isLoading = true
        showLoading(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check internet connection
                val hasInternet = NetworkUtils.hasServerConnection()
                
                if (!hasInternet) {
                    // No internet: Save to offline queue
                    savePendingDoneOffline(remarks)
                    return@launch
                }
                
                // Has internet: Try to update online
                val success = callUpdatePendingDoneAPI(remarks)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            this@ChangePendingDoneActivity,
                            "Status berhasil diupdate ke: ${status.uppercase()}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        // Failed to update: Save to offline queue
                        savePendingDoneOffline(remarks, "Update failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Network error: Save to offline queue
                    savePendingDoneOffline(remarks, e.message)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    showLoading(false)
                }
            }
        }
    }
    
    private suspend fun savePendingDoneOffline(
        remarks: String,
        errorMsg: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val woId = workOrder["woId"]?.toString() ?: ""
            if (woId.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ChangePendingDoneActivity,
                        "Work order ID not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext
            }
            
            // Copy photo to persistent location if exists
            var persistentPhotoPath: String? = null
            selectedImageFile?.let { file ->
                if (file.exists()) {
                    val offlineDir = File(getExternalFilesDir(null), "offline_photos")
                    if (!offlineDir.exists()) {
                        offlineDir.mkdirs()
                    }
                    val persistentFile = File(offlineDir, "${status}${woId}_${System.currentTimeMillis()}.jpg")
                    file.copyTo(persistentFile, overwrite = true)
                    persistentPhotoPath = persistentFile.absolutePath
                }
            }
            
            // Prepare additional data for status 'done'
            var doneBy: String? = null
            var timeDone: String? = null
            var timeSpent: String? = null
            
            if (status.lowercase() == "done") {
                val currentTimeISO = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val startTime = workOrder["mulainya"]?.toString()
                timeSpent = calculateTimeSpent(startTime, currentTimeISO)
                
                doneBy = userName
                timeDone = currentTimeISO
            }
            
            // Create pending work order
            val pendingWO = PendingWorkOrder(
                propID = workOrder["propID"]?.toString() ?: "",
                orderBy = workOrder["orderBy"]?.toString() ?: "",
                job = workOrder["job"]?.toString() ?: "",
                lokasi = workOrder["lokasi"]?.toString() ?: "",
                category = workOrder["category"]?.toString() ?: "",
                dept = workOrder["dept"]?.toString() ?: "",
                priority = workOrder["priority"]?.toString() ?: "",
                woto = workOrder["woto"]?.toString() ?: "",
                status = workOrder["status"]?.toString() ?: "",
                photoPath = persistentPhotoPath,
                requestType = "update_pending_done",
                woId = woId,
                newStatus = status,
                remarks = remarks,
                userName = userName,
                doneBy = doneBy,
                timeDone = timeDone,
                timeSpent = timeSpent,
                lastError = errorMsg ?: "No internet connection"
            )
            
            // Save to database
            OfflineQueueService.addPendingWorkOrder(pendingWO)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ChangePendingDoneActivity,
                    "Status update saved offline. Will sync when internet is available.",
                    Toast.LENGTH_LONG
                ).show()
                
                setResult(Activity.RESULT_OK)
                finish()
            }
            
            // Schedule sync when internet is available
            SyncService.scheduleSync(this@ChangePendingDoneActivity)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ChangePendingDoneActivity,
                    "Failed to save offline: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun calculateTimeSpent(startTime: String?, doneTime: String?): String {
        if (startTime.isNullOrEmpty() || doneTime.isNullOrEmpty()) {
            return "N/A"
        }

        // Check if done time is "0000-00-00" or similar empty date format
        if (doneTime == "0000-00-00" || 
            doneTime == "0000-00-00 00:00:00" || 
            doneTime.contains("0000-00-00")) {
            return "-"
        }

        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val start = dateFormat.parse(startTime)
            val done = dateFormat.parse(doneTime)
            
            if (start == null || done == null) {
                return "Invalid date format"
            }
            
            val differenceInMillis = done.time - start.time
            
            if (differenceInMillis < 0) {
                return "Invalid time range"
            }

            val totalMinutes = differenceInMillis / (1000 * 60)
            val totalHours = differenceInMillis / (1000 * 60 * 60)
            val totalDays = differenceInMillis / (1000 * 60 * 60 * 24)
            
            // Calculate months manually
            val calendarStart = Calendar.getInstance()
            calendarStart.time = start
            val calendarDone = Calendar.getInstance()
            calendarDone.time = done
            
            val totalMonths = (calendarDone.get(Calendar.YEAR) - calendarStart.get(Calendar.YEAR)) * 12 + 
                             (calendarDone.get(Calendar.MONTH) - calendarStart.get(Calendar.MONTH))

            when {
                totalMonths > 0 -> "$totalMonths month${if (totalMonths > 1) "s" else ""}"
                totalDays > 0 -> "$totalDays day${if (totalDays > 1) "s" else ""}"
                totalHours > 0 -> "$totalHours hour${if (totalHours > 1) "s" else ""}"
                totalMinutes > 0 -> "$totalMinutes minute${if (totalMinutes > 1) "s" else ""}"
                else -> "Less than 1 minute"
            }
        } catch (e: Exception) {
            "Invalid date format"
        }
    }

    private suspend fun callUpdatePendingDoneAPI(remarks: String): Boolean {
        return try {
            val woId = workOrder["woId"]?.toString() ?: ""
            
            // Format remarks like in Flutter
            val currentTime = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date())
            val finalRemarks = "$currentTime -> ${status.uppercase()}  ( $userName ) : $remarks"
            
            // Prepare RequestBody for text fields
            val woIdBody = woId.toRequestBody("text/plain".toMediaTypeOrNull())
            val statusBody = status.toRequestBody("text/plain".toMediaTypeOrNull())
            val userNameBody = userName.toRequestBody("text/plain".toMediaTypeOrNull())
            val remarksBody = finalRemarks.toRequestBody("text/plain".toMediaTypeOrNull())
            
            // Prepare additional parameters for status 'done'
            var doneByBody: okhttp3.RequestBody? = null
            var timeDoneBody: okhttp3.RequestBody? = null
            var timeSpentBody: okhttp3.RequestBody? = null
            
            if (status.lowercase() == "done") {
                val currentTimeISO = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val startTime = workOrder["mulainya"]?.toString()
                val timeSpent = calculateTimeSpent(startTime, currentTimeISO)
                
                doneByBody = userName.toRequestBody("text/plain".toMediaTypeOrNull())
                timeDoneBody = currentTimeISO.toRequestBody("text/plain".toMediaTypeOrNull())
                timeSpentBody = timeSpent.toRequestBody("text/plain".toMediaTypeOrNull())
            }
            
            // Prepare photo file if selected (only for status "done")
            val photoPart = if (status.lowercase() == "done") {
                selectedImageFile?.let { file ->
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val photoName = "${status}${woId}.jpg"
                    MultipartBody.Part.createFormData("file", photoName, requestFile)
                }
            } else {
                null
            }
            
            // Call API using RetrofitClient
            val response = RetrofitClient.apiService.updatePendingDone(
                woId = woIdBody,
                status = statusBody,
                userName = userNameBody,
                remarks = remarksBody,
                doneBy = doneByBody,
                timeDone = timeDoneBody,
                timeSpent = timeSpentBody,
                photoFile = photoPart
            )
            
            // Simple response parsing
            val success = response.trim().equals("Success", ignoreCase = true)
            
            success
        } catch (e: Exception) {
            false
        }
    }
    
    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        tvLoading.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val currentFocus = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        currentFocus.clearFocus()
    }
    
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
        
        val longest = kotlin.math.max(bmp.width, bmp.height)
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
        val longest = kotlin.math.max(srcW, srcH)
        while (longest / inSampleSize > reqMaxSide * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
