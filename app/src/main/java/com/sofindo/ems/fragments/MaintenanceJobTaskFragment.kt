package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
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
            
            // Arguments loaded
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
        
        // Setup update notes button
        btnSubmit.setOnClickListener {
            updateMaintenanceNotes()
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
                
                // Step 1: Update task status
                val taskNo = task["taskNo"]?.toString() ?: ""
                val taskMntId = task["mntId"]?.toString() ?: ""
                val taskMntUniq = task["mntUniq"]?.toString() ?: ""
                // Update task status
                
                // Use taskNo as taskId (6 digit number)
                val taskIdToUse = taskNo
                
                // Update doneBy with current user
                val newDoneBy = if (isCompleted) userName else ""
                
                MaintenanceService.updateMaintenanceTaskStatus(
                    taskId = taskIdToUse,
                    isDone = isCompleted,
                    doneBy = newDoneBy
                )
                
                // Update local state - only update the specific task
                val updatedTasks = tasks.toMutableList()
                updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                    put("completed", isCompleted)
                    put("doneBy", newDoneBy) // Store doneBy info for display
                }
                tasks = updatedTasks
                // Don't call notifyItemChanged to avoid rebinding and resetting checkbox
                updateProgressStatus()
                
                // Update adapter to refresh display with doneBy info
                maintenanceAdapter?.updateTasks(tasks)
                
                // Step 2: If task is completed, check if all tasks are done
                if (isCompleted) {
                    val allCompleted = tasks.all { it["completed"] == true }
                    
                    if (allCompleted) {
                        // Auto-complete: Update maintenance event status to "done" (without notes)
                        try {
                            // Get the actual event ID from the first task
                            val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                            val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                            val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                            
                            // Update event status to "done" (notes will be updated via Update Notes button)
                            MaintenanceService.updateMaintenanceEvent(
                                mntId = eventIdToUse,
                                status = "done",
                                notes = "" // Empty notes, will be updated later
                            )
                            
                            Toast.makeText(context, "All tasks completed! Maintenance event marked as done.", Toast.LENGTH_LONG).show()
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
        btnSubmit.text = "Update Notes"
        
        // Update progress icon based on completion
        if (completedCount == totalCount && totalCount > 0) {
            ivProgressIcon.setImageResource(R.drawable.ic_status_done)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.status_done, null))
        } else {
            ivProgressIcon.setImageResource(R.drawable.ic_task)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.blue, null))
        }
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
                // Note already captured above
                

                
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                // Update maintenance notes only
                // Get the actual event ID from the first task
                val actualEventId = if (tasks.isNotEmpty()) tasks[0]["eventId"]?.toString() ?: "" else ""
                val taskMntId = if (tasks.isNotEmpty()) tasks[0]["mntId"]?.toString() ?: "" else ""
                val eventIdToUse = if (actualEventId.isNotEmpty()) actualEventId else if (taskMntId.isNotEmpty()) taskMntId else mntId
                
                if (eventIdToUse.isNotEmpty()) {
                    try {
                        // Update only the notes (status already "done" from auto-complete)
                        MaintenanceService.updateMaintenanceEvent(
                            mntId = eventIdToUse,
                            status = "done", // Keep status as done
                            notes = note // Update notes
                        )
                        
                        if (isAdded) {
                            Toast.makeText(context, "Notes updated successfully!", Toast.LENGTH_SHORT).show()
                            // Navigate back
                            parentFragmentManager.popBackStack()
                        }
                    } catch (eventError: Exception) {
                        if (isAdded) {
                            Toast.makeText(context, "Failed to update notes: ${eventError.message}", Toast.LENGTH_SHORT).show()
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
                btnSubmit.text = "Update Notes"
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
