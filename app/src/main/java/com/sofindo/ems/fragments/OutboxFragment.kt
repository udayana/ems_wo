package com.sofindo.ems.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class OutboxFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: EditText
    private lateinit var btnFilter: ImageButton
    private lateinit var tvEmpty: TextView

    
    // Exactly like HomeFragment
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var isLoading = true
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var searchText = ""
    private var selectedStatus = ""
    private var currentPropID: String? = null
    private var username: String? = null
    private var userDept: String? = null
    private val statusCounts = mutableMapOf<String, Int>()
    private var isLoadingStatusCounts = true
    
    // Exactly like Flutter statusOptions
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
    // Search debounce timer (same as Flutter)
    private var searchDebounce: Timer? = null
    
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
        searchView = view.findViewById(R.id.search_view)
        btnFilter = view.findViewById(R.id.btn_filter)
        tvEmpty = view.findViewById(R.id.tv_empty)

        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
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
            showSender = false,
            replaceWotoWithOrderBy = false,
            isHomeFragment = false
        )
        
        recyclerView.adapter = workOrderAdapter
        
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
        
        searchView.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                onSearchChanged(s?.toString() ?: "")
            }
        })
        
        btnFilter.setOnClickListener {
            showFilterPopup()
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
    

    
    // Exactly like HomeFragment but for outbox
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                // Get user data sekaligus
                val propID = UserService.getCurrentPropID()
                val user = UserService.getCurrentUser()
                username = user?.username ?: user?.email
                userDept = user?.dept
                
                // User data loaded
                
                currentPropID = propID ?: ""
                

                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    // Load outbox work orders
                    loadOutboxWorkOrdersParallel()
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
    
            // Load all work orders - using work orders API
    private fun loadOutboxWorkOrdersParallel() {
        lifecycleScope.launch {
            try {
                // Load outbox work orders
                
                // Fetch from work orders API (all work orders)
                val workOrdersResult = RetrofitClient.apiService.getWorkOrders(
                    propID = currentPropID!!,
                    woto = null, // null to display all departments
                    status = selectedStatus,
                    page = 1
                )
                
                // API response received
                
                if (isAdded) {
                    workOrders.clear()
                    workOrders.addAll(workOrdersResult)
                    
                    // Sort work orders by WO number (nour) in descending order (highest first)
                    sortWorkOrdersByWONumber(workOrders)
                    
                    hasMoreData = workOrdersResult.size >= 10
                    currentPage = 2 // Set for next page
                    
                    // Work orders updated and sorted
                    
                    // Update UI and recalculate status counter
                    updateUI()
                    recomputeStatusCountsFromWorkOrders()
                }
            } catch (e: Exception) {
                // Error loading work orders
                if (isAdded) {
                    showEmptyState("Failed to load outbox work orders: ${e.message}")
                }
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
                val pageData = RetrofitClient.apiService.getWorkOrdersOut(
                    propID = prop,
                    orderBy = user,
                    status = "",
                    page = pageNum,
                    userDept = dept
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
                
                // Load from outbox API
                val workOrdersResult = RetrofitClient.apiService.getWorkOrdersOut(
                    propID = currentPropID!!,
                    orderBy = username!!,
                    status = selectedStatus,
                    page = currentPage,
                    userDept = userDept ?: ""
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
    
    private fun refreshData() {
        loadData(reset = true)
    }
    
    // Load more data for infinite scroll
    private fun loadMoreData() {
        loadData(reset = false)
    }
    
    // Search with debounce - same as Flutter onSearchChanged
    private fun onSearchChanged(value: String) {
        searchText = value
        
        // Cancel previous timer
        searchDebounce?.cancel()
        
        // Set new timer for debounce
        searchDebounce = Timer()
        searchDebounce?.schedule(object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    // Just update the UI with filtered results
                    updateUI()
                }
            }
        }, 500) // 500ms debounce
    }
    
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
    
    // Get filtered work orders based on search text - performance optimization (same as Flutter)
    private fun getFilteredWorkOrders(): List<Map<String, Any>> {
        if (searchText.isEmpty()) {
            return workOrders
        }
        
        val searchLower = searchText.lowercase()
        return workOrders.filter { workOrder ->
            workOrder["nour"]?.toString()?.lowercase()?.contains(searchLower) == true ||
            workOrder["job"]?.toString()?.lowercase()?.contains(searchLower) == true ||
            workOrder["lokasi"]?.toString()?.lowercase()?.contains(searchLower) == true ||
            workOrder["woto"]?.toString()?.lowercase()?.contains(searchLower) == true
        }
    }
    
    private fun onStatusChanged(status: String) {
        selectedStatus = status
        searchText = ""
        searchView.setText("")
        
        // Load data and update status counts
        loadData(reset = true)
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        val filteredData = getFilteredWorkOrders()
        
        if (filteredData.isEmpty()) {
            if (searchText.isNotEmpty()) {
                showEmptyState("No search results")
            } else {
                showEmptyState("") // Empty string instead of "No outbox work orders"
            }
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
        // Cleanup search debounce timer
        searchDebounce?.cancel()
    }
}
