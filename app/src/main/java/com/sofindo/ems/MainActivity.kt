package com.sofindo.ems

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sofindo.ems.auth.LoginActivity
import com.sofindo.ems.fragments.HomeFragment
import com.sofindo.ems.fragments.OutboxFragment
import com.sofindo.ems.fragments.TambahWOFragment
import com.sofindo.ems.fragments.MaintenanceFragment
import com.sofindo.ems.fragments.ProfileFragment
import com.sofindo.ems.fragments.EditProfileFragment
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var bottomNavigationView: BottomNavigationView
    private var currentFragment: Fragment? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Check if user is logged in
        lifecycleScope.launch {
            val currentUser = UserService.getCurrentUser()
            val currentPropID = UserService.getCurrentPropID()
            
            android.util.Log.d("MainActivity", "Current user: $currentUser")
            android.util.Log.d("MainActivity", "Current propID: $currentPropID")
            
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
            
            // Set default fragment to Home
            if (savedInstanceState == null) {
                loadFragment(HomeFragment())
            }
        }
    }
    
    private fun setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_outbox -> {
                    loadFragment(OutboxFragment())
                    true
                }
                R.id.nav_add_wo -> {
                    loadFragment(TambahWOFragment())
                    true
                }
                R.id.nav_maintenance -> {
                    loadFragment(MaintenanceFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
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
        bottomNavigationView.selectedItemId = when (tabIndex) {
            0 -> R.id.nav_home
            1 -> R.id.nav_outbox
            2 -> R.id.nav_add_wo
            3 -> R.id.nav_maintenance
            4 -> R.id.nav_profile
            else -> R.id.nav_home
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
