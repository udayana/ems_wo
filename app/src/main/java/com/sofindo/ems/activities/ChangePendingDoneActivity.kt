package com.sofindo.ems.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
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
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleImageSelection(it) }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageFile?.let { file ->
                handleImageSelection(Uri.fromFile(file))
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_pending_done)
        
        // Get data from intent
        workOrder = (intent.getSerializableExtra("workOrder") as? Map<String, Any>) ?: emptyMap()
        status = intent.getStringExtra("status") ?: ""
        
        // Get current user directly
        lifecycleScope.launch {
            val user = UserService.getCurrentUser()
            userName = user?.username ?: ""
            android.util.Log.d("ChangePendingDone", "Current user: $userName")
        }
        
        initViews()
        setupToolbar()
        setupListeners()
    }
    
    private fun initViews() {
        etRemarks = findViewById(R.id.et_remarks)
        btnUploadPhoto = findViewById(R.id.btn_upload_photo)
        btnSubmit = findViewById(R.id.btn_submit)
        ivPhotoPreview = findViewById(R.id.iv_photo_preview)
        tvLoading = findViewById(R.id.tv_loading)
        loadingOverlay = findViewById(R.id.loading_overlay)
    }
    
    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "Set to ${status.uppercase()}"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_close)
        }
    }
    
    private fun setupListeners() {
        btnUploadPhoto.setOnClickListener {
            showImageSourceDialog()
        }
        
        btnSubmit.setOnClickListener {
            submitChange()
        }
        
        // Enable/disable submit button based on remarks
        etRemarks.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnSubmit.isEnabled = !s.isNullOrEmpty() && s.trim().isNotEmpty()
            }
        })
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
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
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
            
            cameraLauncher.launch(photoURI)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }
    
    private fun handleImageSelection(uri: Uri) {
        try {
            // Convert URI to file
            val inputStream = contentResolver.openInputStream(uri)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(null)
            selectedImageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            
            selectedImageFile?.outputStream()?.use { outputStream ->
                inputStream?.use { input ->
                    input.copyTo(outputStream)
                }
            }
            
            // Show preview
            Glide.with(this)
                .load(selectedImageFile)
                .into(ivPhotoPreview)
            
            ivPhotoPreview.visibility = View.VISIBLE
            
            // Hide keyboard after photo selection
            hideKeyboard()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun submitChange() {
        val remarks = etRemarks.text.toString().trim()
        
        if (remarks.isEmpty()) {
            Toast.makeText(this, "Please enter remarks", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isLoading) return
        
        isLoading = true
        showLoading(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                        Toast.makeText(
                            this@ChangePendingDoneActivity,
                            "Gagal mengupdate status",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ChangePendingDoneActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    showLoading(false)
                }
            }
        }
    }
    
    private suspend fun callUpdatePendingDoneAPI(remarks: String): Boolean {
        return try {
            val woId = workOrder["woId"]?.toString() ?: ""
            
            // Format remarks seperti di Flutter
            val currentTime = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date())
            val finalRemarks = "$currentTime -> ${status.uppercase()}  ( $userName ) : $remarks"
            
            android.util.Log.d("ChangePendingDone", "Calling API with:")
            android.util.Log.d("ChangePendingDone", "woId: $woId")
            android.util.Log.d("ChangePendingDone", "status: $status")
            android.util.Log.d("ChangePendingDone", "userName: $userName")
            android.util.Log.d("ChangePendingDone", "remarks: $finalRemarks")
            
            // Prepare RequestBody for text fields
            val woIdBody = woId.toRequestBody("text/plain".toMediaTypeOrNull())
            val statusBody = status.toRequestBody("text/plain".toMediaTypeOrNull())
            val userNameBody = userName.toRequestBody("text/plain".toMediaTypeOrNull())
            val remarksBody = finalRemarks.toRequestBody("text/plain".toMediaTypeOrNull())
            
            // Prepare photo file if selected
            val photoPart = selectedImageFile?.let { file ->
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val photoName = "${status}${woId}.jpg"
                MultipartBody.Part.createFormData("file", photoName, requestFile)
            }
            
            // Call API using RetrofitClient
            val response = RetrofitClient.apiService.updatePendingDone(
                woId = woIdBody,
                status = statusBody,
                userName = userNameBody,
                remarks = remarksBody,
                photoFile = photoPart
            )
            
            android.util.Log.d("ChangePendingDone", "Raw API Response: '$response'")
            
            // Simple response parsing
            val success = response.trim().equals("Success", ignoreCase = true)
            
            android.util.Log.d("ChangePendingDone", "Parsed success: $success")
            
            success
        } catch (e: Exception) {
            android.util.Log.e("ChangePendingDone", "API Error: ${e.message}", e)
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
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
