package com.sofindo.ems.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sofindo.ems.R
import com.sofindo.ems.adapter.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.StatusCount
import com.sofindo.ems.models.WorkOrder
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class HomeFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var ivFilter: ImageView
    private lateinit var rvWorkOrders: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var llEmptyState: LinearLayout
    private lateinit var llLoading: LinearLayout
    
    private lateinit var workOrderAdapter: WorkOrderAdapter
    private val workOrders = mutableListOf<WorkOrder>()
    private val statusCounts = mutableMapOf<String, Int>()
    
    // State variables
    private var isLoading = false
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var searchText = ""
    private var selectedStatus = ""
    private var currentPropID = ""
    private var currentDept = "Engineering"
    
    // Search debounce
    private var searchJob: Job? = null
    
    // Status options for filter
    private val statusOptions = listOf(
        "" to "All",
        "new" to "NEW", 
        "received" to "RECEIVED",
        "on progress" to "ON PROGRESS",
        "pending" to "PENDING",
        "done" to "DONE"
    )

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
        setupRecyclerView()
        setupSwipeRefresh()
        setupListeners()
        loadInitialData()
    }

    private fun initViews(view: View) {
        etSearch = view.findViewById(R.id.et_search)
        ivFilter = view.findViewById(R.id.iv_filter)
        rvWorkOrders = view.findViewById(R.id.rv_work_orders)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        llEmptyState = view.findViewById(R.id.ll_empty_state)
        llLoading = view.findViewById(R.id.ll_loading)
    }

    private fun setupRecyclerView() {
        workOrderAdapter = WorkOrderAdapter(
            onItemClick = { workOrder ->
                // Navigate to work order detail
                Toast.makeText(context, "Navigate to detail: ${workOrder.nour}", Toast.LENGTH_SHORT).show()
            },
            onChangeStatusClick = { workOrder ->
                // Navigate to change status
                Toast.makeText(context, "Change status: ${workOrder.nour}", Toast.LENGTH_SHORT).show()
            }
        )
        
        rvWorkOrders.apply {
            adapter = workOrderAdapter
            layoutManager = LinearLayoutManager(context)
            
            // Infinite scroll
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    
                    if (!isLoadingMore && hasMoreData && searchText.isEmpty()) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3) {
                            loadMoreData()
                        }
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadWorkOrders(reset = true)
        }
        
        // Set colors for the refresh indicator
        swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.success
        )
    }

    private fun setupListeners() {
        // Search functionality
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newSearchText = s?.toString()?.trim() ?: ""
                if (newSearchText != searchText) {
                    searchText = newSearchText
                    searchJob?.cancel()
                    searchJob = lifecycleScope.launch {
                        delay(500) // Debounce 500ms
                        performSearch()
                    }
                }
            }
        })
        
        // Filter functionality
        ivFilter.setOnClickListener {
            showFilterMenu()
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                // Get actual user data from SharedPreferences
                currentPropID = UserService.getCurrentPropID(requireContext()) ?: ""
                currentDept = UserService.getCurrentDept(requireContext()) ?: "Engineering"
                
                if (currentPropID.isNotEmpty()) {
                    loadWorkOrders(reset = true)
                    loadStatusCounts()
                } else {
                    // Show error if no user data
                    Toast.makeText(context, "No user data found. Please login again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadWorkOrders(reset: Boolean = false) {
        if (isLoading && !reset) return
        
        if (reset) {
            isLoading = true
            currentPage = 1
            hasMoreData = true
            workOrders.clear()
            updateUI()
        }
        
        lifecycleScope.launch {
            try {
                val newWorkOrders = if (searchText.isNotEmpty()) {
                    RetrofitClient.apiService.searchWorkOrders(
                        propID = currentPropID,
                        searchQuery = searchText
                    )
                } else {
                    RetrofitClient.apiService.getWorkOrders(
                        propID = currentPropID,
                        woto = currentDept,
                        status = selectedStatus,
                        page = currentPage
                    )
                }
                
                // Debug: Log API response
                android.util.Log.d("HomeFragment", "API Response: ${newWorkOrders.size} work orders")
                if (newWorkOrders.isNotEmpty()) {
                    android.util.Log.d("HomeFragment", "First WO: ${newWorkOrders.first()}")
                }
                
                if (reset) {
                    workOrders.clear()
                }
                
                workOrders.addAll(newWorkOrders)
                hasMoreData = newWorkOrders.size >= 10
                
                if (!reset) {
                    currentPage++
                }
                
                workOrderAdapter.submitList(workOrders.toList())
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading work orders: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                isLoadingMore = false
                swipeRefreshLayout.isRefreshing = false
                updateUI()
            }
        }
    }

    private fun loadMoreData() {
        if (isLoadingMore || !hasMoreData || searchText.isNotEmpty()) return
        
        isLoadingMore = true
        currentPage++
        
        lifecycleScope.launch {
            try {
                val newWorkOrders = RetrofitClient.apiService.getWorkOrders(
                    propID = currentPropID,
                    woto = currentDept,
                    status = selectedStatus,
                    page = currentPage
                )
                
                workOrders.addAll(newWorkOrders)
                hasMoreData = newWorkOrders.size >= 10
                
                workOrderAdapter.submitList(workOrders.toList())
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading more data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingMore = false
            }
        }
    }

    private fun loadStatusCounts() {
        lifecycleScope.launch {
            try {
                val statusData = RetrofitClient.apiService.getAllStatuses(
                    propID = currentPropID,
                    woto = currentDept
                )
                
                updateStatusCounts(statusData)
                
            } catch (e: Exception) {
                // Handle error silently for status counts
            }
        }
    }

    private fun updateStatusCounts(statusData: List<StatusCount>) {
        statusCounts.clear()
        var totalCount = 0
        
        for (status in statusData) {
            val statusName = status.status ?: ""
            val count = status.count
            
            if (statusName.isEmpty()) {
                statusCounts["new"] = count
            } else {
                statusCounts[statusName] = count
            }
            totalCount += count
        }
        
        statusCounts[""] = totalCount // Total for "All"
        
        // Ensure all status options exist
        for ((status, _) in statusOptions) {
            if (status.isNotEmpty() && !statusCounts.containsKey(status)) {
                statusCounts[status] = 0
            }
        }
    }

    private fun performSearch() {
        // Filter existing data instead of making API call
        val filteredList = if (searchText.isEmpty()) {
            workOrders
        } else {
            workOrders.filter { workOrder ->
                val searchLower = searchText.lowercase()
                workOrder.nour?.lowercase()?.contains(searchLower) == true ||
                workOrder.job?.lowercase()?.contains(searchLower) == true ||
                workOrder.lokasi?.lowercase()?.contains(searchLower) == true ||
                workOrder.woto?.lowercase()?.contains(searchLower) == true
            }
        }
        
        workOrderAdapter.submitList(filteredList)
        updateUI()
    }

    private fun showFilterMenu() {
        val popup = PopupMenu(requireContext(), ivFilter)
        
        for ((status, displayName) in statusOptions) {
            val count = statusCounts[status] ?: 0
            val menuTitle = "$count → $displayName"
            popup.menu.add(menuTitle).apply {
                setOnMenuItemClickListener {
                    selectedStatus = status
                    searchText = ""
                    etSearch.setText("")
                    loadWorkOrders(reset = true)
                    true
                }
            }
        }
        
        popup.show()
    }

    private fun updateUI() {
        val currentList = workOrderAdapter.currentList
        
        when {
            isLoading -> {
                llLoading.visibility = View.VISIBLE
                llEmptyState.visibility = View.GONE
                rvWorkOrders.visibility = View.GONE
            }
            currentList.isEmpty() -> {
                llLoading.visibility = View.GONE
                llEmptyState.visibility = View.VISIBLE
                rvWorkOrders.visibility = View.GONE
                
                // Update empty state message
                val emptyMessage = if (searchText.isNotEmpty()) {
                    "No search results"
                } else {
                    "No work orders"
                }
                
                val emptyTextView = llEmptyState.findViewById<TextView>(R.id.tv_empty_message)
                emptyTextView?.text = emptyMessage
            }
            else -> {
                llLoading.visibility = View.GONE
                llEmptyState.visibility = View.GONE
                rvWorkOrders.visibility = View.VISIBLE
            }
        }
    }
}
