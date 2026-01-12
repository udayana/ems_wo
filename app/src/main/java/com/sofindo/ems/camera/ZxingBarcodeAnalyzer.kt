package com.sofindo.ems.camera

import android.graphics.ImageFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.util.*

/**
 * ZXing Barcode Analyzer - Pure Java implementation, no native libraries
 * 100% compatible with 16KB page size requirement
 */
class ZxingBarcodeAnalyzer(private val onQrScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
    
    private val reader = MultiFormatReader()
    private val handler = Handler(Looper.getMainLooper())
    private var lastScanTime = 0L
    private val scanDebounceTime = 500L // 0.5 second debounce
    
    init {
        // Configure reader for QR code only (faster and more reliable)
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(
            com.google.zxing.BarcodeFormat.QR_CODE
        )
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
        reader.setHints(hints)
    }
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        
        // Debounce to prevent multiple rapid scans
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < scanDebounceTime) {
            imageProxy.close()
            return
        }
        
        try {
            val format = mediaImage.format
            val width = mediaImage.width
            val height = mediaImage.height
            
            // Check image format - should be YUV_420_888
            if (format != ImageFormat.YUV_420_888) {
                imageProxy.close()
                return
            }
            
            val planes = mediaImage.planes
            if (planes.size < 3) {
                imageProxy.close()
                return
            }
            
            // Get Y plane (luminance) - QR codes are grayscale
            val yPlane = planes[0]
            val yBuffer = yPlane.buffer
            
            // Get buffer info
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            
            // Read Y plane data
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            val yBufferPos = yBuffer.position()
            yBuffer.get(yBytes)
            yBuffer.position(yBufferPos) // Reset position
            
            // Extract Y plane data accounting for row stride and pixel stride
            val yData = ByteArray(width * height)
            var yDataOffset = 0
            
            for (y in 0 until height) {
                val rowStart = y * yRowStride
                for (x in 0 until width) {
                    val pixelIndex = rowStart + x * yPixelStride
                    if (pixelIndex < ySize) {
                        yData[yDataOffset] = yBytes[pixelIndex]
                    } else {
                        yData[yDataOffset] = 0
                    }
                    yDataOffset++
                }
            }
            
            // Downscale if too large (for better performance)
            val maxDimension = 800
            val scaleFactor = if (width > maxDimension || height > maxDimension) {
                kotlin.math.min(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
            } else {
                1.0f
            }
            
            val scaledWidth = (width * scaleFactor).toInt()
            val scaledHeight = (height * scaleFactor).toInt()
            
            val scaledYData = if (scaleFactor < 1.0f) {
                downscaleImage(yData, width, height, scaledWidth, scaledHeight)
            } else {
                yData
            }
            
            // Create NV21-like format for PlanarYUVLuminanceSource
            val nv21Data = ByteArray(scaledWidth * scaledHeight * 3 / 2)
            System.arraycopy(scaledYData, 0, nv21Data, 0, scaledYData.size)
            
            // Create luminance source
            val source = PlanarYUVLuminanceSource(
                nv21Data,
                scaledWidth,
                scaledHeight,
                0,
                0,
                scaledWidth,
                scaledHeight,
                false
            )
            
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            // Try to decode QR code
            val result: Result? = try {
                reader.decodeWithState(binaryBitmap)
            } catch (e: com.google.zxing.NotFoundException) {
                null // Normal - no QR code found
            } catch (e: Exception) {
                if (System.currentTimeMillis() % 5000 < 100) {
                    Log.d(TAG, "Decode exception: ${e.javaClass.simpleName}")
                }
                null
            } finally {
                reader.reset()
            }
            
            result?.text?.let { qrCode ->
                lastScanTime = currentTime
                Log.d(TAG, "QR code detected: $qrCode")
                handler.post {
                    try {
                        onQrScanned(qrCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in QR scan callback", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Downscale image using nearest neighbor
     */
    private fun downscaleImage(
        source: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): ByteArray {
        val target = ByteArray(targetWidth * targetHeight)
        val xRatio = sourceWidth.toFloat() / targetWidth
        val yRatio = sourceHeight.toFloat() / targetHeight
        
        for (y in 0 until targetHeight) {
            val sourceY = (y * yRatio).toInt().coerceAtMost(sourceHeight - 1)
            for (x in 0 until targetWidth) {
                val sourceX = (x * xRatio).toInt().coerceAtMost(sourceWidth - 1)
                val sourceIndex = sourceY * sourceWidth + sourceX
                val targetIndex = y * targetWidth + x
                target[targetIndex] = source[sourceIndex]
            }
        }
        return target
    }
    
    companion object {
        private const val TAG = "ZxingBarcodeAnalyzer"
    }
}
































