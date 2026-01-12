package com.sofindo.ems

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging
import com.sofindo.ems.auth.LoginActivity
import com.sofindo.ems.fragments.HomeFragment
import com.sofindo.ems.fragments.OutboxFragment
import com.sofindo.ems.fragments.TambahWOFragment
import com.sofindo.ems.fragments.MaintenanceFragment
import com.sofindo.ems.fragments.ProfileFragment
import com.sofindo.ems.fragments.UtilityFragment
import com.sofindo.ems.fragments.EditProfileFragment
import com.sofindo.ems.services.UserService
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_WO_ID = "extra_target_wo_id"
        const val ACTION_OPEN_WORK_ORDER = "OPEN_WORKORDER"
    }

    lateinit var customBottomNavigation: View
        private set
    private lateinit var tabHome: View
    private lateinit var tabOut: View
    private lateinit var tabAdd: View
    private lateinit var tabMaint: View
    private lateinit var tabProfile: View
    private var currentFragment: Fragment? = null
    var homeFragment: HomeFragment? = null
        private set
    private var pendingNavigateWoId: String? = null
    private var isUiReady: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ================================
        // PAKSA MODE NORMAL (BUKAN EDGE TO EDGE)
        // ================================
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContentView(R.layout.activity_main)

        // ❌ HAPUS BARIS INI
        // setupEdgeToEdge()

        // ❌ HAPUS BARIS INI
        // findViewById<View>(R.id.fragment_container).applyTopInsets()

        // ================================
        // LANJUTKAN SEPERTI BIASA
        // ================================

        handleDeepLink(intent)
        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            val currentUser = UserService.getCurrentUser()
            val currentPropID = UserService.getCurrentPropID()

            if (currentUser == null || currentPropID.isNullOrEmpty()) {
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@launch
            }

            setupBottomNavigation()

            val selectedTabIndex = intent.getIntExtra("selected_tab", -1)

            if (savedInstanceState == null) {
                if (selectedTabIndex >= 0 && selectedTabIndex <= 4) {
                    switchToTab(selectedTabIndex)
                } else {
                    loadFragment(HomeFragment())
                    updateTabSelection(0)
                }
                isUiReady = true
                deliverPendingWorkOrderIfPossible()
            } else {
                currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is HomeFragment) {
                    homeFragment = currentFragment as HomeFragment
                }
                isUiReady = true
                deliverPendingWorkOrderIfPossible()
            }
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
    
    private fun getFcmTokenForTesting() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM_TOKEN", "FCM Token: $token")
                // Copy token ini untuk testing di Firebase Console
                Toast.makeText(this, "FCM Token: ${token.take(20)}...", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e("FCM_TOKEN", "Failed to get FCM token", e)
            }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) {
            android.util.Log.d("DeepLink", "Intent is null")
            return
        }

        val directWoId = intent.getStringExtra(EXTRA_TARGET_WO_ID)
            ?: intent.getStringExtra("woId")
            ?: intent.getStringExtra("workorder_id")
            ?: intent.extras?.getString("woId")
            ?: intent.extras?.getString("workorder_id")

        if (!directWoId.isNullOrEmpty()) {
            android.util.Log.d("DeepLink", "Found WO ID in extras: $directWoId")
            focusOnWorkOrder(directWoId)
            return
        }

        if (intent.action == ACTION_OPEN_WORK_ORDER) {
            val actionWoId = intent.extras?.getString("workorder_id")
            if (!actionWoId.isNullOrEmpty()) {
                android.util.Log.d("DeepLink", "Found WO ID from action: $actionWoId")
                focusOnWorkOrder(actionWoId)
                return
            }
        }

        intent.data?.let { uri ->
            android.util.Log.d("DeepLink", "Received URI: $uri")
            android.util.Log.d("DeepLink", "Scheme: ${uri.scheme}, Host: ${uri.host}, Path: ${uri.lastPathSegment}")
            
            if (uri.scheme == "emswo" && uri.host == "workorder") {
                val woId = uri.lastPathSegment
                android.util.Log.d("DeepLink", "Extracted WO ID from URI: $woId")
                if (!woId.isNullOrEmpty()) {
                    focusOnWorkOrder(woId)
                } else {
                    android.util.Log.e("DeepLink", "WO ID is null or empty in URI")
                    android.widget.Toast.makeText(this, "Invalid Work Order ID", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.d("DeepLink", "URI doesn't match expected pattern")
            }
        } ?: run {
            android.util.Log.d("DeepLink", "No URI data in intent")
        }
    }
    
    private fun focusOnWorkOrder(woId: String) {
        android.util.Log.d("DeepLink", "Request focus on WO: $woId")
        pendingNavigateWoId = woId
        deliverPendingWorkOrderIfPossible()
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
            // Navigate to ProjectViewActivity instead of OutboxFragment
            val intent = Intent(this, com.sofindo.ems.activities.ProjectViewActivity::class.java)
            startActivity(intent)
            // Keep current tab selection (don't change bottom nav selection)
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
            loadFragment(UtilityFragment())
            updateTabSelection(4)
        }
        
        // Set initial selection
        updateTabSelection(0)
    }
    
    fun updateTabSelection(selectedIndex: Int) {
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
    
    private fun deliverPendingWorkOrderIfPossible() {
        if (!isUiReady) {
            android.util.Log.d("DeepLink", "UI not ready yet, pending WO remains $pendingNavigateWoId")
            return
        }

        val woId = pendingNavigateWoId ?: return
        val needToSwitchTab = currentFragment !is HomeFragment
        if (needToSwitchTab) {
            android.util.Log.d("DeepLink", "Switching to Home tab for WO: $woId")
            loadFragment(HomeFragment())
            updateTabSelection(0)
        }

        val targetFragment = homeFragment
        if (targetFragment != null && ::customBottomNavigation.isInitialized) {
            targetFragment.focusOnWorkOrder(woId)
            pendingNavigateWoId = null
        } else {
            android.util.Log.d("DeepLink", "HomeFragment not ready, keep pending WO: $woId")
        }
    }

    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        
        homeFragment = if (fragment is HomeFragment) fragment else null

        // Update app bar title and menu
        updateAppBar()
    }
    
    // Helper function to ensure tab selection is updated after fragment transaction
    private fun ensureTabSelection(selectedIndex: Int) {
        // Post to ensure fragment transaction is complete
        customBottomNavigation.post {
            updateTabSelection(selectedIndex)
        }
    }
    
    private fun updateAppBar() {
        val title = when (currentFragment) {
            is HomeFragment -> "Home"
            is OutboxFragment -> "Outbox"
            is TambahWOFragment -> "Add Work Order"
            is MaintenanceFragment -> "Maintenance"
            is UtilityFragment -> "Utility"
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
                ensureTabSelection(0)
            }
            1 -> {
                loadFragment(OutboxFragment())
                ensureTabSelection(1)
            }
            2 -> {
                loadFragment(TambahWOFragment())
                ensureTabSelection(2)
            }
            3 -> {
                loadFragment(MaintenanceFragment())
                ensureTabSelection(3)
            }
            4 -> {
                loadFragment(UtilityFragment())
                ensureTabSelection(4)
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
