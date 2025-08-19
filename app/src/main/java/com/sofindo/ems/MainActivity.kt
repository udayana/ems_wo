package com.sofindo.ems

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sofindo.ems.camera.CameraFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Set title for the activity
        supportActionBar?.title = "EMS WO"
        
        // Initialize camera functionality
        setupCamera()
    }
    
    private fun setupCamera() {
        val cameraFragment = CameraFragment()
        cameraFragment.setOnQrScannedListener { qrCode ->
            // Handle QR code scan
            Toast.makeText(this, "QR Code: $qrCode", Toast.LENGTH_LONG).show()
            // TODO: Process QR code data
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, cameraFragment)
            .commit()
    }
}
