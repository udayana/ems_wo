package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.adapters.MaintenanceJobTaskAdapter
import kotlinx.coroutines.launch

class MaintenanceJobTaskFragment : Fragment() {
    
    private lateinit var tvJobTitle: TextView
    private lateinit var tvPeriod: TextView
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
    
    private var assetNo: String = ""
    private var mntId: String = ""
    private var propertyName: String = ""
    private var propID: String = ""
    
    private var maintenanceAdapter: MaintenanceJobTaskAdapter? = null
    private var tasks: List<Map<String, Any>> = emptyList()
    private var isSubmitting = false
    
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
            
            // Debug logging untuk arguments
            android.util.Log.d("MaintenanceJobTask", "=== ARGUMENTS DEBUG ===")
            android.util.Log.d("MaintenanceJobTask", "assetNo: $assetNo")
            android.util.Log.d("MaintenanceJobTask", "mntId: $mntId")
            android.util.Log.d("MaintenanceJobTask", "propertyName: $propertyName")
            android.util.Log.d("MaintenanceJobTask", "propID: $propID")
        }
        
        initViews(view)
        setupToolbar(view)
        loadMaintenanceTasks()
    }
    
    private fun initViews(view: View) {
        tvJobTitle = view.findViewById(R.id.tv_job_title)
        tvPeriod = view.findViewById(R.id.tv_period)
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
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Setup submit button
        btnSubmit.setOnClickListener {
            submitMaintenanceTasks()
        }
        
        // Setup retry button
        btnRetry.setOnClickListener {
            loadMaintenanceTasks()
        }
    }
    
    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Set job details
        tvJobTitle.text = propertyName
        tvPeriod.text = "Periode: monthly"
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
                
                val response = MaintenanceService.getMaintenanceTaskJob(
                    context = requireContext(),
                    noAssets = assetNo,
                    propID = currentPropID
                )
                
                if (response["data"] != null && response["data"] is List<*>) {
                    val maintenanceDataList = response["data"] as List<*>
                    
                    val processedTasks = mutableListOf<Map<String, Any>>()
                    
                    // Process all maintenance tasks from the API
                    for (recordIndex in maintenanceDataList.indices) {
                        val maintenanceData = maintenanceDataList[recordIndex] as? Map<String, Any>
                        val jobtask = maintenanceData?.get("jobtask")?.toString() ?: ""
                        
                        // Debug logging untuk melihat data dari API
                        android.util.Log.d("MaintenanceJobTask", "Maintenance data: $maintenanceData")
                        
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
                                
                                android.util.Log.d("MaintenanceJobTask", "Task $recordIndex: taskNo=$taskNo, mntId=$mntId, mntUniq=$mntUniq, id=$id, title=$taskText")
                                
                                // Debug: Log semua data dari maintenanceData
                                android.util.Log.d("MaintenanceJobTask", "Full maintenance data for task $recordIndex: $maintenanceData")
                                
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
                
                // Step 1: Update task status
                val taskNo = task["taskNo"]?.toString() ?: ""
                val taskMntId = task["mntId"]?.toString() ?: ""
                val taskMntUniq = task["mntUniq"]?.toString() ?: ""
                android.util.Log.d("MaintenanceJobTask", "Updating task: taskNo=$taskNo, mntId=$taskMntId, mntUniq=$taskMntUniq, isCompleted=$isCompleted, userName=$userName")
                android.util.Log.d("MaintenanceJobTask", "Full task data: $task")
                
                // Use taskNo as taskId (6 digit number)
                val taskIdToUse = taskNo
                
                MaintenanceService.updateMaintenanceTaskStatus(
                    taskId = taskIdToUse,
                    isDone = isCompleted,
                    doneBy = if (isCompleted) userName else ""
                )
                
                // Update local state - only update the specific task
                val updatedTasks = tasks.toMutableList()
                updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                    put("completed", isCompleted)
                }
                tasks = updatedTasks
                // Don't call notifyItemChanged to avoid rebinding and resetting checkbox
                updateProgressStatus()
                
                // Update visual styling for the specific task item
                val holder = maintenanceAdapter?.let { adapter ->
                    (recyclerView.findViewHolderForAdapterPosition(taskIndex) as? MaintenanceJobTaskAdapter.TaskViewHolder)
                }
                holder?.let { viewHolder ->
                    val task = tasks[taskIndex]
                    val isCompleted = task["completed"] as? Boolean ?: false
                    
                    // Update text styling
                    if (isCompleted) {
                        viewHolder.tvTaskTitle.setTextColor(resources.getColor(R.color.grey, null))
                        viewHolder.tvTaskTitle.paintFlags = viewHolder.tvTaskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        viewHolder.itemView.isActivated = true
                    } else {
                        viewHolder.tvTaskTitle.setTextColor(resources.getColor(R.color.black, null))
                        viewHolder.tvTaskTitle.paintFlags = viewHolder.tvTaskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        viewHolder.itemView.isActivated = false
                    }
                }
                
                // Step 2: If task is completed, check if all tasks are done
                if (isCompleted) {
                    val allCompleted = tasks.all { it["completed"] == true }
                    
                    if (allCompleted) {
                        // Step 3: Update maintenance event with notes (same as Flutter)
                        try {
                            val note = etNote.text.toString().trim()
                            
                            // Get the actual event ID from the first task
                            val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                            val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                            val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                            
                            // Update event status and notes together (same as Flutter)
                            MaintenanceService.updateMaintenanceEvent(
                                mntId = eventIdToUse,
                                status = "done",
                                notes = note // Pass notes directly to updateMaintenanceEvent
                            )
                            
                            Toast.makeText(context, "All tasks completed! Maintenance event updated.", Toast.LENGTH_LONG).show()
                        } catch (eventError: Exception) {
                            Toast.makeText(context, "Task updated but event update failed: ${eventError.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Task marked as completed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Task marked as incomplete", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                val errorMsg = "Error updating task: ${e.message}"
                android.util.Log.e("MaintenanceJobTask", errorMsg)
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                
                // Revert the UI state if API call failed
                val updatedTasks = tasks.toMutableList()
                val taskIndex = updatedTasks.indexOfFirst { it["id"] == taskId }
                if (taskIndex != -1) {
                    updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                        put("completed", !isCompleted) // Revert to previous state
                    }
                    tasks = updatedTasks
                    // Don't call notifyItemChanged to avoid rebinding and resetting checkbox
                    updateProgressStatus()
                }
            }
        }
    }
    
    private fun updateProgressStatus() {
        val completedCount = tasks.count { it["completed"] == true }
        val totalCount = tasks.size
        
        tvProgressStatus.text = "Progress: $completedCount/$totalCount tasks completed"
        btnSubmit.text = "Submit Maintenance ($completedCount/$totalCount)"
        
        // Update progress icon based on completion
        if (completedCount == totalCount && totalCount > 0) {
            ivProgressIcon.setImageResource(R.drawable.ic_status_done)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.status_done, null))
        } else {
            ivProgressIcon.setImageResource(R.drawable.ic_task)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.blue, null))
        }
    }
    

    
    private fun submitMaintenanceTasks() {
        if (isSubmitting) return
        
        val completedTasks = tasks.filter { it["completed"] == true }
        if (completedTasks.isEmpty()) {
            Toast.makeText(context, "Please complete all tasks before submitting", Toast.LENGTH_SHORT).show()
            return
        }
        
        isSubmitting = true
        btnSubmit.isEnabled = false
        btnSubmit.text = "Submitting..."
        
        lifecycleScope.launch {
            try {
                val note = etNote.text.toString().trim()
                
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                // Step 1: Update all tasks in tblmnttask first (same as Flutter)
                for (task in completedTasks) {
                    val taskId = task["taskNo"]?.toString() ?: ""
                    
                    if (taskId.isNotEmpty()) {
                        MaintenanceService.updateMaintenanceTaskStatus(
                            taskId = taskId,
                            isDone = true,
                            doneBy = "user" // TODO: Get actual user ID
                        )
                    }
                }
                
                // Step 2: If all tasks updated successfully, update notes and event
                // Get the actual event ID (number) from the first task
                val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                
                android.util.Log.d("MaintenanceJobTask", "=== EVENT ID DEBUG ===")
                android.util.Log.d("MaintenanceJobTask", "mntId from arguments (string): $mntId")
                android.util.Log.d("MaintenanceJobTask", "actualEventId from task (number): $actualEventId")
                android.util.Log.d("MaintenanceJobTask", "taskMntId from task (number): $taskMntId")
                android.util.Log.d("MaintenanceJobTask", "eventIdToUse for API: $eventIdToUse")
                
                // Debug: Check what's in tasks[0]
                if (tasks.isNotEmpty()) {
                    android.util.Log.d("MaintenanceJobTask", "First task data: ${tasks[0]}")
                    android.util.Log.d("MaintenanceJobTask", "First task eventId: ${tasks[0]["eventId"]}")
                }
                
                if (eventIdToUse.isNotEmpty()) {
                    try {
                        // Debug logging
                        android.util.Log.d("MaintenanceJobTask", "=== SUBMIT MAINTENANCE DEBUG ===")
                        android.util.Log.d("MaintenanceJobTask", "mntId: $mntId")
                        android.util.Log.d("MaintenanceJobTask", "eventIdToUse: $eventIdToUse")
                        android.util.Log.d("MaintenanceJobTask", "status: done")
                        android.util.Log.d("MaintenanceJobTask", "notes: $note")
                        android.util.Log.d("MaintenanceJobTask", "currentPropID: $currentPropID")
                        
                                                                // Step 2: Update event status and notes using update_maintenance_event.php (same as Flutter)
                        android.util.Log.d("MaintenanceJobTask", "=== EVENT UPDATE DEBUG ===")
                        android.util.Log.d("MaintenanceJobTask", "Updating event status and notes...")
                        android.util.Log.d("MaintenanceJobTask", "mntId for event: $eventIdToUse")
                        android.util.Log.d("MaintenanceJobTask", "status: done")
                        android.util.Log.d("MaintenanceJobTask", "notes: $note")
                        android.util.Log.d("MaintenanceJobTask", "eventIdToUse type: ${eventIdToUse::class.java.simpleName}")
                        android.util.Log.d("MaintenanceJobTask", "eventIdToUse length: ${eventIdToUse.length}")
                        android.util.Log.d("MaintenanceJobTask", "eventIdToUse is numeric: ${eventIdToUse.matches(Regex("^\\d+$"))}")
                        
                        MaintenanceService.updateMaintenanceEvent(
                            mntId = eventIdToUse,
                            status = "done",
                            notes = note // Pass notes directly to updateMaintenanceEvent (same as Flutter)
                        )
                        android.util.Log.d("MaintenanceJobTask", "Event status updated successfully")
                        
                        if (isAdded) {
                            Toast.makeText(context, "Maintenance completed successfully!", Toast.LENGTH_SHORT).show()
                            // Navigate back
                            parentFragmentManager.popBackStack()
                        }
                    } catch (eventError: Exception) {
                        // Debug logging for error
                        android.util.Log.e("MaintenanceJobTask", "Event update failed: ${eventError.message}")
                        android.util.Log.e("MaintenanceJobTask", "Stack trace:")
                        eventError.printStackTrace()
                        
                        // If tblevent update fails, still show success for tblmnttask
                        if (isAdded) {
                            Toast.makeText(context, "Tasks completed but event update failed: ${eventError.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(context, "Error submitting maintenance: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSubmitting = false
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit"
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
                    android.util.Log.d("MaintenanceJobTask", "Loading existing notes for mntId: $eventIdToUse")
                    
                    val existingNotes = MaintenanceService.getMaintenanceNotes(
                        mntId = eventIdToUse,
                        propID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                    )
                    
                    android.util.Log.d("MaintenanceJobTask", "Existing notes loaded: $existingNotes")
                    
                    // Set notes to EditText
                    etNote.setText(existingNotes)
                }
            } catch (e: Exception) {
                android.util.Log.e("MaintenanceJobTask", "Error loading existing notes: ${e.message}")
                // Clear text field if error
                etNote.setText("")
            }
        }
    }
    
    companion object {
        fun newInstance(
            assetNo: String,
            mntId: String,
            propertyName: String,
            propID: String
        ): MaintenanceJobTaskFragment {
            return MaintenanceJobTaskFragment().apply {
                arguments = Bundle().apply {
                    putString("assetNo", assetNo)
                    putString("mntId", mntId)
                    putString("propertyName", propertyName)
                    putString("propID", propID)
                }
            }
        }
    }
}
