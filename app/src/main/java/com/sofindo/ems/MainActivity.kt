package com.sofindo.ems

import android.content.Intent
import android.os.Bundle
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
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var bottomNavigationView: BottomNavigationView
    
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
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
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
}
