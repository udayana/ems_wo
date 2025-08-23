package com.sofindo.ems

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sofindo.ems.fragment.HomeFragment
import com.sofindo.ems.fragment.OutboxFragment
import com.sofindo.ems.fragment.ProfileFragment
import com.sofindo.ems.fragment.TambahWOFragment

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
        
        // Set background color to match Flutter (dark gray)
        bottomNavigation.setBackgroundColor(resources.getColor(R.color.dark_gray, theme))
        
        // Make Add icon bigger (1.5x)
        bottomNavigation.itemIconSize = resources.getDimensionPixelSize(R.dimen.bottom_nav_icon_size)
        
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_out -> {
                    loadFragment(OutboxFragment())
                    true
                }
                R.id.nav_add -> {
                    loadFragment(TambahWOFragment { tabIndex ->
                        // Handle tab change callback
                        when (tabIndex) {
                            1 -> bottomNavigation.selectedItemId = R.id.nav_out // Go to Outbox
                            else -> bottomNavigation.selectedItemId = R.id.nav_home
                        }
                    })
                    true
                }
                R.id.nav_maint -> {
                    // TODO: Implement Maintenance fragment
                    showComingSoon("Maintenance")
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
    
    private fun setupHomeFragment() {
        loadFragment(HomeFragment())
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun showComingSoon(feature: String) {
        // Show a simple toast for now
        android.widget.Toast.makeText(this, "$feature - Coming Soon!", android.widget.Toast.LENGTH_SHORT).show()
    }
}
