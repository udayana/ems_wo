package com.sofindo.ems.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Fungsi utilitas untuk operasi gambar yang bisa dipanggil dari mana saja.
 *
 * resizeCrop:
 * 1. Cari sisi yang lebih pendek.
 * 2. Resize sehingga sisi pendek = [size] (default 480px), sisi panjang mengikuti proporsi.
 * 3. Crop di tengah menjadi persegi [size] x [size].
 *    File asli akan ditimpa, dan fungsi mengembalikan File yang sudah diproses.
 */
fun resizeCrop(
    file: File,
    size: Int = 480,
    quality: Int = 90
): File {
    // Baca dimensi tanpa load penuh
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return file

    // Hitung sample size agar decode hemat memori
    val shortestSide = minOf(srcW, srcH)
    val sample = calculateInSampleSize(shortestSide, size * 2) // decode sedikit lebih besar

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return file

    // 1) Resize: sisi pendek menjadi [size], sisi panjang proporsional
    val shortSide = minOf(bitmap.width, bitmap.height)
    val scale = size.toFloat() / shortSide.toFloat()
    val targetW = (bitmap.width * scale).toInt()
    val targetH = (bitmap.height * scale).toInt()

    val scaled = if (targetW != bitmap.width || targetH != bitmap.height) {
        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    } else {
        bitmap
    }
    if (scaled !== bitmap) {
        bitmap.recycle()
    }

    // 2) Crop di tengah jadi [size] x [size]
    val cropX = (scaled.width - size) / 2
    val cropY = (scaled.height - size) / 2
    val safeCropX = cropX.coerceAtLeast(0)
    val safeCropY = cropY.coerceAtLeast(0)
    val cropW = minOf(size, scaled.width - safeCropX)
    val cropH = minOf(size, scaled.height - safeCropY)

    val square = Bitmap.createBitmap(scaled, safeCropX, safeCropY, cropW, cropH)
    if (square !== scaled) {
        scaled.recycle()
    }

    // 3) Simpan kembali ke file sebagai JPEG
    FileOutputStream(file, false).use { out ->
        square.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.flush()
    }
    square.recycle()

    return file
}

// Helper internal untuk menghitung inSampleSize dari sisi pendek.
private fun calculateInSampleSize(shortestSide: Int, targetShortest: Int): Int {
    var inSampleSize = 1
    if (shortestSide <= 0) return inSampleSize

    while (shortestSide / (inSampleSize * 2) >= targetShortest) {
        inSampleSize *= 2
    }
    return inSampleSize
}

/**
 * Wrapper lama agar kode yang masih memanggil ImageUtils.resizeAndSquareCropJpegInPlace()
 * tetap bisa dikompilasi. Implementasinya cukup memanggil resizeCrop di atas.
 */
object ImageUtils {
    fun resizeAndSquareCropJpegInPlace(
        file: File,
        size: Int = 420,
        quality: Int = 90
    ): File {
        return resizeCrop(file, size, quality)
    }
}







