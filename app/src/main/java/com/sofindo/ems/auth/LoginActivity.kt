package com.sofindo.ems.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.User
import com.sofindo.ems.services.UserService
import com.sofindo.ems.auth.RegisterActivity
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {
    
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvError: View
    private lateinit var tvForgotPassword: View
    private lateinit var tvRegister: View
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var ivProfile: ImageView
    
    private var isLoading = false
    private var isPasswordVisible = false
    private var retryCount = 0
    private val maxRetries = 3
    private var savedEmail = ""
    private var savedPassword = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        
        initViews()
        setupListeners()
        
        // Check if redirected from forgot password with auto-login
        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")
        val autoLogin = intent.getBooleanExtra("autoLogin", false)
        
        if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
            etEmail.setText(email)
            etPassword.setText(password)
            
            // Auto-login after password change (like iOS)
            if (autoLogin) {
                // Delay auto-login like iOS (0.5 seconds)
                Handler(Looper.getMainLooper()).postDelayed({
                    handleLogin()
                }, 500)
            }
        } else {
            // Fallback for old format (email_or_hp)
            val emailOrHp = intent.getStringExtra("email_or_hp")
            val passwordOld = intent.getStringExtra("password")
            if (!emailOrHp.isNullOrEmpty() && !passwordOld.isNullOrEmpty()) {
                etEmail.setText(emailOrHp)
                etPassword.setText(passwordOld)
            }
        }
    }
    
    private fun initViews() {
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        tvError = findViewById(R.id.tv_error)
        tvForgotPassword = findViewById(R.id.tv_forgot_password)
        tvRegister = findViewById(R.id.tv_register)
        ivPasswordToggle = findViewById(R.id.iv_password_toggle)
        ivProfile = findViewById(R.id.iv_profile)
        
        // Load saved credentials if available
        loadSavedCredentials()
        
        // Load user profile image if available
        loadUserProfileImage()
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
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
        
        ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
        
        // Add text change listeners for auto-hide keyboard
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkAndHideKeyboard()
            }
        })
        
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkAndHideKeyboard()
            }
        })
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
        
        // Use real API login
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
                        // Server returned error, show simple message
                        lastError = "Belum berhasil, silahkan dicoba lagi !"
                        android.util.Log.e("LoginActivity", "Server error: ${result["error"]}")
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
                    // Log error for debugging, but show simple message to user
                    android.util.Log.e("LoginActivity", "Login error: ${e.message}", e)
                    lastError = "Belum berhasil, silahkan dicoba lagi !"
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
            
            // Try different possible keys for propID
            val propID = result["propID"]?.toString() 
                ?: result["prop_id"]?.toString() 
                ?: result["propID"]?.toString()
                ?: ""
            
            val user = User(
                id = result["id"]?.toString() ?: "",
                username = result["nama"]?.toString() ?: result["email"]?.toString() ?: "",
                email = result["email"]?.toString() ?: "",
                fullName = result["nama"]?.toString(),
                phoneNumber = result["telp"]?.toString(),
                profileImage = result["photoprofile"]?.toString(),
                role = result["role"]?.toString() ?: "user",
                propID = propID,
                dept = result["dept"]?.toString(),
                jabatan = result["jabatan"]?.toString()
            )
            
            // Create user object
            
            // Save user to SharedPreferences
            lifecycleScope.launch {
                try {
                    UserService.saveUser(user)
                    
                    // Verify the data was saved
                    val savedUser = UserService.getCurrentUser()
                    val savedPropID = UserService.getCurrentPropID()
                    
                    // Check if credentials are already saved
                    val sharedPrefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
                    val rememberMe = sharedPrefs.getBoolean("remember_me", false)
                    
                    if (rememberMe) {
                        // Credentials already saved, navigate directly
                        runOnUiThread {
                            navigateToMainActivity()
                        }
                    } else {
                        // Show Remember Me dialog for first time
                        runOnUiThread {
                            showRememberMeDialog(user.email)
                        }
                    }
                    
                    // Send initial FCM token after login (best-effort)
                    try {
                        FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token ->
                                lifecycleScope.launch {
                                    try {
                                        val dept = user.dept ?: ""
                                        RetrofitClient.apiService.saveFcmToken(
                                            token = token,
                                            email = user.email,
                                            dept = dept
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    android.util.Log.e("LoginActivity", "Failed to save user data: ${e.message}", e)
                    handleLoginError("Belum berhasil, silahkan dicoba lagi !")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Failed to process login response: ${e.message}", e)
            handleLoginError("Belum berhasil, silahkan dicoba lagi !")
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
        val intent = Intent(this, ForgotPasswordActivity::class.java)
        startActivity(intent)
    }
    
    private fun loadUserProfileImage() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                
                if (user != null && !user.profileImage.isNullOrEmpty()) {
                    // Load profile image with cache clearing
                    Glide.get(this@LoginActivity).clearMemory()
                    Thread {
                        Glide.get(this@LoginActivity).clearDiskCache()
                    }.start()
                    
                    // Load image with cache busting
                    Glide.with(this@LoginActivity)
                        .load(getProfileImageUrl(user.profileImage!!) + "?t=" + System.currentTimeMillis())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(ivProfile)
                } else {
                    ivProfile.setImageResource(R.drawable.ic_person)
                }
            } catch (e: Exception) {
                // Error loading user data, use default icon
                ivProfile.setImageResource(R.drawable.ic_person)
            }
        }
    }
    
    private fun getProfileImageUrl(profileImage: String): String {
        return "https://emshotels.net/images/user/profile/thumb/$profileImage"
    }
    
    private fun checkAndHideKeyboard() {
        val email = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        
        // Check if email has valid format and password has at least 6 characters
        if (isValidEmail(email) && password.length >= 6) {
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val currentFocus = currentFocus
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
                currentFocus.clearFocus()
            }
        }
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    private fun showRememberMeDialog(userEmail: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_remember_me, null)
        val switchRememberMe = dialogView.findViewById<SwitchMaterial>(R.id.switch_remember_me)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnOk = dialogView.findViewById<View>(R.id.btn_ok)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        btnCancel.setOnClickListener {
            // Don't save credentials, just navigate
            dialog.dismiss()
            navigateToMainActivity()
        }
        
        btnOk.setOnClickListener {
            if (switchRememberMe.isChecked) {
                // Save credentials
                saveCredentials(userEmail, etPassword.text?.toString() ?: "")
            }
            dialog.dismiss()
            navigateToMainActivity()
        }
        
        dialog.show()
    }
    
    private fun saveCredentials(email: String, password: String) {
        val sharedPrefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("saved_email", email)
            putString("saved_password", password)
            putBoolean("remember_me", true)
            apply()
        }
    }
    
    private fun loadSavedCredentials() {
        val sharedPrefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val rememberMe = sharedPrefs.getBoolean("remember_me", false)
        
        if (rememberMe) {
            savedEmail = sharedPrefs.getString("saved_email", "") ?: ""
            savedPassword = sharedPrefs.getString("saved_password", "") ?: ""
            
            if (savedEmail.isNotEmpty() && savedPassword.isNotEmpty()) {
                etEmail.setText(savedEmail)
                etPassword.setText(savedPassword)
            }
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this@LoginActivity, com.sofindo.ems.MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
