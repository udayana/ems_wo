package com.sofindo.ems.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.models.User
import com.sofindo.ems.services.UserService
import com.sofindo.ems.api.RetrofitClient
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment() {

    private lateinit var ivBackButton: ImageView
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        loadUserData()
    }

    private fun initViews(view: View) {
        ivBackButton = view.findViewById(R.id.iv_back_button)
        etFullName = view.findViewById(R.id.et_full_name)
        etEmail = view.findViewById(R.id.et_email)
        etPhone = view.findViewById(R.id.et_phone)
        btnSave = view.findViewById(R.id.btn_save)
        btnCancel = view.findViewById(R.id.btn_cancel)
    }

    private fun setupListeners() {
        ivBackButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        btnSave.setOnClickListener {
            saveProfile()
        }

        btnCancel.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser(requireContext())
                if (user != null) {
                    displayUserData(user)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayUserData(user: User) {
        currentUser = user
        etFullName.setText(user.fullName ?: user.username ?: "")
        etEmail.setText(user.email ?: "")
        etPhone.setText(user.phoneNumber ?: "")
    }

    private fun saveProfile() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            return
        }

        val user = currentUser
        if (user?.id == null) {
            Toast.makeText(context, "User data not available", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                btnSave.isEnabled = false
                btnSave.text = "Saving..."

                val response = RetrofitClient.instance.updateUserProfile(
                    id = user.id,
                    fullName = fullName,
                    email = email,
                    phoneNumber = phone
                )

                val success = response["success"] as? Boolean ?: false
                val message = response["message"] as? String ?: "Unknown error"

                if (success) {
                    val updatedUser = user.copy(
                        fullName = fullName,
                        email = email,
                        phoneNumber = phone
                    )
                    UserService.saveUser(requireContext(), updatedUser)

                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    Toast.makeText(context, "Error: $message", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
                btnSave.text = "Save Changes"
            }
        }
    }
}
