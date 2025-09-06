package com.sofindo.ems

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.sofindo.ems.activities.WorkOrderDetailActivity
import com.sofindo.ems.auth.LoginActivity
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.fragments.HomeFragment
import com.sofindo.ems.fragments.OutboxFragment
import com.sofindo.ems.fragments.TambahWOFragment
import com.sofindo.ems.fragments.MaintenanceFragment
import com.sofindo.ems.fragments.ProfileFragment
import com.sofindo.ems.fragments.EditProfileFragment
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var customBottomNavigation: View
    private lateinit var tabHome: View
    private lateinit var tabOut: View
    private lateinit var tabAdd: View
    private lateinit var tabMaint: View
    private lateinit var tabProfile: View
    private var currentFragment: Fragment? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Handle deep link if app was opened via deep link
        handleDeepLink(intent)
        
        // Check if user is logged in
        lifecycleScope.launch {
            val currentUser = UserService.getCurrentUser()
            val currentPropID = UserService.getCurrentPropID()
            
            // Check user login status
            
            if (currentUser == null || currentPropID.isNullOrEmpty()) {
                // User not logged in, redirect to login
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@launch
            }
            
            // User is logged in, setup UI
            setupBottomNavigation()
            
            // Set default fragment to Home and activate Home tab
            if (savedInstanceState == null) {
                loadFragment(HomeFragment())
                updateTabSelection(0) // Ensure Home tab is active
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            android.util.Log.d("DeepLink", "Received URI: $uri")
            android.util.Log.d("DeepLink", "Scheme: ${uri.scheme}, Host: ${uri.host}, Path: ${uri.lastPathSegment}")
            
            if (uri.scheme == "emswo" && uri.host == "workorder") {
                // Extract work order ID from deep link
                val woId = uri.lastPathSegment
                android.util.Log.d("DeepLink", "Extracted WO ID: $woId")
                
                if (!woId.isNullOrEmpty()) {
                    // Navigate to work order detail
                    navigateToWorkOrderDetail(woId)
                } else {
                    android.util.Log.e("DeepLink", "WO ID is null or empty")
                    android.widget.Toast.makeText(this, "Invalid Work Order ID", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.d("DeepLink", "URI doesn't match expected pattern")
            }
        } ?: run {
            android.util.Log.d("DeepLink", "No URI data in intent")
        }
    }
    
    private fun navigateToWorkOrderDetail(woId: String) {
        android.util.Log.d("DeepLink", "Starting navigation to WO: $woId")
        
        lifecycleScope.launch {
            try {
                // Show loading message
                android.widget.Toast.makeText(this@MainActivity, "Loading Work Order: $woId", android.widget.Toast.LENGTH_SHORT).show()
                android.util.Log.d("DeepLink", "Making API call to getWorkOrderById: $woId")
                
                // Fetch work order data from API
                val workOrderData = RetrofitClient.apiService.getWorkOrderById(woId)
                android.util.Log.d("DeepLink", "API response received: ${workOrderData.size} fields")
                
                // Check if work order exists
                if (workOrderData.isNotEmpty() && workOrderData.containsKey("nour")) {
                    android.util.Log.d("DeepLink", "Work order found, launching WorkOrderDetailActivity")
                    // Launch WorkOrderDetailActivity with the data
                    val intent = Intent(this@MainActivity, WorkOrderDetailActivity::class.java)
                    intent.putExtra("workOrder", workOrderData as java.io.Serializable)
                    startActivity(intent)
                } else {
                    android.util.Log.e("DeepLink", "Work order not found or invalid data")
                    android.widget.Toast.makeText(this@MainActivity, "Work Order not found: $woId", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("DeepLink", "Error loading work order", e)
                android.widget.Toast.makeText(this@MainActivity, "Error loading Work Order: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupBottomNavigation() {
        customBottomNavigation = findViewById(R.id.custom_bottom_navigation)
        tabHome = customBottomNavigation.findViewById(R.id.tab_home)
        tabOut = customBottomNavigation.findViewById(R.id.tab_out)
        tabAdd = customBottomNavigation.findViewById(R.id.tab_add)
        tabMaint = customBottomNavigation.findViewById(R.id.tab_maint)
        tabProfile = customBottomNavigation.findViewById(R.id.tab_profile)
        
        // Set click listeners
        tabHome.setOnClickListener {
            loadFragment(HomeFragment())
            updateTabSelection(0)
        }
        
        tabOut.setOnClickListener {
            loadFragment(OutboxFragment())
            updateTabSelection(1)
        }
        
        tabAdd.setOnClickListener {
            loadFragment(TambahWOFragment())
            updateTabSelection(2)
        }
        
        tabMaint.setOnClickListener {
            loadFragment(MaintenanceFragment())
            updateTabSelection(3)
        }
        
        tabProfile.setOnClickListener {
            loadFragment(ProfileFragment())
            updateTabSelection(4)
        }
        
        // Set initial selection
        updateTabSelection(0)
    }
    
    private fun updateTabSelection(selectedIndex: Int) {
        android.util.Log.d("TabSelection", "updateTabSelection called with index: $selectedIndex")
        
        val tabs = listOf(tabHome, tabOut, tabAdd, tabMaint, tabProfile)
        val iconIds = listOf(R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5)
        val textIds = listOf(R.id.text1, R.id.text2, R.id.text3, R.id.text4, R.id.text5)
        
        // Define colors
        val activeColor = android.graphics.Color.WHITE
        val inactiveColor = android.graphics.Color.argb(128, 255, 255, 255) // 50% opacity white
        
        tabs.forEachIndexed { index, tab ->
            val icon = tab.findViewById<ImageView>(iconIds[index])
            val text = tab.findViewById<TextView>(textIds[index])
            
            android.util.Log.d("TabSelection", "Tab $index - icon: ${icon != null}, text: ${text != null}")
            
            if (index == selectedIndex) {
                // Active tab - bright white
                icon?.setColorFilter(activeColor, android.graphics.PorterDuff.Mode.SRC_IN)
                text?.setTextColor(activeColor)
                android.util.Log.d("TabSelection", "Tab $index set to ACTIVE - Color: $activeColor")
            } else {
                // Inactive tab - dimmed white with 50% opacity
                icon?.setColorFilter(inactiveColor, android.graphics.PorterDuff.Mode.SRC_IN)
                text?.setTextColor(inactiveColor)
                android.util.Log.d("TabSelection", "Tab $index set to INACTIVE - Color: $inactiveColor")
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        
        // Update app bar title and menu
        updateAppBar()
    }
    
    private fun updateAppBar() {
        val title = when (currentFragment) {
            is HomeFragment -> "Home"
            is OutboxFragment -> "Outbox"
            is TambahWOFragment -> "Add Work Order"
            is MaintenanceFragment -> "Maintenance"
            is ProfileFragment -> "Profile"
            is EditProfileFragment -> "Edit Profile"
            else -> "EMS WO"
        }
        
        supportActionBar?.title = title
        invalidateOptionsMenu()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        
        when (currentFragment) {
            is EditProfileFragment -> {
                menuInflater.inflate(R.menu.edit_profile_menu, menu)
                return true
            }
        }
        
        return super.onCreateOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                if (currentFragment is EditProfileFragment) {
                    (currentFragment as EditProfileFragment).saveProfile()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    fun switchToTab(tabIndex: Int) {
        when (tabIndex) {
            0 -> {
                loadFragment(HomeFragment())
                updateTabSelection(0)
            }
            1 -> {
                loadFragment(OutboxFragment())
                updateTabSelection(1)
            }
            2 -> {
                loadFragment(TambahWOFragment())
                updateTabSelection(2)
            }
            3 -> {
                loadFragment(MaintenanceFragment())
                updateTabSelection(3)
            }
            4 -> {
                loadFragment(ProfileFragment())
                updateTabSelection(4)
            }
        }
    }
    
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            updateAppBar()
        } else {
            super.onBackPressed()
        }
    }
}
