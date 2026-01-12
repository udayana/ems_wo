package com.sofindo.ems.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.BitmapFactory
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.services.OfflineMaintenanceService
import com.sofindo.ems.services.SyncService
import com.sofindo.ems.database.PendingMaintenanceTask
import com.sofindo.ems.utils.NetworkUtils
import com.sofindo.ems.adapters.MaintenanceJobTaskAdapter
import com.sofindo.ems.utils.resizeCrop
import org.json.JSONArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceJobTaskFragment : Fragment() {
    
    private lateinit var tvProgressStatus: TextView
    private lateinit var ivProgressIcon: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var etNote: EditText
    private lateinit var btnSubmit: Button

    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: View
    private lateinit var layoutError: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    
    // Photo upload views
    private lateinit var btnAddPhoto: Button
    private lateinit var layoutPhoto1: LinearLayout
    private lateinit var layoutPhoto2: LinearLayout
    private lateinit var layoutPhoto3: LinearLayout
    private lateinit var ivPhoto1: ImageView
    private lateinit var ivPhoto2: ImageView
    private lateinit var ivPhoto3: ImageView
    private lateinit var btnRemovePhoto1: ImageButton
    private lateinit var btnRemovePhoto2: ImageButton
    private lateinit var btnRemovePhoto3: ImageButton
    
    private var assetNo: String = ""
    private var mntId: String = ""
    private var propertyName: String = ""
    private var propID: String = ""
    private var scheduleDate: String = ""
    private var scheduleStartDate: String = "" // Start date from schedule to filter tasks
    private var eventId: String = "" // Event ID from schedule to filter tasks by specific event
    
    private var maintenanceAdapter: MaintenanceJobTaskAdapter? = null
    private var tasks: List<Map<String, Any>> = emptyList()
    private var isSubmitting = false
    
    // Photo management
    private val selectedPhotos = mutableListOf<File?>()
    private var cameraPhotoFile: File? = null
    private var cameraPhotoUri: Uri? = null
    
    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoFile?.let { file ->
                handleCameraImage(file)
            }
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleGalleryImage(it) }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance_job_task, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        arguments?.let { args ->
            assetNo = args.getString("assetNo", "")
            mntId = args.getString("mntId", "")
            propertyName = args.getString("propertyName", "")
            propID = args.getString("propID", "")
            scheduleDate = args.getString("scheduleDate", "")
            scheduleStartDate = args.getString("scheduleStartDate", "")
            eventId = args.getString("eventId", "")
            
            // Arguments loaded
        }
        
        initViews(view)
        setupToolbar(view)
        loadMaintenanceTasks()
    }
    
    private fun initViews(view: View) {
        tvProgressStatus = view.findViewById(R.id.tv_progress_status)
        ivProgressIcon = view.findViewById(R.id.iv_progress_icon)
        recyclerView = view.findViewById(R.id.recycler_view)
        etNote = view.findViewById(R.id.et_note)
        btnSubmit = view.findViewById(R.id.btn_submit)

        progressBar = view.findViewById(R.id.progress_bar)
        layoutContent = view.findViewById(R.id.layout_content)
        layoutError = view.findViewById(R.id.layout_error)
        tvErrorMessage = view.findViewById(R.id.tv_error_message)
        btnRetry = view.findViewById(R.id.btn_retry)
        
        // Initialize photo views
        btnAddPhoto = view.findViewById(R.id.btn_add_photo)
        layoutPhoto1 = view.findViewById(R.id.layout_photo_1)
        layoutPhoto2 = view.findViewById(R.id.layout_photo_2)
        layoutPhoto3 = view.findViewById(R.id.layout_photo_3)
        ivPhoto1 = view.findViewById(R.id.iv_photo_1)
        ivPhoto2 = view.findViewById(R.id.iv_photo_2)
        ivPhoto3 = view.findViewById(R.id.iv_photo_3)
        btnRemovePhoto1 = view.findViewById(R.id.btn_remove_photo_1)
        btnRemovePhoto2 = view.findViewById(R.id.btn_remove_photo_2)
        btnRemovePhoto3 = view.findViewById(R.id.btn_remove_photo_3)
        
        // Initialize selected photos list (max 3)
        selectedPhotos.addAll(listOf(null, null, null))
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Setup update notes button
        btnSubmit.setOnClickListener {
            updateMaintenanceNotes()
        }
        
        // Setup retry button
        btnRetry.setOnClickListener {
            loadMaintenanceTasks()
        }
        
        // Setup photo upload button
        btnAddPhoto.setOnClickListener {
            if (selectedPhotos.count { it != null } < 3) {
                showImageSourceDialog()
            } else {
                Toast.makeText(context, "Maximum 3 photos allowed", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup remove photo buttons
        btnRemovePhoto1.setOnClickListener { removePhoto(0) }
        btnRemovePhoto2.setOnClickListener { removePhoto(1) }
        btnRemovePhoto3.setOnClickListener { removePhoto(2) }
    }
    
    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Set navigation icon color to primary (green)
        toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, null))
        
        // Format date to short format (e.g., "15 Dec 2025")
        val formattedDate = if (scheduleDate.isNotEmpty()) {
            try {
                // Try to parse the date and format it to short format
                val inputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                val outputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                val date = inputFormat.parse(scheduleDate)
                if (date != null) {
                    outputFormat.format(date)
                } else {
                    // Try full month name format
                    try {
                        val inputFormat2 = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
                        val date2 = inputFormat2.parse(scheduleDate)
                        if (date2 != null) {
                            outputFormat.format(date2)
                        } else {
                            scheduleDate
                        }
                    } catch (e2: Exception) {
                        // Try format from backend (e.g., "2025-12-15")
                        try {
                            val inputFormat3 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val date3 = inputFormat3.parse(scheduleDate)
                            if (date3 != null) {
                                outputFormat.format(date3)
                            } else {
                                scheduleDate
                            }
                        } catch (e3: Exception) {
                            scheduleDate
                        }
                    }
                }
            } catch (e: Exception) {
                scheduleDate
            }
        } else {
            ""
        }
        
        // Set title: property name and date in one line (remove "Tasks" since it's already below)
        toolbar.title = if (formattedDate.isNotEmpty()) {
            "$propertyName • $formattedDate"
        } else {
            propertyName
        }
        
        // Clear subtitle
        toolbar.subtitle = ""
    }
    
    private fun loadMaintenanceTasks() {
        showLoading(true)
        showError(false)
        
        lifecycleScope.launch {
            try {
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                // eventId is required - must be provided from button click
                if (eventId.isEmpty()) {
                    throw Exception("Event ID tidak ditemukan. Silakan pilih schedule terlebih dahulu.")
                }
                
                val response = MaintenanceService.getMaintenanceTaskJob(
                    context = requireContext(),
                    noAssets = assetNo,
                    propID = currentPropID,
                    eventId = eventId
                )
                
                if (response["data"] != null && response["data"] is List<*>) {
                    val maintenanceDataList = response["data"] as List<*>
                    
                    val processedTasks = mutableListOf<Map<String, Any>>()
                    
                    // Process all maintenance tasks from the API
                    for (recordIndex in maintenanceDataList.indices) {
                        val maintenanceData = maintenanceDataList[recordIndex] as? Map<String, Any>
                        val jobtask = maintenanceData?.get("jobtask")?.toString() ?: ""
                        
                        // Process maintenance data from API
                        
                        // Parse jobtask text into individual tasks
                        if (jobtask.isNotEmpty()) {
                            var taskText = jobtask.trim()
                            
                            // Remove common numbering patterns and extra punctuation
                            taskText = taskText.replace(Regex("^\\d+[\\.\\)]\\s*"), "")
                            taskText = taskText.replace(Regex("^[-•*]\\s*"), "")
                            taskText = taskText.trim()
                            
                            if (taskText.isNotEmpty() && taskText.length > 3) {
                                val taskNo = maintenanceData?.get("no")?.toString() ?: ""
                                val mntId = maintenanceData?.get("mntId")?.toString() ?: ""
                                val mntUniq = maintenanceData?.get("mntUniq")?.toString() ?: ""
                                val id = maintenanceData?.get("id")?.toString() ?: ""
                                
                                // Process task data
                                
                                // Process maintenance data for task
                                
                                processedTasks.add(
                                    mapOf(
                                        "id" to (recordIndex + 1).toString(),
                                        "title" to taskText,
                                        "completed" to (maintenanceData?.get("done") == 1 || maintenanceData?.get("done") == "1"),
                                        "taskNo" to taskNo,
                                        "mntId" to mntId,
                                        "mntUniq" to mntUniq,
                                        "eventId" to id // Store the actual event ID
                                    )
                                )
                            }
                        }
                    }
                    
                    tasks = processedTasks
                    setupRecyclerView()
                    showLoading(false)
                    
                    // Load existing notes after data is loaded (same as Flutter)
                    loadExistingNotes()
                } else {
                    throw Exception("Tidak ada data maintenance task yang ditemukan")
                }
                
            } catch (e: Exception) {
                showError(true, e.message ?: "Terjadi kesalahan saat memuat data")
            }
        }
    }
    
    private fun setupRecyclerView() {
        maintenanceAdapter = MaintenanceJobTaskAdapter(tasks) { taskId, isCompleted ->
            // Handle task completion toggle with proper API calls
            updateTaskStatus(taskId, isCompleted)
        }
        
        recyclerView.adapter = maintenanceAdapter
        updateProgressStatus()
    }
    
    private fun updateTaskStatus(taskId: String, isCompleted: Boolean) {
        lifecycleScope.launch {
            try {
                // Find the task
                val taskIndex = tasks.indexOfFirst { it["id"] == taskId }
                if (taskIndex == -1) return@launch
                
                val task = tasks[taskIndex]
                
                // Get user info
                val user = UserService.getCurrentUser()
                val userName = user?.fullName ?: user?.username ?: "Unknown User"
                
                // Get propID
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                if (currentPropID.isEmpty()) {
                    Toast.makeText(context, "Property ID not found. Please login again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Check internet connection
                val hasInternet = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    NetworkUtils.hasServerConnection()
                }
                
                // Update local state first (optimistic update)
                val updatedTasks = tasks.toMutableList()
                val taskNo = task["taskNo"]?.toString() ?: ""
                val newDoneBy = if (isCompleted) userName else ""
                
                updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                    put("completed", isCompleted)
                    put("doneBy", newDoneBy)
                }
                tasks = updatedTasks
                updateProgressStatus()
                maintenanceAdapter?.updateTasks(tasks)
                
                if (!hasInternet) {
                    // No internet: Save to offline queue
                    saveTaskStatusOffline(taskNo, taskId, isCompleted, newDoneBy, currentPropID)
                    
                    // Step 2: If task is completed, check if all tasks are done (for offline too)
                    if (isCompleted) {
                        val allCompleted = tasks.all { it["completed"] == true }
                        if (allCompleted) {
                            Toast.makeText(context, "All tasks completed offline. Will sync when internet is available.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Task saved offline. Will sync when internet is available.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Task saved offline. Will sync when internet is available.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Has internet: Try to update online
                val taskIdToUse = taskNo
                
                try {
                    MaintenanceService.updateMaintenanceTaskStatus(
                        taskId = taskIdToUse,
                        isDone = isCompleted,
                        doneBy = newDoneBy
                    )
                    
                    // Step 2: If task is completed, re-fetch data from server to verify all tasks are done
                    if (isCompleted) {
                        // Re-fetch tasks from server to get accurate completion status
                        try {
                            val refreshedResponse = MaintenanceService.getMaintenanceTaskJob(
                                context = requireContext(),
                                noAssets = assetNo,
                                propID = currentPropID,
                                eventId = eventId
                            )
                            
                            if (refreshedResponse["data"] != null && refreshedResponse["data"] is List<*>) {
                                val refreshedDataList = refreshedResponse["data"] as List<*>
                                
                                // Check if ALL tasks from server are completed
                                val allTasksFromServer = refreshedDataList.mapNotNull { data ->
                                    val taskData = data as? Map<String, Any>
                                    taskData?.get("done")?.toString() == "1" || taskData?.get("done") == 1
                                }
                                
                                val allCompleted = allTasksFromServer.isNotEmpty() && allTasksFromServer.all { it == true }
                                
                                if (allCompleted && allTasksFromServer.size == refreshedDataList.size) {
                                    // All tasks are truly completed - update event status to "done"
                                    try {
                                        val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                                        val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                                        val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                                        
                                        MaintenanceService.updateMaintenanceEvent(
                                            mntId = eventIdToUse,
                                            status = "done",
                                            notes = ""
                                        )
                                        
                                        Toast.makeText(context, "All tasks completed! Maintenance event marked as done.", Toast.LENGTH_LONG).show()
                                    } catch (eventError: Exception) {
                                        Toast.makeText(context, "Task updated but event update failed: ${eventError.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Task marked as completed (${allTasksFromServer.count { it }}/${refreshedDataList.size} tasks done)", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Task marked as completed", Toast.LENGTH_SHORT).show()
                            }
                        } catch (refreshError: Exception) {
                            // If refresh fails, just show success message without auto-completing
                            Toast.makeText(context, "Task marked as completed", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Task marked as incomplete", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // API call failed: Save to offline queue
                    saveTaskStatusOffline(taskNo, taskId, isCompleted, newDoneBy, currentPropID, e.message)
                }
                
            } catch (e: Exception) {
                val errorMsg = "Error updating task: ${e.message}"
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                
                // Revert the UI state if error
                val updatedTasks = tasks.toMutableList()
                val taskIndex = updatedTasks.indexOfFirst { it["id"] == taskId }
                if (taskIndex != -1) {
                    updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                        put("completed", !isCompleted)
                    }
                    tasks = updatedTasks
                    updateProgressStatus()
                }
            }
        }
    }
    
    private suspend fun saveTaskStatusOffline(
        taskNo: String,
        taskId: String,
        isCompleted: Boolean,
        doneBy: String,
        currentPropID: String,
        errorMsg: String? = null
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val pendingTask = PendingMaintenanceTask(
                requestType = "update_task_status",
                taskId = taskId,
                taskNo = taskNo,
                isDone = isCompleted,
                doneBy = doneBy,
                assetNo = assetNo,
                propID = currentPropID,
                lastError = errorMsg ?: "No internet connection"
            )
            
            OfflineMaintenanceService.addPendingMaintenanceTask(pendingTask)
            
            // Schedule sync when internet is available
            SyncService.scheduleSync(requireContext())
        } catch (e: Exception) {
            android.util.Log.e("MaintenanceJobTaskFragment", "Error saving task status offline: ${e.message}", e)
        }
    }
    
    private fun updateProgressStatus() {
        val completedCount = tasks.count { it["completed"] == true }
        val totalCount = tasks.size
        
        tvProgressStatus.text = "Progress: $completedCount/$totalCount tasks completed"
        btnSubmit.text = "Update Notes"
        
        // Update progress icon based on completion
        if (completedCount == totalCount && totalCount > 0) {
            ivProgressIcon.setImageResource(R.drawable.ic_status_done)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.status_done, null))
        } else {
            ivProgressIcon.setImageResource(R.drawable.ic_task)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.primary, null))
        }
        
        // Update progress status text color to green
        tvProgressStatus.setTextColor(resources.getColor(R.color.primary, null))
    }
    
    private fun showImageSourceDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Photo Source")
            .setItems(arrayOf("Camera", "Photo Gallery")) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }
    
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        
        try {
            cameraPhotoFile = createImageFile()
            cameraPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                cameraPhotoFile!!
            )
            cameraLauncher.launch(cameraPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        // GetContent() expects MIME type as String, not Intent
        galleryLauncher.launch("image/*")
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    private fun handleCameraImage(file: File) {
        lifecycleScope.launch {
            try {
                // Resize + crop ke 480x480 sebelum upload
                val resizedFile = withContext(Dispatchers.IO) {
                    resizeCrop(file, size = 480, quality = 90)
                }
                addPhotoToSlot(resizedFile)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleGalleryImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                val tempFile = createImageFile()
                val outputStream = FileOutputStream(tempFile)
                
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.close()
                
                // Resize + crop ke 480x480 sebelum upload
                val resizedFile = withContext(Dispatchers.IO) {
                    resizeCrop(tempFile, size = 480, quality = 90)
                }
                addPhotoToSlot(resizedFile)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun addPhotoToSlot(file: File) {
        val emptySlotIndex = selectedPhotos.indexOfFirst { it == null }
        if (emptySlotIndex != -1) {
            selectedPhotos[emptySlotIndex] = file
            updatePhotoUI()
        } else {
            Toast.makeText(context, "Maximum 3 photos allowed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun removePhoto(index: Int) {
        if (index < selectedPhotos.size && selectedPhotos[index] != null) {
            selectedPhotos[index] = null
            updatePhotoUI()
        }
    }
    
    private fun updatePhotoUI() {
        // Update photo 1
        if (selectedPhotos[0] != null) {
            layoutPhoto1.visibility = View.VISIBLE
            Glide.with(this)
                .load(selectedPhotos[0])
                .placeholder(R.drawable.photo_preview_background)
                .error(R.drawable.photo_preview_background)
                .centerCrop()
                .into(ivPhoto1)
        } else {
            layoutPhoto1.visibility = View.GONE
        }
        
        // Update photo 2
        if (selectedPhotos[1] != null) {
            layoutPhoto2.visibility = View.VISIBLE
            Glide.with(this)
                .load(selectedPhotos[1])
                .placeholder(R.drawable.photo_preview_background)
                .error(R.drawable.photo_preview_background)
                .centerCrop()
                .into(ivPhoto2)
        } else {
            layoutPhoto2.visibility = View.GONE
        }
        
        // Update photo 3
        if (selectedPhotos[2] != null) {
            layoutPhoto3.visibility = View.VISIBLE
            Glide.with(this)
                .load(selectedPhotos[2])
                .placeholder(R.drawable.photo_preview_background)
                .error(R.drawable.photo_preview_background)
                .centerCrop()
                .into(ivPhoto3)
        } else {
            layoutPhoto3.visibility = View.GONE
        }
        
        // Update add photo button
        val photoCount = selectedPhotos.count { it != null }
        btnAddPhoto.text = if (photoCount < 3) "📷 Add Photo (${photoCount}/3)" else "📷 Maximum 3 photos"
        btnAddPhoto.isEnabled = photoCount < 3
    }
    
    private fun updateMaintenanceNotes() {
        if (isSubmitting) return
        
        val note = etNote.text.toString().trim()
        if (note.isEmpty()) {
            Toast.makeText(context, "Please enter notes before updating", Toast.LENGTH_SHORT).show()
            return
        }
        
        isSubmitting = true
        btnSubmit.isEnabled = false
        btnSubmit.text = "Updating Notes..."
        
        lifecycleScope.launch {
            try {
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                // Get the actual event ID from the first task
                val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                
                if (eventIdToUse.isEmpty()) {
                    throw Exception("Event ID not found")
                }
                
                // Get photos that are not null
                val photosToUpload = selectedPhotos.filterNotNull()
                
                // Check internet connection
                val hasInternet = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    NetworkUtils.hasServerConnection()
                }
                
                if (!hasInternet) {
                    // No internet: Save to offline queue
                    saveNotesPhotosOffline(eventIdToUse, note, photosToUpload, currentPropID)
                    return@launch
                }
                
                // Has internet: Try to update online
                try {
                    MaintenanceService.updateMaintenanceEvent(
                        mntId = eventIdToUse,
                        status = "done",
                        notes = note,
                        photos = photosToUpload
                    )
                    
                    if (isAdded) {
                        val photoCount = photosToUpload.size
                        val message = if (photoCount > 0) {
                            "Notes and $photoCount photo(s) updated successfully!"
                        } else {
                            "Notes updated successfully!"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                } catch (eventError: Exception) {
                    // Update failed: Save to offline queue
                    saveNotesPhotosOffline(eventIdToUse, note, photosToUpload, currentPropID, eventError.message)
                }
                
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(context, "Error submitting maintenance: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSubmitting = false
                btnSubmit.isEnabled = true
                btnSubmit.text = "Update Notes"
            }
        }
    }
    
    private suspend fun saveNotesPhotosOffline(
        eventId: String,
        note: String,
        photos: List<File>,
        currentPropID: String,
        errorMsg: String? = null
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Copy photos to persistent location
            val offlineDir = File(requireContext().getExternalFilesDir(null), "offline_photos")
            if (!offlineDir.exists()) {
                offlineDir.mkdirs()
            }
            
            val photoPaths = mutableListOf<String>()
            photos.forEach { photoFile ->
                if (photoFile.exists()) {
                    val persistentFile = File(offlineDir, "MAINTENANCE_${eventId}_${System.currentTimeMillis()}_${photoPaths.size}.jpg")
                    photoFile.copyTo(persistentFile, overwrite = true)
                    photoPaths.add(persistentFile.absolutePath)
                }
            }
            
            val photoPathsJson = if (photoPaths.isNotEmpty()) JSONArray(photoPaths).toString() else null
            
            // Get mntId from tasks
            val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
            val mntIdToUse = if (taskMntId.isNotEmpty()) taskMntId else mntId
            
            val pendingTask = PendingMaintenanceTask(
                requestType = "update_notes_photos",
                mntId = mntIdToUse,
                eventId = eventId,
                notes = note,
                status = "done",
                photoPathsJson = photoPathsJson,
                assetNo = assetNo,
                propID = currentPropID,
                lastError = errorMsg ?: "No internet connection"
            )
            
            OfflineMaintenanceService.addPendingMaintenanceTask(pendingTask)
            
            withContext(Dispatchers.Main) {
                val photoCount = photos.size
                val message = if (photoCount > 0) {
                    "Notes and $photoCount photo(s) saved offline. Will sync when internet is available."
                } else {
                    "Notes saved offline. Will sync when internet is available."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                
                // Navigate back
                parentFragmentManager.popBackStack()
            }
            
            // Schedule sync when internet is available
            SyncService.scheduleSync(requireContext())
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save offline: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        layoutContent.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showError(show: Boolean, message: String = "") {
        layoutError.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            tvErrorMessage.text = message
        }
    }
    
    private fun loadExistingNotes() {
        lifecycleScope.launch {
            try {
                // Get the actual event ID from the first task
                val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                
                if (eventIdToUse.isNotEmpty()) {
                    val existingNotes = MaintenanceService.getMaintenanceNotes(
                        mntId = eventIdToUse,
                        propID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                    )
                    
                    // Set notes to EditText
                    etNote.setText(existingNotes)
                }
            } catch (e: Exception) {
                // Clear text field if error
                etNote.setText("")
            }
        }
    }
    
    // === Helper resize lokal: proporsional, sisi terpanjang = maxSide ===
    private fun resizeJpegInPlace(file: File, maxSide: Int = 420, quality: Int = 85): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return file

        val sample = calculateInSampleSize(srcW, srcH, maxSide)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return file

        val longest = kotlin.math.max(bmp.width, bmp.height)
        val scale = longest.toFloat() / maxSide
        val finalBmp = if (scale > 1f) {
            val w = (bmp.width / scale).toInt()
            val h = (bmp.height / scale).toInt()
            android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
        } else bmp

        FileOutputStream(file, false).use { out ->
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
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
    
    companion object {
        fun newInstance(
            assetNo: String,
            mntId: String,
            propertyName: String,
            propID: String,
            scheduleDate: String = "",
            scheduleStartDate: String = "",
            eventId: String = ""
        ): MaintenanceJobTaskFragment {
            return MaintenanceJobTaskFragment().apply {
                arguments = Bundle().apply {
                    putString("assetNo", assetNo)
                    putString("mntId", mntId)
                    putString("propertyName", propertyName)
                    putString("propID", propID)
                    putString("scheduleDate", scheduleDate)
                    putString("scheduleStartDate", scheduleStartDate)
                    putString("eventId", eventId)
                }
            }
        }
    }
}
