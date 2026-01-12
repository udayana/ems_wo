package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sofindo.ems.R
import com.sofindo.ems.adapters.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.dialogs.ReviewDialog
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class OutboxFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var btnFilter: ImageButton
    private lateinit var tvEmpty: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var switchFilter: android.widget.Switch

    
    // Exactly like HomeFragment
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var isLoading = true
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var selectedStatus = ""
    private var currentPropID: String? = null
    private var username: String? = null
    private var userDept: String? = null
    private var userID: String? = null
    private val statusCounts = mutableMapOf<String, Int>()
    private var isLoadingStatusCounts = true
    private var filterByDept = false // Toggle between orderBy and dept like iOS
    private var reviewDialog: ReviewDialog? = null
    private var isEditingReview = false // Track if we're editing existing review
    private var currentReviewId: Int? = null // Store review_id for update
    private var reviewCreatedAt: String? = null // Store created_at for time validation
    private var currentWorkOrderForEdit: Map<String, Any>? = null // Store work order for edit
    
    // Exactly like Flutter statusOptions
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
    
    companion object {
        private const val EDIT_WO_REQUEST_CODE = 1001
    }
    
    // RecyclerView adapter
    private lateinit var workOrderAdapter: WorkOrderAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_outbox, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        initializeData()
    }
    
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh)
        btnFilter = view.findViewById(R.id.btn_filter)
        tvEmpty = view.findViewById(R.id.tv_empty)
        tvUsername = view.findViewById(R.id.tv_username)
        tvDepartment = view.findViewById(R.id.tv_department)
        switchFilter = view.findViewById(R.id.switch_filter)

        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Get current user name and initialize adapter
        lifecycleScope.launch {
            val user = UserService.getCurrentUser()
            val currentUserName = user?.fullName ?: user?.username ?: user?.email
            
            // Initialize adapter with menu callbacks for Outbox (Detail, Edit, Delete)
            workOrderAdapter = WorkOrderAdapter(
                onItemClick = { workOrder ->
                    // Handle work order click (this won't be used since we're using card click for menu)
                    onWorkOrderClick(workOrder)
                },
                onEditClick = { workOrder ->
                    // Handle edit click
                    onEditWorkOrder(workOrder)
                },
                onDeleteClick = { workOrder ->
                    // Handle delete click
                    onDeleteWorkOrder(workOrder)
                },
                onDetailClick = { workOrder ->
                    // Handle detail click
                    onDetailWorkOrder(workOrder)
                },
                onFollowUpClick = { workOrder ->
                    // Handle follow up click (not used in Outbox)
                    onFollowUpWorkOrder(workOrder)
                },
                onNeedReviewClick = { workOrder ->
                    // Handle need review click
                    onNeedReviewWorkOrder(workOrder)
                },
                onReviewClick = { workOrder ->
                    // Handle edit review click (click on "You reviewed" text)
                    onEditReview(workOrder)
                },
                showSender = false,
                replaceWotoWithOrderBy = false,
                isHomeFragment = false,
                currentUserName = currentUserName
            )
            
            recyclerView.adapter = workOrderAdapter
        }
        
        // Add infinite scroll listener
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                if (!isLoadingMore && hasMoreData) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                        firstVisibleItemPosition >= 0 &&
                        totalItemCount >= 10) {
                        
                        // Load more data
                        loadMoreData()
                    }
                }
            }
        })
    }
    
    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        
        btnFilter.setOnClickListener {
            showFilterPopup()
        }
        
        // Toggle switch listener like iOS
        switchFilter.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("OutboxFragment", "Switch toggled - isChecked: $isChecked, filterByDept: $filterByDept -> $isChecked")
            filterByDept = isChecked
            updateToggleUI()
            // Reset and reload when switching filter mode like iOS
            selectedStatus = ""
            currentPage = 1
            workOrders.clear()
            isLoading = false
            hasMoreData = true
            loadData(reset = true)
        }
    }
    
    private fun showFilterPopup() {
        val popup = PopupMenu(requireContext(), btnFilter)
        
        // Tampilkan placeholder terlebih dahulu
        popup.menu.clear()
        popup.menu.add("Loading …")?.isEnabled = false
        popup.show()

        // Quick calculation from existing data for immediate display
        recomputeStatusCountsFromWorkOrders()

        // Update menu with quick count
        updateFilterMenuItems(popup)

        // Then calculate accurately based on all pages asynchronously and update again
        lifecycleScope.launch {
            val precise = fetchOutboxStatusCountsAllPages()
            if (!isAdded) return@launch
            if (precise.isNotEmpty()) {
                statusCounts.clear()
                statusCounts.putAll(precise)
                updateFilterMenuItems(popup)
            }
        }
    }

    private fun updateFilterMenuItems(popup: PopupMenu) {
        popup.menu.clear()
        statusOptions.forEach { status ->
            val count = if (status.isEmpty()) {
                statusCounts[""] ?: 0
            } else if (status.lowercase() == "new") {
                statusCounts["new"] ?: 0
            } else {
                statusCounts[status] ?: 0
            }
            val displayName = when {
                status.isEmpty() -> "All"
                status.lowercase() == "new" -> "NEW"
                else -> status.uppercase()
            }
            val menuItem = popup.menu.add("$count → $displayName")
            menuItem.setOnMenuItemClickListener {
                onStatusChanged(status)
                true
            }
        }
    }
    
    private fun onWorkOrderClick(workOrder: Map<String, Any>) {
        // TODO: Navigate to work order detail
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Clicked Outbox WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onEditWorkOrder(workOrder: Map<String, Any>) {
        try {
            // Check if context is available
            val context = context ?: return
            
            // Navigate to edit work order activity
            val intent = android.content.Intent(context, com.sofindo.ems.activities.EditWorkOrderActivity::class.java)
            intent.putExtra("workOrder", workOrder as java.io.Serializable)
            startActivityForResult(intent, EDIT_WO_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: Cannot open edit screen", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun onDeleteWorkOrder(workOrder: Map<String, Any>) {
        val woNumber = workOrder["nour"]?.toString() ?: ""
        val woId = workOrder["woId"]?.toString() ?: ""
        
        if (woId.isEmpty()) {
            Toast.makeText(context, "Error: Work Order ID not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show confirmation dialog
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Work Order")
            .setMessage("Are you sure you want to delete WO: $woNumber?")
            .setPositiveButton("Delete") { _, _ ->
                // Call delete API
                deleteWorkOrderFromAPI(woId, woNumber)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteWorkOrderFromAPI(woId: String, woNumber: String) {
        lifecycleScope.launch {
            try {
                // Show loading toast
                Toast.makeText(context, "Deleting work order...", Toast.LENGTH_SHORT).show()
                
                // Call delete API
                val response = RetrofitClient.apiService.deleteWorkOrder(woId)
                
                // Check response
                val success = response["success"] as? Boolean ?: false
                val message = response["message"]?.toString() ?: "Unknown response"
                
                if (success) {
                    Toast.makeText(context, "Successfully deleted WO: $woNumber", Toast.LENGTH_SHORT).show()
                    // Refresh data after successful deletion
                    refreshData()
                } else {
                    Toast.makeText(context, "Failed to delete: $message", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error deleting work order: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun onDetailWorkOrder(workOrder: Map<String, Any>) {
        // Navigate to work order detail activity
        val intent = android.content.Intent(context, com.sofindo.ems.activities.WorkOrderDetailActivity::class.java)
        intent.putExtra("workOrder", workOrder as java.io.Serializable)
        startActivity(intent)
    }
    
    private fun onFollowUpWorkOrder(workOrder: Map<String, Any>) {
        // This function is not used in Outbox but required for adapter compatibility
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Follow Up WO: $woNumber (Outbox)", Toast.LENGTH_SHORT).show()
    }
    
    private fun onNeedReviewWorkOrder(workOrder: Map<String, Any>) {
        val woNumber = workOrder["nour"]?.toString() ?: ""
        val woId = workOrder["woId"]?.toString() ?: ""
        val tglWo = workOrder["tglWo"]?.toString() ?: ""
        
        if (woId.isEmpty()) {
            Toast.makeText(context, "Error: Work Order ID not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Reset edit mode
        isEditingReview = false
        currentReviewId = null
        reviewCreatedAt = tglWo // For new review, use tglWo
        
        // Show Review Dialog (half screen sheet)
        reviewDialog = ReviewDialog(
            context = requireContext(),
            woNumber = woNumber,
            createdAtDate = reviewCreatedAt,
            onSave = { rating, comment ->
                submitReview(woId, woNumber, rating, comment)
            },
            onCancel = {
                reviewDialog?.dismiss()
                reviewDialog = null
                isEditingReview = false
                currentReviewId = null
                reviewCreatedAt = null
                currentWorkOrderForEdit = null
            }
        )
        
        reviewDialog?.show()
    }
    
    private fun onEditReview(workOrder: Map<String, Any>) {
        val woNumber = workOrder["nour"]?.toString() ?: ""
        val woId = workOrder["woId"]?.toString() ?: ""
        
        if (woId.isEmpty()) {
            Toast.makeText(context, "Error: Work Order ID not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Set edit mode and store work order
        isEditingReview = true
        currentReviewId = null
        reviewCreatedAt = null
        currentWorkOrderForEdit = workOrder
        
        // Load existing review data
        loadExistingReview(woId, userID ?: "")
    }
    
    private fun loadExistingReview(woId: String, reviewerId: String) {
        lifecycleScope.launch {
            try {
                val propID = currentPropID ?: ""
                
                if (propID.isEmpty() || reviewerId.isEmpty()) {
                    Toast.makeText(context, "Missing user data. Please login again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Call get review API
                val response = RetrofitClient.apiService.getUserReview(
                    action = "get",
                    propID = propID,
                    woId = woId,
                    reviewerId = reviewerId
                )
                
                val success = response["success"] as? Boolean ?: false
                
                if (success) {
                    val reviews = response["data"] as? List<Map<String, Any>>? ?: emptyList()
                    
                    if (reviews.isNotEmpty()) {
                        val firstReview = reviews.first()
                        
                        // Get review data
                        currentReviewId = (firstReview["id"] as? Number)?.toInt()
                        val rating = (firstReview["rating"] as? Number)?.toInt() ?: 0
                        val comment = firstReview["comment"]?.toString() ?: ""
                        reviewCreatedAt = firstReview["created_at"]?.toString() // For edit, use created_at from review
                        
                        // Show Review Dialog with pre-filled data
                        val workOrder = currentWorkOrderForEdit
                        val woNumber = workOrder?.get("nour")?.toString() ?: ""
                        val woId = workOrder?.get("woId")?.toString() ?: ""
                        
                        reviewDialog = ReviewDialog(
                            context = requireContext(),
                            woNumber = woNumber,
                            createdAtDate = reviewCreatedAt,
                            initialRating = rating,
                            initialComment = comment,
                            onSave = { newRating, newComment ->
                                submitReview(woId, woNumber, newRating, newComment)
                            },
                            onCancel = {
                                reviewDialog?.dismiss()
                                reviewDialog = null
                                isEditingReview = false
                                currentReviewId = null
                                reviewCreatedAt = null
                                currentWorkOrderForEdit = null
                            }
                        )
                        
                        reviewDialog?.show()
                    } else {
                        Toast.makeText(context, "Review not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val message = response["message"]?.toString() ?: "Failed to load review"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading review: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun submitReview(woId: String, woNumber: String, rating: Int, comment: String) {
        lifecycleScope.launch {
            try {
                val propID = currentPropID ?: ""
                val reviewerId = userID ?: ""
                
                if (propID.isEmpty() || reviewerId.isEmpty()) {
                    reviewDialog?.setErrorMessage("Missing user data. Please login again.")
                    return@launch
                }
                
                reviewDialog?.setSubmitting(true)
                reviewDialog?.setErrorMessage(null)
                
                // Determine action (save or update)
                val response = if (isEditingReview && currentReviewId != null) {
                    // Update existing review
                    RetrofitClient.apiService.updateUserReview(
                        action = "update",
                        reviewId = currentReviewId!!,
                        propID = propID,
                        woId = woId,
                        reviewerId = reviewerId,
                        rating = rating,
                        comment = comment,
                        editReason = "Review updated by user"
                    )
                } else {
                    // Save new review
                    RetrofitClient.apiService.submitUserReview(
                        action = "save",
                        propID = propID,
                        woId = woId,
                        reviewerId = reviewerId,
                        rating = rating,
                        comment = comment
                    )
                }
                
                val success = response["success"] as? Boolean ?: false
                
                if (success) {
                    // Success - close dialog and show success message
                    reviewDialog?.dismiss()
                    reviewDialog = null
                    
                    val message = if (isEditingReview) {
                        "Review updated successfully"
                    } else {
                        "Review submitted successfully"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    
                    // Reset edit mode
                    isEditingReview = false
                    currentReviewId = null
                    reviewCreatedAt = null
                    currentWorkOrderForEdit = null
                    
                    // Refresh data to get updated is_review and review_rating
                    refreshData()
                } else {
                    val message = response["message"]?.toString() ?: "Failed to save review"
                    reviewDialog?.setErrorMessage(message)
                    reviewDialog?.setSubmitting(false)
                }
                
            } catch (e: Exception) {
                reviewDialog?.setErrorMessage("Error: ${e.message}")
                reviewDialog?.setSubmitting(false)
            }
        }
    }

    
    // Exactly like HomeFragment but for outbox
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                // Get user data sekaligus
                val propID = UserService.getCurrentPropID()
                val user = UserService.getCurrentUser()
                username = user?.username ?: user?.email
                userDept = user?.dept
                userID = user?.id
                
                // User data loaded
                
                currentPropID = propID ?: ""
                
                // Update toggle UI with user data
                updateToggleUI()
                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    // Load outbox work orders using iOS endpoint
                    loadData(reset = true)
                } else {
                    showEmptyState("Missing user data. Please login again.")
                }
            } catch (e: Exception) {
                showEmptyState("Error loading data: ${e.message}")
            } finally {
                isLoading = false
                isLoadingStatusCounts = false
                updateUI()
            }
        }
    }
    
    
    // Calculate status counts from loaded workOrders data (based on dept/user)
    private fun recomputeStatusCountsFromWorkOrders() {
        if (!isAdded) return
        isLoadingStatusCounts = true
        val localCounts = mutableMapOf<String, Int>()
        // Initialize all status options
        localCounts[""] = 0
        for (s in statusOptions) localCounts.putIfAbsent(s, 0)
        var total = 0
        for (wo in workOrders) {
            val raw = wo["status"]?.toString()?.lowercase() ?: ""
            val key = if (raw.isEmpty()) "new" else raw
            localCounts[key] = (localCounts[key] ?: 0) + 1
            total++
        }
        localCounts[""] = total
        // Ensure all keys exist
        for (s in statusOptions) localCounts.putIfAbsent(s, 0)
        statusCounts.clear()
        statusCounts.putAll(localCounts)
        isLoadingStatusCounts = false
    }

    // Get all Outbox pages to calculate accurate counter per status
    private suspend fun fetchOutboxStatusCountsAllPages(): Map<String, Int> {
        return try {
            val prop = currentPropID ?: return emptyMap()
            val user = username ?: return emptyMap()
            val dept = userDept ?: ""
            val localCounts = mutableMapOf<String, Int>()
            localCounts[""] = 0
            for (s in statusOptions) localCounts.putIfAbsent(s, 0)

            var pageNum = 1
            var total = 0
            while (true) {
                val pageData = RetrofitClient.apiService.searchWorkOrdersOut(
                    propID = prop,
                    orderBy = if (filterByDept) null else user,
                    dept = if (filterByDept) dept else null,
                    status = "",
                    page = pageNum
                )
                if (pageData.isEmpty()) break
                for (wo in pageData) {
                    val raw = wo["status"]?.toString()?.lowercase() ?: ""
                    val key = if (raw.isEmpty()) "new" else raw
                    localCounts[key] = (localCounts[key] ?: 0) + 1
                    total++
                }
                // If server pagination is 10/item, continue until less than 10
                if (pageData.size < 10) break
                pageNum++
            }
            localCounts[""] = total
            for (s in statusOptions) localCounts.putIfAbsent(s, 0)
            localCounts
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    // Optimization: Update status counts from API response
    private fun updateStatusCountsFromAPI(statusData: List<Map<String, Any>>) {
        if (!isAdded) return
        
        statusCounts.clear()
        var totalCount = 0
        
        for (status in statusData) {
            val statusName = status["status"]?.toString() ?: ""
            val count = (status["count"] as? Number)?.toInt() ?: 0
            
            if (statusName.isEmpty()) {
                statusCounts["new"] = count
            } else {
                statusCounts[statusName] = count
            }
            totalCount += count
        }
        
        statusCounts[""] = totalCount // Total for "All"
        
        // Ensure all required statuses exist
        for (status in statusOptions) {
            if (status.isNotEmpty() && !statusCounts.containsKey(status)) {
                statusCounts[status] = 0
            }
        }
        
        // Ensure 'new' exists
        if (!statusCounts.containsKey("new")) {
            statusCounts["new"] = 0
        }
    }
    
    private fun updateToggleUI() {
        // Update UI based on current user data and toggle state
        tvUsername.text = username ?: "User"
        tvDepartment.text = userDept ?: "Dept"
        
        // Update text colors based on toggle state like iOS
        if (filterByDept) {
            tvUsername.setTextColor(resources.getColor(R.color.gray, null))
            tvUsername.typeface = android.graphics.Typeface.DEFAULT
            tvDepartment.setTextColor(resources.getColor(R.color.primary, null))
            tvDepartment.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else {
            tvUsername.setTextColor(resources.getColor(R.color.primary, null))
            tvUsername.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tvDepartment.setTextColor(resources.getColor(R.color.gray, null))
            tvDepartment.typeface = android.graphics.Typeface.DEFAULT
        }
    }
    
    private fun loadData(reset: Boolean = false) {
        if (isLoading && !reset) return
        if (isLoadingMore && !reset) return
        
        if (reset) {
            isLoading = true
            isLoadingMore = false
        } else {
            isLoadingMore = true
        }
        
        lifecycleScope.launch {
            try {
                if (reset) {
                    workOrders.clear()
                    currentPage = 1
                    hasMoreData = true
                }
                
                if (currentPropID.isNullOrEmpty() || username.isNullOrEmpty()) {
                    isLoading = false
                    isLoadingMore = false
                    return@launch
                }
                
                // Use iOS endpoint: cari_wo_out.php like bacaWOOut.swift
                android.util.Log.d("OutboxFragment", "Loading data - filterByDept: $filterByDept, orderBy: ${if (filterByDept) null else username}, dept: ${if (filterByDept) userDept else null}, status: $selectedStatus, page: $currentPage")
                
                val workOrdersResult = RetrofitClient.apiService.searchWorkOrdersOut(
                    propID = currentPropID!!,
                    orderBy = if (filterByDept) null else username!!,
                    dept = if (filterByDept) userDept ?: "" else null,
                    status = selectedStatus,
                    page = currentPage
                )
                
                if (isAdded) {
                    if (reset) {
                        workOrders.clear()
                        workOrders.addAll(workOrdersResult)
                    } else {
                        workOrders.addAll(workOrdersResult)
                    }
                    
                    // Sort work orders by WO number (nour) in descending order (highest first)
                    sortWorkOrdersByWONumber(workOrders)
                    
                    if (workOrdersResult.size < 10) {
                        hasMoreData = false
                    } else {
                        currentPage++
                    }
                    
                    isLoading = false
                    isLoadingMore = false
                    // Update UI and recalculate status counter
                    updateUI()
                    recomputeStatusCountsFromWorkOrders()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    isLoading = false
                    isLoadingMore = false
                    Toast.makeText(context, "Failed to load outbox work orders: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun refreshData() {
        loadData(reset = true)
    }
    
    // Load more data for infinite scroll
    private fun loadMoreData() {
        loadData(reset = false)
    }
    
    // Search functionality removed - using iOS approach with filter only
    
    // Sort work orders by WO number (nour) in descending order (highest first)
    private fun sortWorkOrdersByWONumber(workOrdersList: MutableList<Map<String, Any>>) {
        workOrdersList.sortByDescending { workOrder ->
            val nour = workOrder["nour"]?.toString() ?: ""
            try {
                nour.toIntOrNull() ?: 0
            } catch (e: NumberFormatException) {
                0
            }
        }
    }
    
    // No search filtering - using iOS approach with server-side filtering only
    private fun getFilteredWorkOrders(): List<Map<String, Any>> {
        return workOrders
    }
    
    private fun onStatusChanged(status: String) {
        selectedStatus = status
        
        // Load data and update status counts
        loadData(reset = true)
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        val filteredData = getFilteredWorkOrders()
        
        if (filteredData.isEmpty()) {
            showEmptyState("") // Empty string like iOS
        } else {
            hideEmptyState()
            // Update RecyclerView adapter with filteredData
            workOrderAdapter.updateData(filteredData)
        }
    }
    
    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun hideEmptyState() {
        tvEmpty.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == EDIT_WO_REQUEST_CODE && resultCode == android.app.Activity.RESULT_OK) {
            // Work order was successfully updated, refresh the data
            refreshData()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup if needed
    }
}
