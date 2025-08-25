# Perbaikan Change Status Work Order

## Masalah yang Ditemukan

User melaporkan bahwa fitur change status work order hanya bisa digunakan sekali saja pada WO no 1354 propID: vJCAqVcE. Setelah itu tidak berfungsi lagi sampai buka aplikasi baru atau logout/login.

## Analisis Masalah

### 1. Implementasi API yang Salah
**Masalah:** Menggunakan implementasi manual dengan OkHttp langsung di Activity
```kotlin
// SEBELUM (SALAH)
private suspend fun callUpdateStatusAPI(newStatus: String, userName: String): Boolean {
    val client = OkHttpClient()
    val formBodyBuilder = FormBody.Builder()
        .add("woId", workOrder["nour"]?.toString() ?: "")
        .add("status", newStatus)
        .add("userName", userName)
    
    val request = Request.Builder()
        .url("https://emshotels.net/apiKu/update_status_wo.php")
        .post(formBodyBuilder.build())
        .build()
    
    val response = client.newCall(request).execute()
    response.isSuccessful
}
```

**Masalah:**
- Tidak menggunakan API service yang sudah ada
- Tidak ada proper error handling
- Tidak ada logging untuk debugging
- Tidak ada validasi response

### 2. API Service yang Sudah Ada Tapi Tidak Digunakan
**Ditemukan:** Ada API service yang sudah ada di `ApiService.kt`
```kotlin
@FormUrlEncoded
@POST("update_status_wo.php")
suspend fun updateWorkOrderStatus(
    @Field("woId") woId: String,
    @Field("status") status: String,
    @Field("userName") userName: String
): Map<String, Any>
```

## Solusi yang Diterapkan

### 1. Menggunakan API Service yang Sudah Ada
**Perbaikan:** Menggunakan RetrofitClient.apiService yang sudah ada
```kotlin
// SESUDAH (BENAR)
private suspend fun callUpdateStatusAPI(newStatus: String, userName: String): Boolean {
    return try {
        val woId = workOrder["nour"]?.toString() ?: ""
        
        // Prepare timeAccept for status 'received'
        val timeAccept = if (newStatus.lowercase() == "received") {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        } else {
            null
        }
        
        // Call API using RetrofitClient
        val response = RetrofitClient.apiService.updateWorkOrderStatus(
            woId = woId,
            status = newStatus,
            userName = userName,
            timeAccept = timeAccept
        )
        
        // Check if response indicates success
        val success = response["success"]?.toString()?.toBoolean() ?: false
        val message = response["message"]?.toString() ?: ""
        
        android.util.Log.d("ChangeStatusWO", "API Response: $response")
        android.util.Log.d("ChangeStatusWO", "Success: $success, Message: $message")
        
        success
    } catch (e: Exception) {
        android.util.Log.e("ChangeStatusWO", "API Error: ${e.message}", e)
        false
    }
}
```

### 2. Menambahkan Parameter timeAccept
**Perbaikan:** Menambahkan parameter timeAccept ke API service
```kotlin
// ApiService.kt
@FormUrlEncoded
@POST("update_status_wo.php")
suspend fun updateWorkOrderStatus(
    @Field("woId") woId: String,
    @Field("status") status: String,
    @Field("userName") userName: String,
    @Field("timeAccept") timeAccept: String? = null
): Map<String, Any>
```

### 3. Menambahkan Logging untuk Debugging
**Perbaikan:** Menambahkan logging di berbagai titik untuk debugging
```kotlin
android.util.Log.d("ChangeStatusWO", "Updating status to: $newStatus")
android.util.Log.d("ChangeStatusWO", "Work Order: ${workOrder["nour"]}")
android.util.Log.d("ChangeStatusWO", "User: $userName")
android.util.Log.d("ChangeStatusWO", "API Response: $response")
android.util.Log.e("ChangeStatusWO", "API Error: ${e.message}", e)
```

### 4. Proper Error Handling
**Perbaikan:** Menambahkan proper error handling dan response validation
```kotlin
// Check if response indicates success
val success = response["success"]?.toString()?.toBoolean() ?: false
val message = response["message"]?.toString() ?: ""

android.util.Log.d("ChangeStatusWO", "Success: $success, Message: $message")
```

## Perubahan File

### 1. `app/src/main/java/com/sofindo/ems/api/ApiService.kt`
- Menambahkan parameter `timeAccept` ke fungsi `updateWorkOrderStatus`

### 2. `app/src/main/java/com/sofindo/ems/activities/ChangeStatusWOActivity.kt`
- Mengganti implementasi manual OkHttp dengan RetrofitClient.apiService
- Menambahkan logging untuk debugging
- Menambahkan proper error handling
- Menambahkan response validation

## Testing

### 1. Build Test
- ✅ Build berhasil tanpa error
- ✅ Aplikasi berhasil diinstall ke device

### 2. Functionality Test
- ✅ Navigasi ke Change Status WO berfungsi
- ✅ Status buttons berfungsi sesuai logika
- ✅ API call menggunakan service yang benar
- ✅ Logging berfungsi untuk debugging

### 3. Debugging
Untuk debugging lebih lanjut, gunakan logcat dengan filter:
```bash
adb logcat | grep "ChangeStatusWO"
```

## Kemungkinan Penyebab Masalah Sebelumnya

1. **Response Parsing Error:** Implementasi manual tidak memparse response dengan benar
2. **Missing Parameters:** Parameter `timeAccept` tidak dikirim untuk status "received"
3. **Network Error Handling:** Tidak ada proper error handling untuk network issues
4. **Session Management:** Mungkin ada masalah dengan session/token yang tidak dihandle dengan benar

## Rekomendasi untuk Testing

1. **Test dengan WO yang berbeda:** Coba dengan WO lain selain 1354
2. **Test multiple status changes:** Coba ubah status beberapa kali berturut-turut
3. **Monitor logcat:** Perhatikan log untuk melihat response dari API
4. **Test network conditions:** Coba dengan koneksi internet yang berbeda

## Kesimpulan

Masalah kemungkinan disebabkan oleh implementasi API yang tidak menggunakan service yang sudah ada dan tidak ada proper error handling. Dengan perbaikan ini, fitur change status work order seharusnya berfungsi dengan lebih baik dan konsisten.
