package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sofindo.ems.R
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.services.AssetService
import com.sofindo.ems.models.Maintenance
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceDetailFragment : Fragment() {
    
    private lateinit var ivAssetImage: android.widget.ImageView
    private lateinit var tvAssetNo: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvProperty: TextView
    private lateinit var tvMerk: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvSerialNo: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var tvSupplier: TextView
    private lateinit var tvDatePurchased: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvMaintenanceId: TextView
    private lateinit var tvDescription: TextView
    private lateinit var layoutDescription: View
    private lateinit var btnViewJobTasksPast: MaterialButton
    private lateinit var btnViewJobTasksNearest: MaterialButton
    private lateinit var btnViewJobTasksUpcoming: MaterialButton
    private lateinit var tvNoScheduleMessage: TextView
    private lateinit var progressBar: ProgressBar
    
    private var mntId: String = ""
    private var propID: String = ""
    private var assetUrl: String = ""
    private var assetData: Map<String, Any>? = null
    
    // Schedule data
    private var pastSchedule: Maintenance? = null
    private var nearestSchedule: Maintenance? = null
    private var upcomingSchedule: Maintenance? = null
    
    // Store schedule data with mntId and status for navigation and button state
    private var scheduleDataMap: Map<Int, Map<String, Any>> = emptyMap()
    
    // Track if no schedule message has been shown to avoid duplicate toasts
    private var hasShownNoScheduleMessage = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance_detail, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        arguments?.let { args ->
            mntId = args.getString("mntId", "")
            propID = args.getString("propID", "")
            assetUrl = args.getString("assetUrl", "")
        }
        
        initViews(view)
        setupToolbarMenu()
        loadMaintenanceDetails()
    }
    
    private fun initViews(view: View) {
        ivAssetImage = view.findViewById(R.id.iv_asset_image)
        tvAssetNo = view.findViewById(R.id.tv_asset_no)
        tvCategory = view.findViewById(R.id.tv_category)
        tvProperty = view.findViewById(R.id.tv_property)
        tvMerk = view.findViewById(R.id.tv_merk)
        tvModel = view.findViewById(R.id.tv_model)
        tvSerialNo = view.findViewById(R.id.tv_serial_no)
        tvCapacity = view.findViewById(R.id.tv_capacity)
        tvSupplier = view.findViewById(R.id.tv_supplier)
        tvDatePurchased = view.findViewById(R.id.tv_date_purchased)
        tvLocation = view.findViewById(R.id.tv_location)
        tvMaintenanceId = view.findViewById(R.id.tv_maintenance_id)
        tvDescription = view.findViewById(R.id.tv_description)
        layoutDescription = view.findViewById(R.id.layout_description)
        btnViewJobTasksPast = view.findViewById(R.id.btn_view_job_tasks_past)
        btnViewJobTasksNearest = view.findViewById(R.id.btn_view_job_tasks_nearest)
        btnViewJobTasksUpcoming = view.findViewById(R.id.btn_view_job_tasks_upcoming)
        tvNoScheduleMessage = view.findViewById(R.id.tv_no_schedule_message)
        progressBar = view.findViewById(R.id.progress_bar)
        
        // Setup toolbar back button
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Set navigation icon color to white (for dark AppBar background)
        toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, null))
        
        // Set initial title
        toolbar.title = "Asset Detail"
        
        // Setup button click listeners
        btnViewJobTasksPast.setOnClickListener {
            pastSchedule?.let { schedule ->
                navigateToJobTasks(schedule)
            }
        }
        
        btnViewJobTasksNearest.setOnClickListener {
            nearestSchedule?.let { schedule ->
                navigateToJobTasks(schedule)
            }
        }
        
        btnViewJobTasksUpcoming.setOnClickListener {
            upcomingSchedule?.let { schedule ->
                navigateToJobTasks(schedule)
            }
        }
    }
    
    private fun navigateToJobTasks(schedule: Maintenance) {
        // Get schedule data from map
        val scheduleData = scheduleDataMap[schedule.id]
        
        // Get eventId from schedule (this is the actual event ID from tblevent) - CRITICAL
        val eventId = schedule.id.toString()
        
        // Validate eventId is not empty (like iOS)
        if (eventId.isEmpty()) {
            Toast.makeText(context, "Event ID tidak ditemukan. Silakan pilih schedule terlebih dahulu.", Toast.LENGTH_SHORT).show()
            android.util.Log.e("MaintenanceDetail", "Cannot open job tasks: eventId is empty")
            return
        }
        
        // Get assetNo - ensure it's not empty (like iOS)
        var assetNo = assetData?.get("no")?.toString()?.trim()
        if (assetNo.isNullOrEmpty()) {
            // Fallback to TextView if assetData is null
            assetNo = tvAssetNo.text.toString().trim()
            assetNo = assetNo.replace("Asset No:", "").trim()
        }
        
        // Validate assetNo is not empty
        if (assetNo.isEmpty() || assetNo == "N/A") {
            Toast.makeText(context, "Asset number tidak ditemukan.", Toast.LENGTH_SHORT).show()
            android.util.Log.e("MaintenanceDetail", "Cannot open job tasks: assetNo is empty")
            return
        }
        
        // Get propID - ensure it's not empty (like iOS)
        var currentPropID = propID
        if (currentPropID.isEmpty()) {
            currentPropID = UserService.getCurrentPropIDSync() ?: ""
        }
        
        // Validate propID is not empty
        if (currentPropID.isEmpty()) {
            Toast.makeText(context, "Property ID tidak ditemukan. Silakan login ulang.", Toast.LENGTH_SHORT).show()
            android.util.Log.e("MaintenanceDetail", "Cannot open job tasks: propID is empty")
            return
        }
        
        // Get mntId from schedule data map if available
        val mntIdToUse = scheduleData?.get("mntId")?.toString() 
            ?: assetData?.get("mntId")?.toString() 
            ?: schedule.id.toString()
        
        // Get formatted date from schedule (for display)
        val scheduleFormattedDate = scheduleData?.get("formatted_date")?.toString() 
            ?: schedule.formattedDate
        
        // Get start_date from schedule (for filtering tasks)
        val scheduleStartDate = scheduleData?.get("start_date")?.toString() 
            ?: schedule.startDate
        
        // Get property name
        val propertyName = assetData?.get("property")?.toString() ?: ""
        
        android.util.Log.d("MaintenanceDetail", "Opening job tasks:")
        android.util.Log.d("MaintenanceDetail", "  assetNo: $assetNo")
        android.util.Log.d("MaintenanceDetail", "  propID: $currentPropID")
        android.util.Log.d("MaintenanceDetail", "  eventId: $eventId")
        android.util.Log.d("MaintenanceDetail", "  scheduleStartDate: $scheduleStartDate")
        
        val fragment = MaintenanceJobTaskFragment.newInstance(
            assetNo = assetNo,
            mntId = mntIdToUse,
            propertyName = propertyName,
            propID = currentPropID,
            scheduleDate = scheduleFormattedDate,
            scheduleStartDate = scheduleStartDate,
            eventId = eventId
        )
        
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("maintenance_job_task")
            .commit()
    }
    
    private fun setupToolbarMenu() {
        val toolbar = view?.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val context = this.context ?: return
        
        // Create custom action view for History menu item
        toolbar?.post {
            val menuItem = toolbar.menu.findItem(R.id.action_view_history)
            menuItem?.let {
                // Create a custom view with icon and text
                val actionView = LayoutInflater.from(context).inflate(
                    R.layout.menu_item_history_action,
                    null
                )
                
                // Set click listener on the action view
                actionView.setOnClickListener {
                    // Navigate to Maintenance History Fragment
                    val fragment = MaintenanceHistoryFragment.newInstance(
                        assetNo = assetData?.get("no")?.toString() ?: "",
                        mntId = assetData?.get("no")?.toString() ?: "", // Use asset number as mntId for history
                        propertyName = assetData?.get("property")?.toString() ?: "",
                        propID = propID
                    )
                    
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack("maintenance_history")
                        .commit()
                }
                
                it.actionView = actionView
            }
        }
        
        toolbar?.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_view_history -> {
                    // This will be handled by the action view click listener
                    true
                }
                else -> false
            }
        }
    }
    
    private fun loadMaintenanceDetails() {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                if (assetUrl.isNotEmpty()) {
                    // Load asset details from QR scan
                    loadAssetDetails()
                } else if (mntId.isNotEmpty()) {
                    // Load maintenance details
                    loadMaintenanceData()
                } else {
                    Toast.makeText(context, "No data provided", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun loadAssetDetails() {
        try {
            assetData = AssetService.getAssetDetail(assetUrl)
            
            // Asset data received from API
            
            // Update UI with asset data
            tvAssetNo.text = assetData?.get("no")?.toString() ?: "N/A"
            tvCategory.text = assetData?.get("category")?.toString() ?: "N/A"
            tvProperty.text = assetData?.get("property")?.toString() ?: "N/A"
            
            // Update toolbar title with property name
            val propertyName = assetData?.get("property")?.toString()
            if (!propertyName.isNullOrEmpty()) {
                val toolbar = view?.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                toolbar?.title = propertyName
            }
            tvMerk.text = assetData?.get("merk")?.toString() ?: "N/A"
            tvModel.text = assetData?.get("model")?.toString() ?: "N/A"
            tvSerialNo.text = assetData?.get("serno")?.toString() ?: "N/A"
            tvCapacity.text = assetData?.get("capacity")?.toString() ?: "N/A"
            tvSupplier.text = assetData?.get("supplier")?.toString() ?: "N/A"
            tvDatePurchased.text = assetData?.get("datePurchased")?.toString() ?: "N/A"
            tvLocation.text = assetData?.get("lokasi")?.toString() ?: "N/A"
            tvMaintenanceId.text = assetData?.get("mntId")?.toString() ?: "N/A"
            
            // Load description if available
            val description = assetData?.get("keterangan")?.toString()
            if (!description.isNullOrEmpty()) {
                tvDescription.text = description
                layoutDescription.visibility = View.VISIBLE
            } else {
                layoutDescription.visibility = View.GONE
            }
            
            // Load image if available - matching Flutter
            val imageUrl = assetData?.get("imageUrl")?.toString()
            
            // Image always visible - either real image or placeholder
            ivAssetImage.visibility = View.VISIBLE
            
            if (!imageUrl.isNullOrEmpty()) {
                // Load image using Glide - matching Flutter implementation
                Glide.with(this)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(ivAssetImage)
                    
                // Loading image from URL
            } else {
                // Show placeholder if no image URL
                ivAssetImage.setImageResource(R.drawable.ic_image_placeholder)
            }
            
            // Load maintenance schedules after asset data is loaded
            loadMaintenanceSchedules()
            
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading asset: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadMaintenanceSchedules() {
        lifecycleScope.launch {
            try {
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropIDSync() ?: ""
                if (currentPropID.isEmpty()) {
                    android.util.Log.e("MaintenanceDetail", "propID is empty")
                    hideAllScheduleButtons()
                    return@launch
                }
                
                // Get Asset No from assetData (preferred) or from displayed TextView as fallback
                var assetNo = assetData?.get("no")?.toString()?.trim()
                
                // If not found in assetData, try to get from TextView
                if (assetNo.isNullOrEmpty()) {
                    assetNo = tvAssetNo.text.toString().trim()
                    // Remove "Asset No:" prefix if present
                    assetNo = assetNo.replace("Asset No:", "").trim()
                }
                
                android.util.Log.d("MaintenanceDetail", "Loading schedules for Asset No: '$assetNo', propID: '$currentPropID'")
                android.util.Log.d("MaintenanceDetail", "Asset Data: $assetData")
                
                if (assetNo.isNullOrEmpty() || assetNo == "N/A") {
                    android.util.Log.e("MaintenanceDetail", "Asset No is empty or N/A")
                    hideAllScheduleButtons()
                    return@launch
                }
                
                // Get asset schedules from new endpoint
                val scheduleDataList = MaintenanceService.getAssetSchedule(assetNo, currentPropID)
                
                android.util.Log.d("MaintenanceDetail", "Found ${scheduleDataList.size} schedules")
                
                if (scheduleDataList.isEmpty()) {
                    android.util.Log.d("MaintenanceDetail", "No schedules found, hiding buttons")
                    hideAllScheduleButtons()
                    return@launch
                }
                
                // Convert schedule data to Maintenance objects for categorization
                // Store original schedule data for later use in navigation
                val maintenanceList = scheduleDataList.mapNotNull { scheduleData ->
                    try {
                        Maintenance(
                            id = (scheduleData["id"] as? Number)?.toInt() ?: 0,
                            title = scheduleData["property"]?.toString() ?: "",
                            description = scheduleData["location"]?.toString() ?: "",
                            startDate = scheduleData["start_date"]?.toString() ?: "",
                            endDate = scheduleData["end_date"]?.toString() ?: "",
                            status = scheduleData["status"]?.toString() ?: "",
                            propID = currentPropID,
                            formattedDate = scheduleData["formatted_date"]?.toString() ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                // Store schedule data with mntId for navigation
                scheduleDataMap = scheduleDataList.associateBy { 
                    (it["id"] as? Number)?.toInt() ?: 0 
                }
                
                android.util.Log.d("MaintenanceDetail", "Converted ${maintenanceList.size} schedules for categorization")
                
                if (maintenanceList.isEmpty()) {
                    android.util.Log.d("MaintenanceDetail", "No valid schedules after conversion")
                    hideAllScheduleButtons()
                    return@launch
                }
                
                categorizeSchedules(maintenanceList)
                
            } catch (e: Exception) {
                // Log error with details
                android.util.Log.e("MaintenanceDetail", "Error loading schedules: ${e.message}", e)
                e.printStackTrace()
                // Show error toast for debugging
                view?.let {
                    Toast.makeText(context, "Error loading schedules: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                hideAllScheduleButtons()
            }
        }
    }
    
    private fun hideAllScheduleButtons() {
        view?.let {
            btnViewJobTasksPast.visibility = View.GONE
            btnViewJobTasksNearest.visibility = View.GONE
            btnViewJobTasksUpcoming.visibility = View.GONE
            // Show no schedule message
            tvNoScheduleMessage.visibility = View.VISIBLE
            // Show toast notification only once
            if (!hasShownNoScheduleMessage) {
                Toast.makeText(context, "No maintenance schedule available", Toast.LENGTH_SHORT).show()
                hasShownNoScheduleMessage = true
            }
        }
    }
    
    private fun categorizeSchedules(schedules: List<Maintenance>) {
        val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) // Short format: 15 Dec 2025
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        
        // Parse and sort schedules by startDate, and group by unique dates
        val parsedSchedules = schedules.mapNotNull { maintenance ->
            try {
                val dateStr = maintenance.startDate.split(" ")[0] // Get date only
                val date = dateOnlyFormat.parse(dateStr)
                if (date != null) {
                    Pair(maintenance, date)
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.second }
        
        // Group by unique dates to avoid duplicates
        val uniqueDateSchedules = mutableListOf<Pair<Maintenance, Date>>()
        val seenDates = mutableSetOf<String>()
        for (pair in parsedSchedules) {
            val dateStr = dateOnlyFormat.format(pair.second)
            if (!seenDates.contains(dateStr)) {
                seenDates.add(dateStr)
                uniqueDateSchedules.add(pair)
            }
        }
        
        // Categorize schedules using unique dates
        val pastSchedules = uniqueDateSchedules.filter { it.second.before(today.time) }
        val todaySchedules = uniqueDateSchedules.filter { 
            val cal = Calendar.getInstance()
            cal.time = it.second
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis == today.timeInMillis
        }
        val futureSchedules = uniqueDateSchedules.filter { it.second.after(today.time) }
        
        // Find past schedule (closest to today - the most recent past)
        pastSchedule = pastSchedules.lastOrNull()?.first
        
        // Find nearest schedule (today or closest future)
        nearestSchedule = when {
            todaySchedules.isNotEmpty() -> todaySchedules.firstOrNull()?.first
            futureSchedules.isNotEmpty() -> futureSchedules.firstOrNull()?.first
            else -> null
        }
        
        // Find upcoming schedule (next after nearest - must be different date)
        // Since we already have unique dates, we can simply get the next schedule in the list
        upcomingSchedule = when {
            // If nearest is today, upcoming is the first future schedule
            todaySchedules.isNotEmpty() && futureSchedules.isNotEmpty() -> {
                futureSchedules.firstOrNull()?.first
            }
            // If nearest is a future schedule, upcoming is the next one in the list
            // (which is guaranteed to have a different date since we filtered for unique dates)
            futureSchedules.size > 1 -> {
                // Find the index of nearest schedule in futureSchedules
                val nearestIndex = futureSchedules.indexOfFirst { 
                    it.first.id == nearestSchedule?.id 
                }
                // Get the next schedule after nearest
                if (nearestIndex >= 0 && nearestIndex < futureSchedules.size - 1) {
                    futureSchedules[nearestIndex + 1].first
                } else {
                    null
                }
            }
            else -> null
        }
        
        // Update UI with buttons
        updateScheduleButtons(displayDateFormat)
    }
    
    private fun updateScheduleButtons(dateFormat: SimpleDateFormat) {
        view?.let {
            // Hide no schedule message when there are schedules
            tvNoScheduleMessage.visibility = View.GONE
            // Reset flag when schedules are found
            hasShownNoScheduleMessage = false
            
            // Update Past button
            pastSchedule?.let { schedule ->
                try {
                    val dateStr = schedule.startDate.split(" ")[0]
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                    val formattedDate = date?.let { dateFormat.format(it) } ?: schedule.formattedDate
                    btnViewJobTasksPast.text = "View Job Task $formattedDate"
                    btnViewJobTasksPast.visibility = View.VISIBLE
                    
                    // Get status from schedule data map
                    val scheduleData = scheduleDataMap[schedule.id]
                    val status = (scheduleData?.get("status")?.toString()?.trim() ?: schedule.status.trim()).ifEmpty { "" }
                    // Only disable if status is exactly "done" (case-insensitive)
                    // Status kosong, null, atau pecahan seperti "2/6 done" tetap enable
                    val isDone = status.lowercase() == "done"
                    
                    btnViewJobTasksPast.isEnabled = !isDone
                    btnViewJobTasksPast.alpha = if (isDone) 0.5f else 1.0f
                } catch (e: Exception) {
                    btnViewJobTasksPast.text = "View Job Task ${schedule.formattedDate}"
                    btnViewJobTasksPast.visibility = View.VISIBLE
                    
                    val scheduleData = scheduleDataMap[schedule.id]
                    val status = (scheduleData?.get("status")?.toString()?.trim() ?: schedule.status.trim()).ifEmpty { "" }
                    // Only disable if status is exactly "done" (case-insensitive)
                    // Status kosong, null, atau pecahan seperti "2/6 done" tetap enable
                    val isDone = status.lowercase() == "done"
                    
                    btnViewJobTasksPast.isEnabled = !isDone
                    btnViewJobTasksPast.alpha = if (isDone) 0.5f else 1.0f
                }
            } ?: run {
                btnViewJobTasksPast.visibility = View.GONE
            }
            
            // Update Nearest button
            nearestSchedule?.let { schedule ->
                try {
                    val dateStr = schedule.startDate.split(" ")[0]
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                    val formattedDate = date?.let { dateFormat.format(it) } ?: schedule.formattedDate
                    btnViewJobTasksNearest.text = "View Job Task $formattedDate"
                    btnViewJobTasksNearest.visibility = View.VISIBLE
                    
                    // Get status from schedule data map
                    val scheduleData = scheduleDataMap[schedule.id]
                    val status = (scheduleData?.get("status")?.toString()?.trim() ?: schedule.status.trim()).ifEmpty { "" }
                    // Only disable if status is exactly "done" (case-insensitive)
                    // Status kosong, null, atau pecahan seperti "2/6 done" tetap enable
                    val isDone = status.lowercase() == "done"
                    
                    btnViewJobTasksNearest.isEnabled = !isDone
                    btnViewJobTasksNearest.alpha = if (isDone) 0.5f else 1.0f
                } catch (e: Exception) {
                    btnViewJobTasksNearest.text = "View Job Task ${schedule.formattedDate}"
                    btnViewJobTasksNearest.visibility = View.VISIBLE
                    
                    val scheduleData = scheduleDataMap[schedule.id]
                    val status = (scheduleData?.get("status")?.toString()?.trim() ?: schedule.status.trim()).ifEmpty { "" }
                    // Only disable if status is exactly "done" (case-insensitive)
                    // Status kosong, null, atau pecahan seperti "2/6 done" tetap enable
                    val isDone = status.lowercase() == "done"
                    
                    btnViewJobTasksNearest.isEnabled = !isDone
                    btnViewJobTasksNearest.alpha = if (isDone) 0.5f else 1.0f
                }
            } ?: run {
                btnViewJobTasksNearest.visibility = View.GONE
            }
            
            // Update Upcoming button
            upcomingSchedule?.let { schedule ->
                try {
                    val dateStr = schedule.startDate.split(" ")[0]
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                    val formattedDate = date?.let { dateFormat.format(it) } ?: schedule.formattedDate
                    btnViewJobTasksUpcoming.text = "View Job Task $formattedDate"
                    btnViewJobTasksUpcoming.visibility = View.VISIBLE
                    
                    // Get status from schedule data map
                    val scheduleData = scheduleDataMap[schedule.id]
                    val status = (scheduleData?.get("status")?.toString()?.trim() ?: schedule.status.trim()).ifEmpty { "" }
                    // Only disable if status is exactly "done" (case-insensitive)
                    // Status kosong, null, atau pecahan seperti "2/6 done" tetap enable
                    val isDone = status.lowercase() == "done"
                    
                    btnViewJobTasksUpcoming.isEnabled = !isDone
                    btnViewJobTasksUpcoming.alpha = if (isDone) 0.5f else 1.0f
                } catch (e: Exception) {
                    btnViewJobTasksUpcoming.text = "View Job Task ${schedule.formattedDate}"
                    btnViewJobTasksUpcoming.visibility = View.VISIBLE
                    
                    val scheduleData = scheduleDataMap[schedule.id]
                    val status = (scheduleData?.get("status")?.toString()?.trim() ?: schedule.status.trim()).ifEmpty { "" }
                    // Only disable if status is exactly "done" (case-insensitive)
                    // Status kosong, null, atau pecahan seperti "2/6 done" tetap enable
                    val isDone = status.lowercase() == "done"
                    
                    btnViewJobTasksUpcoming.isEnabled = !isDone
                    btnViewJobTasksUpcoming.alpha = if (isDone) 0.5f else 1.0f
                }
            } ?: run {
                btnViewJobTasksUpcoming.visibility = View.GONE
            }
        }
    }
    
    private suspend fun loadMaintenanceData() {
        // Get propID from UserService if not provided
        val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
        
        if (currentPropID.isEmpty()) {
            Toast.makeText(context, "Property ID not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // TODO: Load maintenance details if needed
        // For now, we'll use placeholder data
        tvAssetNo.text = "Maintenance Task"
        tvCategory.text = "Maintenance description will be loaded here"
        tvProperty.text = "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}"
        tvLocation.text = "Status: Pending"
    }
    
    // Removed updateMaintenanceStatus function as it's no longer needed
    
    companion object {
        fun newInstance(mntId: String, propID: String, assetUrl: String = ""): MaintenanceDetailFragment {
            return MaintenanceDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("mntId", mntId)
                    putString("propID", propID)
                    putString("assetUrl", assetUrl)
                }
            }
        }
    }
}
