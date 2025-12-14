package com.sofindo.ems.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sofindo.ems.R
import com.sofindo.ems.camera.ZxingBarcodeAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class QRScannerFragment : Fragment() {
    
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var previewView: PreviewView
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && isAdded && view != null) {
            startCamera()
        } else if (!isGranted) {
            val context = context
            if (context != null) {
                Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        previewView = view.findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun allPermissionsGranted(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                requireContext(), 
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera permission", e)
            false
        }
    }
    
    private fun startCamera() {
        // Check if fragment is still attached
        if (!isAdded || view == null) {
            Log.w(TAG, "Fragment not attached or view is null, cannot start camera")
            return
        }
        
        val context = requireContext()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                // Check again if fragment is still attached before proceeding
                if (!isAdded || view == null) {
                    Log.w(TAG, "Fragment detached before camera initialization, aborting")
                    return@addListener
                }
                
                // Get camera provider with timeout
                val provider = try {
                    cameraProviderFuture.get(10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get camera provider", e)
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Failed to initialize camera. Please try again.", Toast.LENGTH_LONG).show()
                    }
                    return@addListener
                }
                
                cameraProvider = provider
                
                // Build preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Build image analyzer for QR code scanning using ZXing (pure Java, 16KB compatible)
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(800, 800))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                .also {
                    Log.d(TAG, "Setting up ZXing Barcode Analyzer")
                    it.setAnalyzer(cameraExecutor, ZxingBarcodeAnalyzer { qrCode ->
                        Log.d(TAG, "ZXing Barcode Analyzer callback triggered with code: $qrCode")
                        // Handle QR code result on main thread
                        if (isAdded) {
                            handleQRCodeResult(qrCode)
                        } else {
                            Log.w(TAG, "Fragment not added, ignoring QR code result")
                        }
                    })
                }
                
                // Unbind all use cases before binding new ones
                provider.unbindAll()
                
                // Bind use cases to lifecycle - CRITICAL: use viewLifecycleOwner for Fragment
                try {
                    provider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                    Log.d(TAG, "Camera started successfully and bound to lifecycle")
                } catch (bindExc: Exception) {
                    Log.e(TAG, "Failed to bind camera to lifecycle", bindExc)
                    throw bindExc
                }
                
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to start camera", exc)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to start camera: ${exc.message}", Toast.LENGTH_LONG).show()
                }
            }
            
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun handleQRCodeResult(qrCode: String) {
        if (!isAdded || view == null) {
            Log.w(TAG, "Fragment not attached, ignoring QR code result")
            return
        }
        
        val context = context
        if (context == null) {
            Log.w(TAG, "Context is null, cannot handle QR code result")
            return
        }
        
        Log.d(TAG, "QR code scanned: $qrCode")
        Toast.makeText(context, "Scanned: $qrCode", Toast.LENGTH_SHORT).show()
        
        // Stop camera before navigating
        stopCamera()
        
        // Navigate to MaintenanceDetailFragment with the scanned asset number/URL
        try {
            val detailFragment = com.sofindo.ems.fragments.MaintenanceDetailFragment.newInstance("", "", qrCode)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to detail fragment", e)
            Toast.makeText(context, "Failed to open maintenance detail", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            Log.d(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop camera when fragment is paused to save resources
        stopCamera()
    }
    
    override fun onResume() {
        super.onResume()
        // Restart camera when fragment resumes if permission is granted
        if (allPermissionsGranted() && isAdded && view != null) {
            startCamera()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up camera resources
        stopCamera()
        // Shutdown executor
        try {
            cameraExecutor.shutdown()
            if (!cameraExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow()
            }
            Log.d(TAG, "Camera executor shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down camera executor", e)
            cameraExecutor.shutdownNow()
        }
    }
    
    companion object {
        private const val TAG = "QRScannerFragment"
        
        fun newInstance(): QRScannerFragment {
            return QRScannerFragment()
        }
    }
}
