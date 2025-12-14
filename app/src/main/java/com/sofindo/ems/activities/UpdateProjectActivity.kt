package com.sofindo.ems.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import com.sofindo.ems.utils.NetworkUtils
import com.sofindo.ems.services.OfflineProjectService
import com.sofindo.ems.services.SyncService
import com.sofindo.ems.database.PendingProject
import com.sofindo.ems.dialogs.ImageViewerDialog
import com.sofindo.ems.models.ProjectDetail
import com.sofindo.ems.models.ProjectJob
import com.sofindo.ems.models.ProjectPhoto
import org.json.JSONArray
import okhttp3.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class UpdateProjectActivity : AppCompatActivity() {

    private lateinit var toolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var btnSave: Button
    private lateinit var tvProjectName: TextView
    
    private lateinit var spStatus: Spinner
    private lateinit var etNote: EditText
    private lateinit var llJobs: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    
    private var projectId: String = ""
    private var propID: String = ""
    private var projectName: String = ""
    private var currentStatus: String = ""
    private var projectDetail: ProjectDetail? = null
    
    // Map untuk menyimpan photos baru per job: jobIndex -> list of Pair(photoId, Uri)
    // photoId adalah ID dari photo before yang akan diupdate
    private val newPhotosByJob: MutableMap<Int, MutableList<Pair<String, Uri>>> = mutableMapOf()
    private var selectedJobIndexForPhoto: Int = -1
    private var selectedBeforePhotoId: String = ""
    
    private val httpClient = OkHttpClient()
    
    private val statusList = listOf("new", "on progress", "pending", "done", "cancelled")
    
    // Photo selection state
    private var cameraPhotoFile: File? = null
    private var cameraPhotoUri: Uri? = null
    
    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val file = cameraPhotoFile
                if (file != null && file.exists() && file.length() > 0) {
                    val resizedFile = resizeJpegInPlace(file, maxSide = 420, quality = 90)
                    val uri = FileProvider.getUriForFile(
                        this@UpdateProjectActivity,
                        "${packageName}.fileprovider",
                        resizedFile
                    )
                    addPhotoToJob(selectedJobIndexForPhoto, uri)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.extras?.getParcelable<Bitmap>("data")?.let { thumb ->
                        try {
                            val fallback = createImageFile()
                            FileOutputStream(fallback).use { out ->
                                thumb.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            val resizedFile = resizeJpegInPlace(fallback, maxSide = 420, quality = 90)
                            val uri = FileProvider.getUriForFile(
                                this@UpdateProjectActivity,
                                "${packageName}.fileprovider",
                                resizedFile
                            )
                            addPhotoToJob(selectedJobIndexForPhoto, uri)
                        } catch (e: Exception) {
                            Toast.makeText(this@UpdateProjectActivity, "Failed to save camera image: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(this@UpdateProjectActivity, "Camera file not found", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_project)


        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        projectId = intent.getStringExtra("projectId") ?: ""
        propID = intent.getStringExtra("propID") ?: ""
        projectName = intent.getStringExtra("projectName") ?: ""
        currentStatus = intent.getStringExtra("currentStatus") ?: ""

        if (projectId.isEmpty() || propID.isEmpty()) {
            Toast.makeText(this, "Project ID atau propID tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        setupStatusSpinner()
        setupListeners()
        loadProjectDetail()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        backButton = toolbar.findViewById(R.id.back_button)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        btnSave = findViewById(R.id.btn_save)
        tvProjectName = findViewById(R.id.tv_project_name)
        
        spStatus = findViewById(R.id.sp_status)
        etNote = findViewById(R.id.et_note)
        llJobs = findViewById(R.id.ll_jobs)
        scrollView = findViewById(R.id.scroll_view)
        loadingContainer = findViewById(R.id.loading_container)
        progressBar = findViewById(R.id.progress_bar)
        
        toolbarTitle.text = "Update Project"
        tvProjectName.text = projectName
    }

    private fun setupToolbar() {
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupStatusSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spStatus.adapter = adapter
        
        // Set current status
        val currentIndex = statusList.indexOf(currentStatus.lowercase())
        if (currentIndex >= 0) {
            spStatus.setSelection(currentIndex)
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveUpdate()
        }
    }

    private fun loadProjectDetail() {
        showLoading(true)
        
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("emshotels.net")
            .addPathSegment("apiKu")
            .addPathSegment("project-read.php")
            .addQueryParameter("projectId", projectId)
            .addQueryParameter("propID", propID)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@UpdateProjectActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    runOnUiThread {
                        showLoading(false)
                        if (response.isSuccessful) {
                            try {
                                val json = JSONObject(responseBody)
                                val status = json.optString("status", "")
                                if (status == "success") {
                                    val dataDict = json.optJSONObject("data")
                                    if (dataDict != null) {
                                        projectDetail = ProjectDetail.fromJson(dataDict)
                                        // Update projectName from detail if available
                                        val detailProjectName = dataDict.optString("projectName", "")
                                        if (detailProjectName.isNotEmpty()) {
                                            projectName = detailProjectName
                                            runOnUiThread {
                                                tvProjectName.text = projectName
                                            }
                                        }
                                        // Display existing remarks
                                        runOnUiThread {
                                            val existingRemarks = projectDetail?.remarks ?: ""
                                            if (existingRemarks.isNotEmpty()) {
                                                etNote.setText(existingRemarks)
                                            }
                                        }
                                        displayJobs()
                                    }
                                } else {
                                    val message = json.optString("message", "Failed to load project detail")
                                    Toast.makeText(this@UpdateProjectActivity, message, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("UpdateProjectActivity", "Error parsing data", e)
                                Toast.makeText(this@UpdateProjectActivity, "Failed to parse data", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@UpdateProjectActivity, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showLoading(false)
                        android.util.Log.e("UpdateProjectActivity", "Error reading response", e)
                        Toast.makeText(this@UpdateProjectActivity, "Error reading response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun displayJobs() {
        llJobs.removeAllViews()
        val jobs = projectDetail?.jobs ?: return
        
        jobs.forEach { job ->
            val jobView = createJobView(job)
            llJobs.addView(jobView)
        }
    }

    private fun createJobView(job: ProjectJob): View {
        val jobView = layoutInflater.inflate(R.layout.item_job_update, llJobs, false)
        
        val tvJobTitle = jobView.findViewById<TextView>(R.id.tv_job_title)
        val llPhotosList = jobView.findViewById<LinearLayout>(R.id.ll_photos_list)
        
        // Set job title
        tvJobTitle.text = "Job ${job.jobIndex + 1}: ${job.jobDescription}"
        
        // Separate photos by type
        val beforePhotos = job.photos.filter { it.photoType.lowercase() == "before" }
        
        // Display photos in vertical pairs: Photo X -- photoDone X -- upload
        llPhotosList.removeAllViews()
        if (beforePhotos.isEmpty()) {
            val emptyView = TextView(this)
            emptyView.text = "No photos"
            emptyView.textSize = 12f
            emptyView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            emptyView.gravity = android.view.Gravity.CENTER
            emptyView.setPadding(16, 16, 16, 16)
            llPhotosList.addView(emptyView)
        } else {
            beforePhotos.forEachIndexed { index, beforePhoto ->
                val photoPairView = createPhotoPairView(beforePhoto, job.jobIndex, index + 1)
                llPhotosList.addView(photoPairView)
            }
        }
        
        return jobView
    }
    
    private fun createPhotoPairView(beforePhoto: ProjectPhoto, jobIndex: Int, photoIndex: Int): View {
        val pairView = layoutInflater.inflate(R.layout.item_photo_pair, null)
        
        val flBeforePhoto = pairView.findViewById<FrameLayout>(R.id.fl_before_photo)
        val flAfterPhoto = pairView.findViewById<FrameLayout>(R.id.fl_after_photo)
        val btnUpload = pairView.findViewById<ImageButton>(R.id.btn_upload)
        
        // Display before photo
        val beforePhotoView = createBeforePhotoView(beforePhoto, jobIndex, photoIndex - 1)
        flBeforePhoto.removeAllViews()
        flBeforePhoto.addView(beforePhotoView)
        
        // Display after photo (photoDone) or placeholder
        flAfterPhoto.removeAllViews()
        if (!beforePhoto.photoDone.isNullOrEmpty()) {
            // Display existing photoDone
            val photoDoneView = createPhotoDoneView(beforePhoto.photoDone, beforePhoto.photoId)
            flAfterPhoto.addView(photoDoneView)
            // Keep upload button visible so user can replace photoDone
            btnUpload.visibility = View.VISIBLE
        } else {
            // Check if there's a new photo for this before photo
            val newPhoto = newPhotosByJob[jobIndex]?.find { it.first == beforePhoto.photoId }
            if (newPhoto != null) {
                val newPhotoView = createPhotoView(newPhoto.second, true, jobIndex, beforePhoto.photoId)
                flAfterPhoto.addView(newPhotoView)
                // Keep upload button visible so user can change photo
                btnUpload.visibility = View.VISIBLE
            } else {
                // Empty placeholder
                val emptyView = TextView(this)
                emptyView.text = "-"
                emptyView.textSize = 12f
                emptyView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                emptyView.gravity = android.view.Gravity.CENTER
                emptyView.minWidth = 100.dpToPx()
                emptyView.minHeight = 100.dpToPx()
                emptyView.background = ContextCompat.getDrawable(this, R.drawable.rounded_white_background)
                flAfterPhoto.addView(emptyView)
                btnUpload.visibility = View.VISIBLE
            }
        }
        
        // Setup upload button - always visible for photoDone upload/replace
        btnUpload.setOnClickListener {
            selectedJobIndexForPhoto = jobIndex
            selectedBeforePhotoId = beforePhoto.photoId
            showImageSourceDialog()
        }
        
        return pairView
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createBeforePhotoView(photo: ProjectPhoto, jobIndex: Int, index: Int): View {
        val photoView = layoutInflater.inflate(R.layout.item_photo_small, null)
        val ivPhoto = photoView.findViewById<ImageView>(R.id.iv_photo)
        val btnRemove = photoView.findViewById<ImageButton>(R.id.btn_remove)
        
        val cleanPhotoName = photo.photo.split("/").lastOrNull() ?: photo.photo
        val imageUrl = "https://emshotels.net/photo/project/$cleanPhotoName"
        
        Glide.with(this)
            .load(imageUrl)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(ivPhoto)
        
        btnRemove.visibility = View.GONE
        
        // Make clickable to view full image
        photoView.setOnClickListener {
            val dialog = ImageViewerDialog(this, imageUrl)
            dialog.show()
        }
        
        return photoView
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createPhotoDoneView(photoDone: String, beforePhotoId: String): View {
        val photoView = layoutInflater.inflate(R.layout.item_photo_small, null)
        val ivPhoto = photoView.findViewById<ImageView>(R.id.iv_photo)
        val btnRemove = photoView.findViewById<ImageButton>(R.id.btn_remove)
        
        val cleanPhotoName = photoDone.split("/").lastOrNull() ?: photoDone
        val imageUrl = "https://emshotels.net/photo/project/$cleanPhotoName"
        
        Glide.with(this)
            .load(imageUrl)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(ivPhoto)
        
        btnRemove.visibility = View.GONE
        
        // Make clickable to view full image
        photoView.setOnClickListener {
            val dialog = ImageViewerDialog(this, imageUrl)
            dialog.show()
        }
        
        return photoView
    }

    private fun createPhotoView(photo: ProjectPhoto, isClickable: Boolean): View {
        val photoView = layoutInflater.inflate(R.layout.item_photo_small, null)
        val ivPhoto = photoView.findViewById<ImageView>(R.id.iv_photo)
        val btnRemove = photoView.findViewById<ImageButton>(R.id.btn_remove)
        
        val cleanPhotoName = photo.photo.split("/").lastOrNull() ?: photo.photo
        val imageUrl = "https://emshotels.net/photo/project/$cleanPhotoName"
        
        Glide.with(this)
            .load(imageUrl)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(ivPhoto)
        
        btnRemove.visibility = View.GONE
        
        if (isClickable) {
            photoView.setOnClickListener {
                val dialog = ImageViewerDialog(this, imageUrl)
                dialog.show()
            }
        }
        
        return photoView
    }

    private fun createPhotoView(uri: Uri, isNew: Boolean, jobIndex: Int, beforePhotoId: String): View {
        val photoView = layoutInflater.inflate(R.layout.item_photo_small, null)
        val ivPhoto = photoView.findViewById<ImageView>(R.id.iv_photo)
        val btnRemove = photoView.findViewById<ImageButton>(R.id.btn_remove)
        
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(ivPhoto)
        
        if (isNew) {
            btnRemove.visibility = View.VISIBLE
            btnRemove.setOnClickListener {
                newPhotosByJob[jobIndex]?.removeAll { it.first == beforePhotoId }
                if (newPhotosByJob[jobIndex]?.isEmpty() == true) {
                    newPhotosByJob.remove(jobIndex)
                }
                displayJobs() // Refresh display
            }
            // Make clickable to view full image
            photoView.setOnClickListener {
                // For local URI, we need to pass it as string to ImageViewerDialog
                // Glide in ImageViewerDialog can handle file:// URIs
                val dialog = ImageViewerDialog(this, uri.toString())
                dialog.show()
            }
        } else {
            btnRemove.visibility = View.GONE
        }
        
        return photoView
    }
    
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
    
    private fun showSelectBeforePhotoDialog(beforePhotos: List<ProjectPhoto>, jobIndex: Int) {
        val photoOptions = beforePhotos.mapIndexed { index, _ ->
            "Photo ${index + 1}"
        }.toTypedArray()
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Pilih Photo Before")
        builder.setItems(photoOptions) { _, which ->
            selectedJobIndexForPhoto = jobIndex
            selectedBeforePhotoId = beforePhotos[which].photoId
            showImageSourceDialog()
        }
        builder.show()
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Photo Source")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        
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
                val resizedUri = FileProvider.getUriForFile(
                    this@UpdateProjectActivity,
                    "${packageName}.fileprovider",
                    resizedFile
                )
                addPhotoToJob(selectedJobIndexForPhoto, resizedUri)
            } catch (e: Exception) {
                Toast.makeText(this@UpdateProjectActivity, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun addPhotoToJob(jobIndex: Int, uri: Uri) {
        if (selectedBeforePhotoId.isEmpty()) {
            Toast.makeText(this, "Silakan pilih photo before terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!newPhotosByJob.containsKey(jobIndex)) {
            newPhotosByJob[jobIndex] = mutableListOf()
        }
        // Remove existing photo for this beforePhotoId if any
        newPhotosByJob[jobIndex]?.removeAll { it.first == selectedBeforePhotoId }
        // Add new photo
        newPhotosByJob[jobIndex]?.add(Pair(selectedBeforePhotoId, uri))
        selectedBeforePhotoId = "" // Reset
        displayJobs() // Refresh display
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

    private fun saveUpdate() {
        val status = spStatus.selectedItem.toString()
        val note = etNote.text.toString().trim()
        
        // Check if there are any changes
        val hasStatusChange = status != currentStatus
        val hasNote = note.isNotEmpty()
        val hasNewPhotos = newPhotosByJob.isNotEmpty()
        
        if (!hasStatusChange && !hasNote && !hasNewPhotos) {
            Toast.makeText(this, "Tidak ada perubahan untuk disimpan", Toast.LENGTH_SHORT).show()
            return
        }
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                // Check internet connection
                val hasInternet = withContext(Dispatchers.IO) {
                    NetworkUtils.hasServerConnection()
                }
                
                if (!hasInternet) {
                    // No internet: Save to offline queue
                    saveProjectUpdateOffline(status, note)
                    return@launch
                }
                
                // Has internet: Try to update online
                uploadUpdate(status, note)
            } catch (e: Exception) {
                android.util.Log.e("UpdateProjectActivity", "Error uploading update", e)
                // Network error: Save to offline queue
                saveProjectUpdateOffline(status, note, e.message)
            }
        }
    }

    private suspend fun uploadUpdate(status: String, note: String) {
        // Debug: Log note value
        android.util.Log.d("UpdateProjectActivity", "Uploading note: '$note' (length: ${note.length})")
        
        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "update")
            .addFormDataPart("projectId", projectId)
            .addFormDataPart("propID", propID)
            .addFormDataPart("status", status)
        
        // Always send note parameter (even if empty, backend will check)
        // Parameter name is "note" which will be saved to "remarks" field in database
        requestBodyBuilder.addFormDataPart("note", note)
        
        // Add photos with photoId (before photo ID) and job_index
        // Format: photos[], photo_id[], job_index[]
        val photoList = mutableListOf<Triple<Int, String, Uri>>() // jobIndex, beforePhotoId, uri
        newPhotosByJob.forEach { (jobIndex, photoPairs) ->
            photoPairs.forEach { (beforePhotoId, uri) ->
                photoList.add(Triple(jobIndex, beforePhotoId, uri))
            }
        }
        
        photoList.forEachIndexed { index, (jobIndex, beforePhotoId, uri) ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = File(cacheDir, "upload_photo_${System.currentTimeMillis()}_${jobIndex}_$index.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    
                    if (file.exists()) {
                        val requestFile = file.asRequestBody("image/jpeg".toMediaType())
                        // Format filename untuk photoDone: Done_{projectId}_{timestamp}_{index}.jpg
                        val timestamp = System.currentTimeMillis()
                        val filename = "Done_${projectId}_${timestamp}_$index.jpg"
                        requestBodyBuilder.addFormDataPart("photos[]", filename, requestFile)
                        requestBodyBuilder.addFormDataPart("photo_id[]", beforePhotoId)
                        requestBodyBuilder.addFormDataPart("job_index[]", jobIndex.toString())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateProjectActivity", "Error adding photo", e)
            }
        }
        
        val requestBody = requestBodyBuilder.build()

        val request = Request.Builder()
            .url("https://emshotels.net/apiKu/update_project.php")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    // Network error: Save to offline queue
                    lifecycleScope.launch {
                        saveProjectUpdateOffline(status, note, e.message)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val statusResponse = json.optString("status", "")
                    
                    runOnUiThread {
                        showLoading(false)
                        if (statusResponse == "success") {
                            Toast.makeText(this@UpdateProjectActivity, "Project updated successfully", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            // Update failed: Save to offline queue
                            val message = json.optString("message", "Failed to update project")
                            lifecycleScope.launch {
                                saveProjectUpdateOffline(status, note, message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showLoading(false)
                        android.util.Log.e("UpdateProjectActivity", "Error parsing response", e)
                        // Parse error: Save to offline queue
                        lifecycleScope.launch {
                            saveProjectUpdateOffline(status, note, e.message)
                        }
                    }
                }
            }
        })
    }
    
    private suspend fun saveProjectUpdateOffline(
        status: String,
        note: String,
        errorMsg: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // Copy photos to persistent location
            val offlineDir = File(getExternalFilesDir(null), "offline_photos")
            if (!offlineDir.exists()) {
                offlineDir.mkdirs()
            }
            
            val photoPaths = mutableListOf<String>()
            val jobIndices = mutableListOf<Int>()
            val beforePhotoIds = mutableListOf<String>()
            
            newPhotosByJob.forEach { (jobIndex, photoPairs) ->
                photoPairs.forEach { (beforePhotoId, uri) ->
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val persistentFile = File(offlineDir, "PROJECT_UPDATE_${projectId}_${System.currentTimeMillis()}_${photoPaths.size}.jpg")
                            val outputStream = java.io.FileOutputStream(persistentFile)
                            inputStream.copyTo(outputStream)
                            inputStream.close()
                            outputStream.close()
                            
                            if (persistentFile.exists()) {
                                photoPaths.add(persistentFile.absolutePath)
                                jobIndices.add(jobIndex)
                                beforePhotoIds.add(beforePhotoId)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UpdateProjectActivity", "Error copying photo: ${e.message}", e)
                    }
                }
            }
            
            val photoPathsJson = if (photoPaths.isNotEmpty()) JSONArray(photoPaths).toString() else null
            val jobIndexJson = if (jobIndices.isNotEmpty()) JSONArray(jobIndices).toString() else null
            val beforePhotoIdsJson = if (beforePhotoIds.isNotEmpty()) JSONArray(beforePhotoIds).toString() else null
            
            // Create pending project update
            val pendingProject = PendingProject(
                requestType = "update",
                projectId = projectId,
                propID = propID,
                newStatus = status,
                note = note,
                photoPathsJson = photoPathsJson,
                jobIndexJson = jobIndexJson,
                beforePhotoIdsJson = beforePhotoIdsJson,
                lastError = errorMsg ?: "No internet connection"
            )
            
            // Save to database
            OfflineProjectService.addPendingProject(pendingProject)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@UpdateProjectActivity,
                    "Project update saved offline. Will sync when internet is available.",
                    Toast.LENGTH_LONG
                ).show()
                
                setResult(RESULT_OK)
                finish()
            }
            
            // Schedule sync when internet is available
            SyncService.scheduleSync(this@UpdateProjectActivity)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@UpdateProjectActivity,
                    "Failed to save offline: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        scrollView.visibility = if (show) View.GONE else View.VISIBLE
        btnSave.isEnabled = !show
    }

}

