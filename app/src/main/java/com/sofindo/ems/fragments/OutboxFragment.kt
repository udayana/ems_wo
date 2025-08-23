package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SearchView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class OutboxFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: SearchView
    private lateinit var statusSpinner: Spinner
    private lateinit var tvEmpty: TextView
    
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var currentPage = 1
    private var hasMoreData = true
    private var isLoading = false
    private var selectedStatus = ""
    private var searchText = ""
    private var currentPropID: String? = null
    private var username: String? = null
    
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
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
        statusSpinner = view.findViewById(R.id.status_spinner)
        tvEmpty = view.findViewById(R.id.tv_empty)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        // TODO: Add adapter
        
        // Setup Spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statusOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        statusSpinner.adapter = adapter
    }
    
    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchText = query ?: ""
                loadOutboxData()
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                searchText = newText ?: ""
                loadOutboxData()
                return true
            }
        })
        
        statusSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStatus = statusOptions[position]
                loadOutboxData()
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedStatus = ""
            }
        })
    }
    
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                currentPropID = user?.propID
                username = user?.username ?: user?.email
                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    loadOutboxData()
                } else {
                    showEmptyState("User data not found")
                }
            } catch (e: Exception) {
                showEmptyState("Failed to load data: ${e.message}")
            }
        }
    }
    
    private fun loadOutboxData() {
        if (isLoading || currentPropID.isNullOrEmpty() || username.isNullOrEmpty()) return
        
        isLoading = true
        updateUI()
        
        lifecycleScope.launch {
            try {
                val result = RetrofitClient.apiService.getOutboxWorkOrders(
                    propID = currentPropID!!,
                    username = username!!,
                    status = selectedStatus,
                    page = currentPage,
                    search = searchText
                )
                
                if (isAdded) {
                    if (currentPage == 1) {
                        workOrders.clear()
                    }
                    
                    workOrders.addAll(result)
                    hasMoreData = result.size >= 10
                    currentPage++
                    
                    updateUI()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    showEmptyState("Failed to load outbox: ${e.message}")
                }
            } finally {
                isLoading = false
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun refreshData() {
        currentPage = 1
        hasMoreData = true
        loadOutboxData()
    }
    
    private fun updateUI() {
        if (workOrders.isEmpty()) {
            showEmptyState("No outbox items found")
        } else {
            hideEmptyState()
            // TODO: Update RecyclerView adapter
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
}

