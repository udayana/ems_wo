package com.sofindo.ems.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.MainActivity
import com.sofindo.ems.adapters.ProjectAdapter
import com.sofindo.ems.fragments.ProfileFragment
import com.sofindo.ems.models.Project
import com.sofindo.ems.services.UserService
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.RequestBody.Companion.toRequestBody

class ProjectViewActivity : AppCompatActivity() {

    private lateinit var toolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var btnAddProject: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var btnProfile: ImageButton
    private lateinit var rvProjects: RecyclerView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var emptyContainer: LinearLayout
    private lateinit var btnRetryEmpty: Button
    private lateinit var loadingMoreContainer: LinearLayout
    private lateinit var bottomNavigation: View
    private lateinit var nestedScrollView: androidx.core.widget.NestedScrollView

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var httpClient: OkHttpClient

    private var projects: MutableList<Project> = mutableListOf()
    private var filteredProjects: MutableList<Project> = mutableListOf()
    private lateinit var adapter: ProjectAdapter

    private var propID: String = ""
    private var userID: String = ""
    private var nama: String = ""

    private var isLoading = false
    private var page = 1
    private var searchText = ""
    private var isDeleting = false
    private var projectToDelete: Project? = null

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_view)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupListeners()
        setupBottomNavigation()
        
        // Load user data using UserService (like HomeFragment)
        loadUserData()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        backButton = toolbar.findViewById(R.id.back_button)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        btnAddProject = toolbar.findViewById(R.id.btn_add_project)
        etSearch = findViewById(R.id.et_search)
        btnProfile = findViewById(R.id.btn_profile)
        rvProjects = findViewById(R.id.rv_projects)
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        tvErrorMessage = findViewById(R.id.tv_error_message)
        btnRetry = findViewById(R.id.btn_retry)
        emptyContainer = findViewById(R.id.empty_container)
        btnRetryEmpty = findViewById(R.id.btn_retry_empty)
        loadingMoreContainer = findViewById(R.id.loading_more_container)
        nestedScrollView = findViewById(R.id.nested_scroll_view)

        sharedPreferences = getSharedPreferences("ems_user_prefs", Context.MODE_PRIVATE)
        httpClient = OkHttpClient()
        bottomNavigation = findViewById(R.id.custom_bottom_navigation)

        toolbarTitle.text = "Projects"
    }

    private fun loadUserData() {
        // Ensure UserService is initialized (should be done in App.kt, but just in case)
        try {
            // Try to access prefs to check if initialized
            UserService.getCurrentPropIDSync()
        } catch (e: UninitializedPropertyAccessException) {
            // UserService not initialized, initialize it
            UserService.init(this)
        } catch (e: Exception) {
            // Other error, try to init anyway
            UserService.init(this)
        }
        
        // Try to get propID synchronously first (faster) - exactly like HomeFragment
        val propIDSync = UserService.getCurrentPropIDSync()
        
        if (!propIDSync.isNullOrEmpty()) {
            propID = propIDSync
            // Get other user data from SharedPreferences
            userID = sharedPreferences.getString("user_id", "") 
                ?: sharedPreferences.getString("userID", "") 
                ?: ""
            nama = sharedPreferences.getString("full_name", "") 
                ?: sharedPreferences.getString("nama", "") 
                ?: ""
            
            android.util.Log.d("ProjectViewActivity", "propID loaded: $propID")
            
            // Load data immediately
            loadData(reset = true)
        } else {
            // Fallback: try from SharedPreferences directly with both possible keys
            propID = sharedPreferences.getString("prop_id", "") 
                ?: sharedPreferences.getString("propID", "") 
                ?: ""
            
            if (!propID.isEmpty()) {
                android.util.Log.d("ProjectViewActivity", "propID loaded from SharedPreferences: $propID")
                loadData(reset = true)
            } else {
                // Fallback to async if sync data not available - exactly like HomeFragment
                lifecycleScope.launch {
                    try {
                        val propIDAsync = UserService.getCurrentPropID()
                        val user = UserService.getCurrentUser()
                        
                        propID = propIDAsync ?: ""
                        userID = user?.id ?: ""
                        nama = user?.fullName ?: user?.username ?: ""
                        
                        android.util.Log.d("ProjectViewActivity", "propID loaded async: $propID")
                        
                        if (!propID.isEmpty()) {
                            loadData(reset = true)
                        } else {
                            showError("propID tidak ditemukan. Silakan login ulang.")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProjectViewActivity", "Error loading user data", e)
                        showError("Error loading data: ${e.message}")
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProjectAdapter(filteredProjects) { project ->
            // Navigate to detail
            // Use same logic as iOS: project.id.isEmpty ? (project.projectId ?? "") : project.id
            // This ensures we use the correct identifier for the API
            val projectId = if (project.id.isEmpty()) {
                project.projectId ?: ""
            } else {
                project.id
            }
            
            if (projectId.isEmpty()) {
                android.util.Log.e("ProjectViewActivity", "Project ID is empty for project: ${project.projectName}")
                showError("Project ID tidak ditemukan")
                return@ProjectAdapter
            }
            
            android.util.Log.d("ProjectViewActivity", "Opening project detail - id: ${project.id}, projectId: ${project.projectId}, final: $projectId, name: ${project.projectName}")
            
            val intent = Intent(this, ProjectDetailActivity::class.java)
            intent.putExtra("projectId", projectId)
            intent.putExtra("propID", propID)
            startActivity(intent)
        }

        rvProjects.layoutManager = LinearLayoutManager(this)
        rvProjects.adapter = adapter

        // Swipe to delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION && position < filteredProjects.size) {
                    projectToDelete = filteredProjects[position]
                    showDeleteConfirmDialog(projectToDelete!!)
                    adapter.notifyItemChanged(position) // Restore item position
                }
            }

            override fun onChildDraw(c: android.graphics.Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val background = android.graphics.drawable.ColorDrawable()
                    background.color = ContextCompat.getColor(this@ProjectViewActivity, R.color.red)
                    background.setBounds(
                        if (dX > 0) itemView.left else (itemView.right + dX.toInt()),
                        itemView.top,
                        if (dX > 0) (itemView.left + dX.toInt()) else itemView.right,
                        itemView.bottom
                    )
                    background.draw(c)

                    val deleteIcon = ContextCompat.getDrawable(this@ProjectViewActivity, android.R.drawable.ic_menu_delete)
                    val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconBottom = iconTop + deleteIcon.intrinsicHeight

                    if (dX > 0) {
                        val iconLeft = itemView.left + iconMargin
                        val iconRight = iconLeft + deleteIcon.intrinsicWidth
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    } else {
                        val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    }

                    deleteIcon.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvProjects)

        // Pagination - load more when scrolled to bottom
        rvProjects.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null) {
                    val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    val totalItemCount = layoutManager.itemCount

                    if (!isLoading && searchText.isEmpty() && lastVisiblePosition >= totalItemCount - 1) {
                        loadData(reset = false)
                    }
                }
            }
        })
    }

    private fun setupSearch() {
        // Prevent auto-scroll when EditText gets focus
        etSearch.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Prevent NestedScrollView from auto-scrolling by keeping scroll at top
                nestedScrollView.post {
                    nestedScrollView.scrollTo(0, 0)
                }
            }
        }
        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val newSearchText = s?.toString() ?: ""

                // Cancel previous search
                searchRunnable?.let { handler.removeCallbacks(it) }

                // Debounce search - wait 300ms after user stops typing
                searchRunnable = Runnable {
                    searchText = newSearchText
                    // Reload data when search text changes (server handles search)
                    loadData(reset = true)
                }
                handler.postDelayed(searchRunnable!!, 300)
            }
        })
    }

    private fun setupListeners() {
        btnProfile.setOnClickListener {
            // Navigate to profile fragment
            val profileFragment = ProfileFragment()
            // Since this is an Activity, we might need to navigate differently
            // For now, just show a message or navigate to MainActivity with profile tab
            showProfileDialog()
        }

        btnRetry.setOnClickListener {
            loadData(reset = true)
        }

        btnRetryEmpty.setOnClickListener {
            loadData(reset = true)
        }

        btnAddProject.setOnClickListener {
            // Navigate to create project
            val intent = Intent(this, CreateProjectActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupBottomNavigation() {
        val tabHome = bottomNavigation.findViewById<View>(R.id.tab_home)
        val tabOut = bottomNavigation.findViewById<View>(R.id.tab_out)
        val tabAdd = bottomNavigation.findViewById<View>(R.id.tab_add)
        val tabMaint = bottomNavigation.findViewById<View>(R.id.tab_maint)
        val tabProfile = bottomNavigation.findViewById<View>(R.id.tab_profile)
        
        // Set click listeners for bottom navigation
        tabHome.setOnClickListener {
            // Navigate to MainActivity with Home tab
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        tabOut.setOnClickListener {
            // Already on Project page, do nothing or highlight
            // Could add visual feedback
        }
        
        tabAdd.setOnClickListener {
            // Navigate to MainActivity with Add tab
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("selected_tab", 2) // Add tab index
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        tabMaint.setOnClickListener {
            // Navigate to MainActivity with Maintenance tab
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("selected_tab", 3) // Maintenance tab index
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        tabProfile.setOnClickListener {
            // Navigate to MainActivity with Profile/Utility tab
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("selected_tab", 4) // Profile tab index
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        // Highlight current tab (Project tab)
        updateBottomNavSelection(1) // Project is at index 1 (replacing Out)
    }
    
    private fun updateBottomNavSelection(selectedIndex: Int) {
        val tabs = listOf(
            bottomNavigation.findViewById<View>(R.id.tab_home),
            bottomNavigation.findViewById<View>(R.id.tab_out),
            bottomNavigation.findViewById<View>(R.id.tab_add),
            bottomNavigation.findViewById<View>(R.id.tab_maint),
            bottomNavigation.findViewById<View>(R.id.tab_profile)
        )
        val iconIds = listOf(R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5)
        val textIds = listOf(R.id.text1, R.id.text2, R.id.text3, R.id.text4, R.id.text5)
        
        // Define colors
        val activeColor = android.graphics.Color.WHITE
        val inactiveColor = android.graphics.Color.argb(128, 255, 255, 255) // 50% opacity white
        
        tabs.forEachIndexed { index, tab ->
            val icon = tab.findViewById<android.widget.ImageView>(iconIds[index])
            val text = tab.findViewById<android.widget.TextView>(textIds[index])
            
            if (index == selectedIndex) {
                // Active tab - bright white
                icon?.setColorFilter(activeColor, android.graphics.PorterDuff.Mode.SRC_IN)
                text?.setTextColor(activeColor)
            } else {
                // Inactive tab - dimmed white with 50% opacity
                icon?.setColorFilter(inactiveColor, android.graphics.PorterDuff.Mode.SRC_IN)
                text?.setTextColor(inactiveColor)
            }
        }
    }

    private fun showProfileDialog() {
        // Simple dialog to show profile or navigate
        AlertDialog.Builder(this)
            .setTitle("Profile")
            .setMessage("Fitur profile akan membuka halaman profile")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadData(reset: Boolean) {
        if (isLoading) return
        if (propID.isEmpty()) return

        isLoading = true

        if (reset) {
            page = 1
            projects.clear()
            showLoading(true)
            hideError()
            hideEmpty()
        } else {
            showLoadingMore(true)
        }

        // Build URL with query parameters
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("emshotels.net")
            .addPathSegment("apiKu")
            .addPathSegment("project-read.php")
            .addQueryParameter("propID", propID)
            .addQueryParameter("page", page.toString())
        
        // Add search parameter if not empty
        if (searchText.isNotEmpty()) {
            urlBuilder.addQueryParameter("search", searchText)
        }
        
        val url = urlBuilder.build()
        android.util.Log.d("ProjectViewActivity", "Loading data - URL: $url, searchText: '$searchText'")

        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    isLoading = false
                    showLoading(false)
                    showLoadingMore(false)
                    showError("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    handler.post {
                        isLoading = false
                        showLoading(false)
                        showLoadingMore(false)

                        if (response.isSuccessful) {
                            try {
                                parseProjectsData(responseBody, reset)
                            } catch (e: Exception) {
                                android.util.Log.e("ProjectViewActivity", "Error parsing data", e)
                                showError("Failed to parse data: ${e.message}")
                            }
                        } else {
                            showError("Server error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        isLoading = false
                        showLoading(false)
                        showLoadingMore(false)
                        android.util.Log.e("ProjectViewActivity", "Error reading response", e)
                        showError("Error reading response: ${e.message}")
                    }
                }
            }
        })
    }

    private fun parseProjectsData(jsonString: String, reset: Boolean) {
        try {
            android.util.Log.d("ProjectViewActivity", "Parsing response: $jsonString")
            val json = JSONObject(jsonString)
            val status = json.optString("status", "")

            if (status == "success") {
                val dataArray = json.optJSONArray("data")
                android.util.Log.d("ProjectViewActivity", "Data array length: ${dataArray?.length() ?: 0}")
                
                if (dataArray != null && dataArray.length() > 0) {
                    val newProjects = mutableListOf<Project>()
                    for (i in 0 until dataArray.length()) {
                        val projectJson = dataArray.optJSONObject(i)
                        if (projectJson != null) {
                            val project = Project.fromJson(projectJson)
                            android.util.Log.d("ProjectViewActivity", "Parsed project - id: ${project.id}, projectId: ${project.projectId}, name: ${project.projectName}")
                            newProjects.add(project)
                        }
                    }

                    if (reset) {
                        projects.clear()
                        projects.addAll(newProjects)
                        page = 2
                    } else {
                        projects.addAll(newProjects)
                        page++
                    }

                    // Server already filtered, so use projects directly
                    filteredProjects.clear()
                    filteredProjects.addAll(projects)
                    android.util.Log.d("ProjectViewActivity", "Total projects: ${projects.size}, filtered: ${filteredProjects.size}")
                    updateUI()
                } else {
                    // No data returned from server
                    android.util.Log.d("ProjectViewActivity", "No data returned from server")
                    if (reset) {
                        projects.clear()
                        filteredProjects.clear()
                        updateUI()
                    }
                }
            } else {
                val message = json.optString("message", "Failed to load projects")
                android.util.Log.e("ProjectViewActivity", "Error from server: $message")
                showError(message)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProjectViewActivity", "Error parsing response", e)
            showError("Failed to parse response: ${e.message}")
        }
    }

    private fun filterProjects() {
        filteredProjects.clear()
        if (searchText.isEmpty()) {
            filteredProjects.addAll(projects)
        } else {
            val searchLower = searchText.lowercase()
            filteredProjects.addAll(projects.filter { project ->
                project.projectName.lowercase().contains(searchLower) ||
                (project.lokasi?.lowercase()?.contains(searchLower) == true) ||
                (project.category?.lowercase()?.contains(searchLower) == true)
            })
        }
    }

    private fun updateUI() {
        adapter.notifyDataSetChanged()

        if (filteredProjects.isEmpty() && !isLoading) {
            showEmpty()
        } else {
            hideEmpty()
            rvProjects.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        rvProjects.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showLoadingMore(show: Boolean) {
        loadingMoreContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        tvErrorMessage.text = message
        errorContainer.visibility = View.VISIBLE
        rvProjects.visibility = View.GONE
        emptyContainer.visibility = View.GONE
    }

    private fun hideError() {
        errorContainer.visibility = View.GONE
    }

    private fun showEmpty() {
        emptyContainer.visibility = View.VISIBLE
        rvProjects.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }

    private fun hideEmpty() {
        emptyContainer.visibility = View.GONE
        rvProjects.visibility = View.VISIBLE
    }

    private fun showDeleteConfirmDialog(project: Project) {
        AlertDialog.Builder(this)
            .setTitle("Delete Project")
            .setMessage("Are you sure you want to delete project '${project.projectName}'? This action cannot be undone.")
            .setPositiveButton("Delete", null)
            .setNegativeButton("Cancel") { _, _ ->
                projectToDelete = null
            }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        deleteProject(project)
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun deleteProject(project: Project) {
        if (isDeleting) return

        val projectId = if (project.id.isEmpty()) (project.projectId ?: "") else project.id
        if (projectId.isEmpty()) {
            showError("Project ID not found")
            return
        }

        isDeleting = true

        val payload = JSONObject().apply {
            put("projectId", projectId)
            put("propID", propID)
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://emshotels.net/apiKu/delete_project.php")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    isDeleting = false
                    showError("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    handler.post {
                        isDeleting = false

                        if (response.isSuccessful) {
                            try {
                                val json = JSONObject(responseBody)
                                val status = json.optString("status", "")
                                if (status == "success") {
                                    // Remove project from list
                                    projects.removeAll { it.id == project.id }
                                    filterProjects()
                                    updateUI()
                                    projectToDelete = null
                                } else {
                                    val message = json.optString("message", "Failed to delete project")
                                    showError(message)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ProjectViewActivity", "Error parsing delete response", e)
                                showError("Failed to parse response: ${e.message}")
                            }
                        } else {
                            showError("Server error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        isDeleting = false
                        android.util.Log.e("ProjectViewActivity", "Error reading delete response", e)
                        showError("Error reading response: ${e.message}")
                    }
                }
            }
        })
    }
}


