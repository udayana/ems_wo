package com.sofindo.ems.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.auth.LoginActivity
import com.sofindo.ems.models.User
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var ivEditProfile: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var btnChangePassword: View
    private lateinit var btnHelpSupport: View
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

    private fun initViews(view: View) {
        ivEditProfile = view.findViewById(R.id.iv_edit_profile)
        tvUserName = view.findViewById(R.id.tv_user_name)
        tvEmail = view.findViewById(R.id.tv_email)
        tvPhone = view.findViewById(R.id.tv_phone)
        btnChangePassword = view.findViewById(R.id.btn_change_password)
        btnHelpSupport = view.findViewById(R.id.btn_help_support)
        btnLogout = view.findViewById(R.id.btn_logout)
    }

    private fun setupListeners() {
        ivEditProfile.setOnClickListener {
            val editProfileFragment = EditProfileFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editProfileFragment)
                .addToBackStack(null)
                .commit()
        }
        
        btnChangePassword.setOnClickListener {
            openChangePasswordUrl()
        }
        
        btnHelpSupport.setOnClickListener {
            val supportFragment = SupportFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, supportFragment)
                .addToBackStack(null)
                .commit()
        }
        
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser(requireContext())
                if (user != null) {
                    displayUserData(user)
                } else {
                    Toast.makeText(context, "User data not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayUserData(user: User) {
        tvUserName.text = user.fullName ?: user.username ?: "Unknown User"
        tvEmail.text = user.email ?: "Not available"
        tvPhone.text = user.phoneNumber ?: "Not filled"
    }

    private fun openChangePasswordUrl() {
        val url = "https://emshotels.net/member/forgot-password.php"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link: $url", Toast.LENGTH_SHORT).show()
        }
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
                UserService.clearUserData(requireContext())
                Toast.makeText(context, "Successfully logged out from application", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }
}
