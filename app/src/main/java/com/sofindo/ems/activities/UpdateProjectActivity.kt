package com.sofindo.ems.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.dialogs.ImageViewerDialog
import com.sofindo.ems.models.ProjectDetail
import com.sofindo.ems.models.ProjectJob
import com.sofindo.ems.models.ProjectPhoto
import com.sofindo.ems.utils.resizeCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class UpdateProjectActivity : AppCompatActivity() {

    private lateinit var toolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var btnSave: Button
    
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
    
    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
        private const val REQUEST_CODE_CAMERA = 1002
        private const val PERMISSION_REQUEST_CODE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_project)

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
        
        spStatus = findViewById(R.id.sp_status)
        etNote = findViewById(R.id.et_note)
        llJobs = findViewById(R.id.ll_jobs)
        scrollView = findViewById(R.id.scroll_view)
        loadingContainer = findViewById(R.id.loading_container)
        progressBar = findViewById(R.id.progress_bar)
        
        toolbarTitle.text = "Update Project"
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
        val llBeforePhotos = jobView.findViewById<LinearLayout>(R.id.ll_before_photos)
        val tvArrow = jobView.findViewById<TextView>(R.id.tv_arrow)
        val llAfterPhotos = jobView.findViewById<LinearLayout>(R.id.ll_after_photos)
        val btnAddPhotoAfter = jobView.findViewById<Button>(R.id.btn_add_photo_after)
        
        // Set job title
        tvJobTitle.text = "Job ${job.jobIndex + 1}: ${job.jobDescription}"
        
        // Separate photos by type
        val beforePhotos = job.photos.filter { it.photoType.lowercase() == "before" }
        val afterPhotos = job.photos.filter { it.photoType.lowercase() == "after" }
        
        // Display before photos with click to add after photo
        llBeforePhotos.removeAllViews()
        if (beforePhotos.isEmpty()) {
            val emptyView = TextView(this)
            emptyView.text = "No photos"
            emptyView.textSize = 12f
            emptyView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            emptyView.gravity = android.view.Gravity.CENTER
            llBeforePhotos.addView(emptyView)
        } else {
            beforePhotos.forEachIndexed { index, photo ->
                val photoView = createBeforePhotoView(photo, job.jobIndex, index)
                llBeforePhotos.addView(photoView)
            }
        }
        
        // Display after photos (existing) - photoDone dari before photo
        llAfterPhotos.removeAllViews()
        beforePhotos.forEachIndexed { index, beforePhoto ->
            // Check if this before photo has photoDone
            if (!beforePhoto.photoDone.isNullOrEmpty()) {
                // Display existing photoDone
                val photoView = createPhotoDoneView(beforePhoto.photoDone, beforePhoto.photoId)
                llAfterPhotos.addView(photoView)
            } else {
                // Check if there's a new photo for this before photo
                val newPhoto = newPhotosByJob[job.jobIndex]?.find { it.first == beforePhoto.photoId }
                if (newPhoto != null) {
                    val photoView = createPhotoView(newPhoto.second, true, job.jobIndex, beforePhoto.photoId)
                    llAfterPhotos.addView(photoView)
                } else {
                    // Empty placeholder
                    val emptyView = TextView(this)
                    emptyView.text = "-"
                    emptyView.textSize = 12f
                    emptyView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    emptyView.gravity = android.view.Gravity.CENTER
                    emptyView.minWidth = 100.dpToPx()
                    emptyView.minHeight = 100.dpToPx()
                    llAfterPhotos.addView(emptyView)
                }
            }
        }
        
        // Setup add photo button - show dialog to select which before photo
        btnAddPhotoAfter.setOnClickListener {
            if (beforePhotos.isEmpty()) {
                Toast.makeText(this, "Tidak ada photo before untuk menambah photo after", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedJobIndexForPhoto = job.jobIndex
            showSelectBeforePhotoDialog(beforePhotos, job.jobIndex)
        }
        
        return jobView
    }

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
        
        // Make clickable to add after photo
        photoView.setOnClickListener {
            selectedJobIndexForPhoto = jobIndex
            selectedBeforePhotoId = photo.photoId
            showImageSourceDialog()
        }
        
        return photoView
    }

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
        val photoOptions = beforePhotos.mapIndexed { index, photo ->
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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
            return
        }
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_CAMERA)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && selectedJobIndexForPhoto >= 0) {
            when (requestCode) {
                REQUEST_CODE_PICK_IMAGE -> {
                    data?.data?.let { uri ->
                        addPhotoToJob(selectedJobIndexForPhoto, uri)
                    }
                }
                REQUEST_CODE_CAMERA -> {
                    data?.extras?.get("data")?.let { bitmap ->
                        val uri = saveBitmapToFile(bitmap as android.graphics.Bitmap)
                        uri?.let {
                            addPhotoToJob(selectedJobIndexForPhoto, it)
                        }
                    }
                }
            }
        }
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

    private fun saveBitmapToFile(bitmap: android.graphics.Bitmap): Uri? {
        return try {
            val file = File(cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val outputStream = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            android.util.Log.e("UpdateProjectActivity", "Error saving bitmap", e)
            null
        }
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
                uploadUpdate(status, note)
            } catch (e: Exception) {
                android.util.Log.e("UpdateProjectActivity", "Error uploading update", e)
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@UpdateProjectActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun uploadUpdate(status: String, note: String) {
        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "update")
            .addFormDataPart("projectId", projectId)
            .addFormDataPart("propID", propID)
            .addFormDataPart("status", status)
        
        if (note.isNotEmpty()) {
            requestBodyBuilder.addFormDataPart("note", note)
        }
        
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
                        // Resize + crop ke 480x480 sebelum upload
                        val processedFile = withContext(Dispatchers.IO) {
                            resizeCrop(file, size = 480, quality = 90)
                        }
                        val requestFile = processedFile.asRequestBody("image/jpeg".toMediaType())
                        requestBodyBuilder.addFormDataPart("photos[]", "photo_${jobIndex}_$index.jpg", requestFile)
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
                    Toast.makeText(this@UpdateProjectActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            val message = json.optString("message", "Failed to update project")
                            Toast.makeText(this@UpdateProjectActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showLoading(false)
                        android.util.Log.e("UpdateProjectActivity", "Error parsing response", e)
                        Toast.makeText(this@UpdateProjectActivity, "Error parsing response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        scrollView.visibility = if (show) View.GONE else View.VISIBLE
        btnSave.isEnabled = !show
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
