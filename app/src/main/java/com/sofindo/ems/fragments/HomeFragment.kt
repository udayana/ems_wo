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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.adapters.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Timer
import java.util.TimerTask

class HomeFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: EditText
    private lateinit var btnFilter: ImageButton
    private lateinit var tvEmpty: TextView
    
    // Exactly like Flutter _BacaWOViewState
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var isLoading = false
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var searchText = ""
    private var selectedStatus = ""
    private var currentPropID: String? = null
    private val statusCounts = mutableMapOf<String, Int>()
    private var isLoadingStatusCounts = true
    
    // Exactly like Flutter statusOptions
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
    // Search debounce timer (same as Flutter)
    private var searchDebounce: Timer? = null
    
    // RecyclerView adapter
    private lateinit var workOrderAdapter: WorkOrderAdapter
    
    // Activity result launcher for change status
    private val changeStatusLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Refresh data after status change
            refreshData()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
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
        
        // Initialize adapter with menu callbacks for Home (Detail and Follow Up only)
        workOrderAdapter = WorkOrderAdapter(
            onItemClick = { workOrder ->
                // Handle work order click (this won't be used since we're using card click for menu)
                onWorkOrderClick(workOrder)
            },
            onEditClick = { workOrder ->
                // Handle edit click (not used in Home)
                onEditWorkOrder(workOrder)
            },
            onDeleteClick = { workOrder ->
                // Handle delete click (not used in Home)
                onDeleteWorkOrder(workOrder)
            },
            onDetailClick = { workOrder ->
                // Handle detail click
                onDetailWorkOrder(workOrder)
            },
            onFollowUpClick = { workOrder ->
                // Handle follow up click
                onFollowUpWorkOrder(workOrder)
            },
            showSender = false, 
            replaceWotoWithOrderBy = true,
            isHomeFragment = true
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
        
        // Load status counts when filter is opened (same as Flutter)
        lifecycleScope.launch {
            if (currentPropID != null && currentPropID!!.isNotEmpty()) {
                val userDept = UserService.getCurrentDept()
                val finalDept = userDept ?: "Engineering"
                loadStatusCountsInBackground(finalDept)
            }
        }
        
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
        
        popup.show()
    }
    
    private fun onWorkOrderClick(workOrder: Map<String, Any>) {
        // TODO: Navigate to work order detail
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Clicked WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onEditWorkOrder(workOrder: Map<String, Any>) {
        // TODO: Navigate to edit work order
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Edit WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onDeleteWorkOrder(workOrder: Map<String, Any>) {
        // TODO: Show delete confirmation dialog
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Delete WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onDetailWorkOrder(workOrder: Map<String, Any>) {
        // Navigate to work order detail activity
        val intent = android.content.Intent(context, com.sofindo.ems.activities.WorkOrderDetailActivity::class.java)
        intent.putExtra("workOrder", workOrder as java.io.Serializable)
        startActivity(intent)
    }
    
    private fun onFollowUpWorkOrder(workOrder: Map<String, Any>) {
        // Navigate to change status activity
        val intent = android.content.Intent(context, com.sofindo.ems.activities.ChangeStatusWOActivity::class.java)
        intent.putExtra("workOrder", workOrder as java.io.Serializable)
        changeStatusLauncher.launch(intent)
    }
    
    // Exactly like Flutter _initializeData()
    private fun initializeData() {
        // Set loading state and show loading UI
        isLoading = true
        showLoadingState()
        
        // Try to get data synchronously first for faster initial load
        val propIDSync = UserService.getCurrentPropIDSync()
        val userDeptSync = UserService.getCurrentDeptSync()
        
        if (!propIDSync.isNullOrEmpty()) {
            currentPropID = propIDSync
            val finalDept = userDeptSync ?: "Engineering"
            
            // Start loading data immediately with sync data
            loadWorkOrdersParallel(finalDept)
        } else {
            // Fallback to async if sync data not available
            lifecycleScope.launch {
                try {
                    // Get propID and department at once (same as Flutter)
                    val propID = UserService.getCurrentPropID()
                    val userDept = UserService.getCurrentDept()
                    val finalDept = userDept ?: "Engineering"
                    
                    // User data loaded
                    
                    currentPropID = propID ?: ""
                    
                    if (!currentPropID.isNullOrEmpty()) {
                        // Only load work orders, counter filter is NOT loaded automatically
                        // Counter filter will be loaded when user clicks filter icon
                        loadWorkOrdersParallel(finalDept)
                    } else {
                        showEmptyState("No property ID found. Please login again.")
                        isLoading = false
                        isLoadingStatusCounts = false
                    }
                } catch (e: Exception) {
                    showEmptyState("Error loading data: ${e.message}")
                    isLoading = false
                    isLoadingStatusCounts = false
                }
            }
        }
    }
    
            // Load work orders only (without automatic status counts) - same as Flutter
    private fun loadWorkOrdersParallel(department: String) {
        lifecycleScope.launch {
            try {
                // Load work orders
                
                // Fetch directly from API without cache with timeout
                val workOrdersResult = withTimeout(15000) { // 15 seconds timeout
                    RetrofitClient.apiService.getWorkOrders(
                        propID = currentPropID!!,
                        woto = department,
                        status = selectedStatus,
                        page = 1
                    )
                }
                
                // API response received
                
                if (isAdded) {
                    workOrders.clear()
                    workOrders.addAll(workOrdersResult)
                    hasMoreData = workOrdersResult.size >= 10
                    currentPage = 2 // Set for next page
                    
                    // Work orders updated
                    
                    // Load counter filter after work orders are successfully loaded
                    // This ensures counter is available when filter is first clicked
                    loadStatusCountsInBackground(department)
                    
                    // Set loading to false and update UI after data is loaded
                    isLoading = false
                    updateUI()
                }
            } catch (e: Exception) {
                // Error loading work orders
                if (isAdded) {
                    val errorMessage = when {
                        e is kotlinx.coroutines.TimeoutCancellationException -> "Request timeout. Please check your connection."
                        else -> "Failed to load work orders: ${e.message}"
                    }
                    showEmptyState(errorMessage)
                    isLoading = false
                }
            }
        }
    }
    
            // Load status counts only when filter is clicked - same as Flutter
    private fun loadStatusCountsInBackground(department: String) {
        isLoadingStatusCounts = true
        
        lifecycleScope.launch {
            try {
                // Load from API get_all_statuses
                val statusData = RetrofitClient.apiService.getAllStatuses(
                    propID = currentPropID!!,
                    woto = department
                )
                
                // Update status counts from API
                updateStatusCountsFromAPI(statusData)
            } catch (e: Exception) {
                // API get_all_statuses failed
                // Set default values if API fails
                statusCounts.clear()
                statusCounts[""] = 0
                statusCounts["new"] = 0
                for (status in statusOptions) {
                    if (status.isNotEmpty()) {
                        statusCounts[status] = 0
                    }
                }
            } finally {
                if (isAdded) {
                    isLoadingStatusCounts = false
                }
            }
        }
    }
    
            // Optimization: Update status counts from API response - same as Flutter
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
            // Show loading state for reset operations
            showLoadingState()
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
                
                if (searchText.isEmpty()) {
                    if (currentPropID.isNullOrEmpty()) {
                        isLoading = false
                        isLoadingMore = false
                        showEmptyState("No property ID found. Please login again.")
                        return@launch
                    }
                    
                    val userDept = UserService.getCurrentDept()
                    val finalDept = userDept ?: "Engineering"
                    
                    val workOrdersResult = withTimeout(15000) { // 15 seconds timeout
                        RetrofitClient.apiService.getWorkOrders(
                            propID = currentPropID!!,
                            woto = finalDept,
                            status = selectedStatus,
                            page = currentPage
                        )
                    }
                    
                    if (isAdded) {
                        if (reset) {
                            workOrders.clear()
                            workOrders.addAll(workOrdersResult)
                        } else {
                            workOrders.addAll(workOrdersResult)
                        }
                        
                        if (workOrdersResult.size < 10) {
                            hasMoreData = false
                        } else {
                            currentPage++
                        }
                        
                        isLoading = false
                        isLoadingMore = false
                        updateUI()
                        
                        // Status counts are NOT updated automatically, only when filter is clicked
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    isLoading = false
                    isLoadingMore = false
                    val errorMessage = when {
                        e is kotlinx.coroutines.TimeoutCancellationException -> "Request timeout. Please check your connection."
                        else -> "Failed to load work orders: ${e.message}"
                    }
                    showEmptyState(errorMessage)
                }
            }
        }
    }
    
    private fun refreshData() {
        loadData(reset = true)
    }
    
            // Load more data for infinite scroll - exactly like Flutter
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
        
        // Load data and update status counts - exactly like Flutter
        loadData(reset = true)
        
        // After data is loaded, update status counts for new filter
        // Status counts will be loaded in background
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        val filteredData = getFilteredWorkOrders()
        
        if (isLoading) {
            // Still loading, keep showing loading state
            showLoadingState()
        } else if (filteredData.isEmpty()) {
            if (searchText.isNotEmpty()) {
                showEmptyState("No search results")
            } else {
                showEmptyState("No work orders")
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
    
    private fun showLoadingState() {
        tvEmpty.text = "Loading work orders..."
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    

    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup search debounce timer
        searchDebounce?.cancel()
    }
}

