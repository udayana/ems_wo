package com.sofindo.ems.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {
    
    private lateinit var etEmail: EditText
    private lateinit var btnSendOTP: MaterialButton
    private lateinit var tvError: TextView
    private lateinit var toolbar: Toolbar
    
    private var isLoading = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etEmail = findViewById(R.id.et_email)
        btnSendOTP = findViewById(R.id.btn_send_otp)
        tvError = findViewById(R.id.tv_error)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Forgot Password"
    }
    
    private fun setupListeners() {
        btnSendOTP.setOnClickListener {
            if (!isLoading) {
                hideKeyboard()
                handleSendOTP()
            }
        }
        
        // Handle toolbar back button
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun handleSendOTP() {
        val email = etEmail.text?.toString()?.trim() ?: ""
        
        // Validation
        if (!isValidEmail(email)) {
            if (email.isEmpty()) {
                showError("Please enter your email address")
            } else {
                showError("Please enter a valid email address")
            }
            return
        }
        
        // Send OTP
        sendOTP(email)
    }
    
    private fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        val emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        return trimmed.matches(emailRegex.toRegex())
    }
    
    private fun sendOTP(email: String) {
        isLoading = true
        updateUI()
        
        lifecycleScope.launch {
            try {
                val requestBody = mapOf("email" to email)
                val result = RetrofitClient.apiService.checkEmailAndSendOTP(requestBody)
                
                val success = result["success"] as? Boolean ?: false
                if (success) {
                    // Navigate to OTP verification
                    val intent = Intent(this@ForgotPasswordActivity, VerifikasiOTPActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                } else {
                    val message = result["message"]?.toString() ?: "Failed to send OTP"
                    showError(message)
                }
            } catch (e: Exception) {
                android.util.Log.e("ForgotPasswordActivity", "Error sending OTP: ${e.message}", e)
                showError("Failed to send OTP: ${e.message}")
            } finally {
                isLoading = false
                updateUI()
            }
        }
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    private fun updateUI() {
        if (isLoading) {
            btnSendOTP.text = "Sending..."
            btnSendOTP.isEnabled = false
            hideError()
        } else {
            btnSendOTP.text = "Send OTP to Email"
            btnSendOTP.isEnabled = true
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus()
        }
    }
}

