package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    
    private lateinit var ivProfile: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnLogout: Button
    
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
    
    private fun initViews(view: View) {
        ivProfile = view.findViewById(R.id.iv_profile)
        tvName = view.findViewById(R.id.tv_name)
        tvEmail = view.findViewById(R.id.tv_email)
        tvPhone = view.findViewById(R.id.tv_phone)
        tvRole = view.findViewById(R.id.tv_role)
        tvDepartment = view.findViewById(R.id.tv_department)
        btnEditProfile = view.findViewById(R.id.btn_edit_profile)
        btnLogout = view.findViewById(R.id.btn_logout)
    }
    
    private fun setupListeners() {
        btnEditProfile.setOnClickListener {
            // TODO: Navigate to edit profile
        }
        
        btnLogout.setOnClickListener {
            logout()
        }
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                val propID = UserService.getCurrentPropID()
                
                android.util.Log.d("ProfileFragment", "Current user: $user")
                android.util.Log.d("ProfileFragment", "Current propID: $propID")
                
                if (user != null) {
                    tvName.text = user.fullName ?: user.username
                    tvEmail.text = user.email
                    tvPhone.text = user.phoneNumber ?: "Not provided"
                    tvRole.text = user.role
                    tvDepartment.text = user.dept ?: "Not specified"
                    
                    // Add propID to display for debugging
                    val propIDText = view?.findViewById<TextView>(R.id.tv_prop_id)
                    propIDText?.text = "Prop ID: $propID"
                    
                    // TODO: Load profile image if available
                    // if (user.profileImage != null) {
                    //     // Load image using Glide or similar
                    // }
                } else {
                    showError("User data not found")
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error loading user data", e)
                showError("Failed to load user data: ${e.message}")
            }
        }
    }
    
    private fun logout() {
        lifecycleScope.launch {
            try {
                UserService.clearUserData()
                // Navigate back to login
                requireActivity().finish()
            } catch (e: Exception) {
                showError("Failed to logout: ${e.message}")
            }
        }
    }
    
    private fun showError(message: String) {
        // TODO: Show error message
    }
}

