# Implementasi Change Status WO

## Deskripsi

Halaman Change Status WO adalah halaman baru yang memungkinkan user untuk mengubah status work order. Halaman ini dapat diakses melalui menu "Follow Up" dari halaman Home. Implementasi ini mengikuti struktur dan fungsionalitas dari Flutter `change_status_view.dart`.

## File yang Dibuat

### 1. `app/src/main/java/com/sofindo/ems/activities/ChangeStatusWOActivity.kt`

**Fitur:**
- Menampilkan informasi work order (Location, Job, Date, Current Status)
- Tombol status yang dapat diaktifkan/nonaktifkan berdasarkan status saat ini
- Logika disable button sesuai dengan status current
- API call untuk update status work order
- Loading indicator saat proses update
- Dialog konfirmasi untuk status pending dan done

**Fungsi Utama:**
```kotlin
// Setup status buttons dengan logika disable
private fun setupStatusButtons()

// Logika disable button berdasarkan status current
private fun isButtonDisabled(status: String, currentStatus: String): Boolean

// Update status work order via API
private fun updateStatus(newStatus: String)

// API call untuk update status
private suspend fun callUpdateStatusAPI(newStatus: String, userName: String): Boolean
```

### 2. `app/src/main/res/layout/activity_change_status_wo.xml`

**Layout:**
- Background terang (#F5F5F5) sesuai dengan Flutter
- Toolbar dengan tombol back
- Card informasi work order
- Tombol status dalam format vertical list
- Loading overlay untuk feedback visual

**Komponen:**
- **Work Order Info Card:** Location, Job, Date, Current Status
- **Status Buttons:** Received, On Progress, Pending, Done
- **Loading Overlay:** Progress indicator saat update

### 3. Drawable Resources

**File yang Dibuat:**
- `card_background.xml` - Background untuk card informasi
- `button_primary_background.xml` - Background untuk tombol aktif
- `button_disabled_background.xml` - Background untuk tombol nonaktif

### 4. `app/src/main/AndroidManifest.xml` (Dimodifikasi)

**Perubahan:**
- Menambahkan deklarasi ChangeStatusWOActivity
- Menggunakan theme yang konsisten dengan aplikasi

## Integrasi dengan Fragment

### HomeFragment

**Perubahan pada fungsi `onFollowUpWorkOrder`:**
```kotlin
private fun onFollowUpWorkOrder(workOrder: Map<String, Any>) {
    // Navigate to change status activity
    val intent = android.content.Intent(context, com.sofindo.ems.activities.ChangeStatusWOActivity::class.java)
    intent.putExtra("workOrder", workOrder as java.io.Serializable)
    startActivityForResult(intent, REQUEST_CHANGE_STATUS)
}
```

**Handler untuk refresh data:**
```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (requestCode == REQUEST_CHANGE_STATUS && resultCode == android.app.Activity.RESULT_OK) {
        // Refresh data after status change
        refreshData()
    }
}
```

## Logika Status Button

### Status List
- **received** - Work order diterima
- **on progress** - Work order sedang dikerjakan
- **pending** - Work order ditunda
- **done** - Work order selesai

### Logika Disable Button
```kotlin
private fun isButtonDisabled(status: String, currentStatus: String): Boolean {
    return when (currentStatus) {
        "" -> false // New: semua aktif
        "received" -> status == "received"
        "on progress" -> status == "received" || status == "on progress"
        "pending" -> status == "received" || status == "pending"
        "done" -> true // Semua disable
        else -> false
    }
}
```

## API Integration

### Endpoint
- **URL:** `https://emshotels.net/apiKu/update_status_wo.php`
- **Method:** POST
- **Content-Type:** `application/x-www-form-urlencoded`

### Request Body
```
woId=123&status=received&userName=username&timeAccept=2024-01-01 10:00:00
```

**Parameter:**
- `woId` - ID work order
- `status` - Status baru
- `userName` - Username yang melakukan update
- `timeAccept` - Waktu accept (hanya untuk status "received")

## Fitur UI/UX

### 1. Work Order Information Card
- Menampilkan informasi penting work order
- Format yang konsisten dengan Flutter
- Current status ditampilkan dengan warna biru

### 2. Status Buttons
- Tombol besar dan mudah ditekan
- Visual feedback untuk status disabled
- Konfirmasi dialog untuk status penting (pending/done)

### 3. Loading State
- Overlay loading saat proses update
- Mencegah multiple click
- Feedback visual untuk user

### 4. Error Handling
- Toast message untuk success/error
- Graceful handling untuk network error
- Validasi input sebelum API call

## Konsistensi dengan Flutter

Implementasi Android mengikuti struktur dan fungsionalitas Flutter:

- **Layout:** Background terang, card design yang sama
- **Status Logic:** Logika disable button yang identik
- **API Call:** Format request yang sama
- **User Flow:** Navigasi dan feedback yang konsisten
- **Error Handling:** Toast message dan loading state

## Cara Penggunaan

1. **Dari Home Fragment:**
   - Klik card work order
   - Pilih menu "Follow Up"
   - Halaman change status akan terbuka

2. **Update Status:**
   - Pilih status yang diinginkan dari tombol yang tersedia
   - Untuk status "pending" atau "done" akan muncul dialog konfirmasi
   - Klik "Ya" untuk melanjutkan update

3. **Navigasi:**
   - Gunakan tombol back di toolbar untuk kembali
   - Setelah update berhasil, akan kembali ke Home dan data di-refresh

## Testing

- Build berhasil tanpa error
- Aplikasi berhasil diinstall ke device
- Navigasi dari Home berfungsi
- Status buttons berfungsi sesuai logika
- API call berfungsi dengan benar
- Loading state dan error handling berfungsi
