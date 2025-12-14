package com.sofindo.ems.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.auth.LoginActivity
import com.sofindo.ems.services.UserService
import com.sofindo.ems.fragments.SupportFragment
import com.sofindo.ems.fragments.EditProfileFragment
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    
    private lateinit var ivProfile: ImageView
    private lateinit var ivEditProfile: ImageView
    private lateinit var tvPropertyName: TextView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var btnChangePassword: View
    private lateinit var btnHelpSupport: View
    private lateinit var btnUpdate: View
    private lateinit var btnLogout: View
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh user data when returning from edit profile
        loadUserData()
    }
    
    private fun initViews(view: View) {
        ivProfile = view.findViewById(R.id.iv_profile)
        ivEditProfile = view.findViewById(R.id.iv_edit_profile)
        tvPropertyName = view.findViewById(R.id.tv_property_name)
        tvName = view.findViewById(R.id.tv_name)
        tvEmail = view.findViewById(R.id.tv_email)
        tvPhone = view.findViewById(R.id.tv_phone)
        btnChangePassword = view.findViewById(R.id.btn_change_password)
        btnHelpSupport = view.findViewById(R.id.btn_help_support)
        btnUpdate = view.findViewById(R.id.btn_update)
        btnLogout = view.findViewById(R.id.btn_logout)
    }
    
    private fun setupListeners() {
        ivEditProfile.setOnClickListener {
            navigateToEditProfile()
        }
        
        btnChangePassword.setOnClickListener {
            openChangePasswordUrl()
        }
        
        btnHelpSupport.setOnClickListener {
            navigateToSupport()
        }
        
        btnUpdate.setOnClickListener {
            openGooglePlayStore()
        }
        
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                
                if (user != null) {
                    // Load user data
                    tvName.text = user.fullName ?: user.username ?: user.email
                    tvEmail.text = user.email
                    tvPhone.text = user.phoneNumber ?: "No phone number"
                    
                    // Set property name from user data
                    tvPropertyName.text = user.propertyName ?: "Profile"
                    
                    // Load profile image with cache clearing
                    if (!user.profileImage.isNullOrEmpty()) {
                        // Clear Glide cache for this specific URL
                        Glide.get(requireContext()).clearMemory()
                        Thread {
                            Glide.get(requireContext()).clearDiskCache()
                        }.start()
                        
                        // Load image with cache busting
                        Glide.with(requireContext())
                            .load(getProfileImageUrl(user.profileImage!!) + "?t=" + System.currentTimeMillis())
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivProfile)
                    } else {
                        ivProfile.setImageResource(R.drawable.ic_person)
                    }
                }
            } catch (e: Exception) {
                // Error loading user data
            }
        }
    }
    
    private fun getProfileImageUrl(profileImage: String): String {
        return "https://emshotels.net/images/user/profile/thumb/$profileImage"
    }
    
    private fun openChangePasswordUrl() {
        val url = "https://emshotels.net/member/forgot-password.php"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Cannot open link: $url",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun navigateToSupport() {
        // Navigate to support fragment
        val supportFragment = SupportFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, supportFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun navigateToEditProfile() {
        // Navigate to edit profile fragment
        val editProfileFragment = EditProfileFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, editProfileFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout from the application?")
            .setPositiveButton("Logout") { _, _ ->
                handleLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                // Logout but keep user data for display
                UserService.logout()
                
                Toast.makeText(
                    requireContext(),
                    "Successfully logged out from application",
                    Toast.LENGTH_LONG
                ).show()
                
                // Navigate to login screen and clear all previous routes
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
                
            } catch (e: Exception) {
                // Still logout even if there's an error
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }
    
    private fun openGooglePlayStore() {
        try {
            // Try to open Google Play Store for this app
            val packageName = requireContext().packageName
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to web browser if Play Store app is not available
                val packageName = requireContext().packageName
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Cannot open Google Play Store",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}

