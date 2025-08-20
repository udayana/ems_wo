package com.sofindo.ems

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sofindo.ems.fragment.HomeFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Set title for the activity
        supportActionBar?.title = "EMS WO"
        
        // Initialize bottom navigation
        setupBottomNavigation()
        
        // Initialize home fragment
        setupHomeFragment()
    }
    
    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val homeFragment = HomeFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, homeFragment)
                        .commit()
                    true
                }
                R.id.nav_qr -> {
                    // TODO: Implement QR Scanner fragment
                    true
                }
                R.id.nav_outbox -> {
                    // TODO: Implement Outbox fragment
                    true
                }
                R.id.nav_profile -> {
                    // TODO: Implement Profile fragment
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupHomeFragment() {
        val homeFragment = HomeFragment()
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, homeFragment)
            .commit()
    }
}
