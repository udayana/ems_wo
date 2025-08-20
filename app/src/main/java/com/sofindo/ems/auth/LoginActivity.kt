package com.sofindo.ems.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.User
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvError: View
    private lateinit var tvForgotPassword: View
    private lateinit var tvRegister: View
    private lateinit var ivPasswordToggle: ImageView
    
    private var isLoading = false
    private var isPasswordVisible = false
    private var retryCount = 0
    private val maxRetries = 3
    
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupListeners()
        
        // Hardcode credentials for testing
        etEmail.setText("engineer@demo.com")
        etPassword.setText("123456")
    }
    
    private fun initViews() {
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        tvError = findViewById(R.id.tv_error)
        tvForgotPassword = findViewById(R.id.tv_forgot_password)
        tvRegister = findViewById(R.id.tv_register)
        ivPasswordToggle = findViewById(R.id.iv_password_toggle)
    }
    
    private fun setupListeners() {
        btnLogin.setOnClickListener {
            if (!isLoading) {
                handleLogin()
            }
        }
        
        tvForgotPassword.setOnClickListener {
            openForgotPassword()
        }
        
        tvRegister.setOnClickListener {
            // TODO: Navigate to register screen
            Toast.makeText(this, "Register feature coming soon", Toast.LENGTH_SHORT).show()
        }
        
        ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
    }
    
    private fun handleLogin() {
        val email = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        
        // Validation
        if (email.isEmpty()) {
            showError("Email or phone number is required")
            return
        }
        
        if (password.isEmpty()) {
            showError("Password is required")
            return
        }
        
        // Start login process
        loginWithRetry(email, password)
    }
    
    private fun loginWithRetry(email: String, password: String) {
        isLoading = true
        retryCount = 0
        updateUI()
        
        lifecycleScope.launch {
            var lastError = ""
            
            for (attempt in 1..maxRetries) {
                try {
                    retryCount = attempt
                    updateUI()
                    
                    val result = RetrofitClient.apiService.login(email, password)
                    
                    if (result["error"] != null) {
                        lastError = result["error"].toString()
                        if (attempt < maxRetries) {
                            // Wait before retry
                            kotlinx.coroutines.delay(1000L * attempt)
                            continue
                        }
                    } else {
                        // Login successful
                        handleLoginSuccess(result)
                        return@launch
                    }
                } catch (e: Exception) {
                    lastError = "Login failed: ${e.message}"
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(1000L * attempt)
                        continue
                    }
                }
            }
            
            // All attempts failed
            handleLoginError(lastError)
        }
    }
    
    private fun handleLoginSuccess(result: Map<String, Any>) {
        try {
            val user = User(
                id = result["id"]?.toString() ?: "",
                username = result["nama"]?.toString() ?: result["email"]?.toString() ?: "",
                email = result["email"]?.toString() ?: "",
                fullName = result["nama"]?.toString(),
                phoneNumber = result["telp"]?.toString(),
                profileImage = result["photoprofile"]?.toString(),
                role = result["dept"]?.toString() ?: "user",
                propID = result["propID"]?.toString(),
                dept = result["dept"]?.toString()
            )
            
            // Save user to SharedPreferences
            lifecycleScope.launch {
                UserService.saveUser(this@LoginActivity, user)
            }
            
            // Navigate to main activity
            val intent = Intent(this, com.sofindo.ems.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            handleLoginError("Failed to process login response: ${e.message}")
        }
    }
    
    private fun handleLoginError(error: String) {
        isLoading = false
        updateUI()
        showError(error)
    }
    
    private fun showError(message: String) {
        val errorTextView = findViewById<android.widget.TextView>(R.id.tv_error)
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        findViewById<View>(R.id.tv_error).visibility = View.GONE
    }
    
    private fun updateUI() {
        btnLogin.isEnabled = !isLoading
        
        if (isLoading) {
            if (retryCount > 0) {
                btnLogin.text = "Retry $retryCount/$maxRetries"
            } else {
                btnLogin.text = "Logging in..."
            }
            hideError()
        } else {
            btnLogin.text = "Login"
        }
    }
    
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        
        if (isPasswordVisible) {
            etPassword.transformationMethod = null
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            etPassword.transformationMethod = PasswordTransformationMethod()
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
        }
        
        // Move cursor to end
        etPassword.setSelection(etPassword.text?.length ?: 0)
    }
    
    private fun openForgotPassword() {
        try {
            val url = "https://emshotels.net/member/forgot-password.php"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open forgot password link", Toast.LENGTH_SHORT).show()
        }
    }
}
