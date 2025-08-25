# Implementasi Work Order Detail

## Deskripsi

Halaman Work Order Detail adalah halaman baru yang menampilkan detail lengkap dari work order. Halaman ini dapat diakses melalui menu "Detail" dari halaman Home dan Outbox. Implementasi ini mengikuti struktur dan fungsionalitas dari Flutter `work_order_detail_view.dart`.

## File yang Dibuat

### 1. `app/src/main/java/com/sofindo/ems/activities/WorkOrderDetailActivity.kt`

**Fitur:**
- Menampilkan detail lengkap work order dalam format yang sama dengan Flutter
- Mendukung tampilan foto single atau dual (before/after)
- Menghitung waktu yang dihabiskan (time spent)
- Format tanggal dan waktu yang konsisten
- Navigasi kembali ke halaman sebelumnya

**Fungsi Utama:**
```kotlin
// Populate work order details
private fun populateWorkOrderDetails()

// Setup photo section (single or dual photos)
private fun setupPhotoSection()

// Calculate time spent between start and done time
private fun calculateTimeSpent(startTime: String?, doneTime: String?)

// Format date time display
private fun formatDateTime(dateTimeStr: String?)
```

### 2. `app/src/main/res/layout/activity_work_order_detail.xml`

**Layout:**
- Background gelap (#424242) sesuai dengan Flutter
- Toolbar dengan tombol back
- ScrollView untuk konten yang panjang
- Layout detail dalam format label-value
- Section foto yang kondisional (single/dual)

**Komponen:**
- **Detail Fields:** WO ID, Job, Location, Category, Priority, Dept, To, Order by, Status
- **Time Fields:** Start Time, Accepted, Done, Spent, Done By
- **Remarks:** Conditional field (hanya muncul jika ada data)
- **Photo Section:** Single photo atau dual photos (Before/After)

### 3. `app/src/main/AndroidManifest.xml` (Dimodifikasi)

**Perubahan:**
- Menambahkan deklarasi WorkOrderDetailActivity
- Menggunakan theme yang konsisten dengan aplikasi

## Integrasi dengan Fragment

### HomeFragment dan OutboxFragment

**Perubahan pada fungsi `onDetailWorkOrder`:**
```kotlin
private fun onDetailWorkOrder(workOrder: Map<String, Any>) {
    // Navigate to work order detail activity
    val intent = android.content.Intent(context, com.sofindo.ems.activities.WorkOrderDetailActivity::class.java)
    intent.putExtra("workOrder", workOrder as java.io.Serializable)
    startActivity(intent)
}
```

## Fitur Detail Work Order

### 1. Informasi Dasar
- **WO ID:** Nomor work order
- **Job:** Deskripsi pekerjaan
- **Location:** Lokasi pekerjaan
- **Category:** Kategori work order
- **Priority:** Prioritas (LOW, HIGH, dll)
- **Dept:** Departemen
- **To:** Tujuan/departemen tujuan
- **Order by:** Pembuat work order
- **Status:** Status work order (NEW, DONE, dll)

### 2. Informasi Waktu
- **Start Time:** Waktu mulai
- **Accepted:** Waktu diterima
- **Done:** Waktu selesai
- **Spent:** Waktu yang dihabiskan (dihitung otomatis)
- **Done By:** Orang yang menyelesaikan

### 3. Informasi Tambahan
- **Remarks:** Catatan tambahan (conditional)

### 4. Foto
- **Single Photo:** Jika hanya ada satu foto (before atau after)
- **Dual Photos:** Jika ada foto before dan after
- **Photo Viewer:** Klik foto untuk melihat dalam ukuran penuh

## Perhitungan Time Spent

Implementasi mengikuti logika Flutter untuk menghitung waktu yang dihabiskan:

```kotlin
private fun calculateTimeSpent(startTime: String?, doneTime: String?): String {
    // Validasi input
    if (startTime.isNullOrEmpty() || doneTime.isNullOrEmpty()) {
        return "N/A"
    }
    
    // Check empty date format
    if (doneTime.contains("0000-00-00")) {
        return "-"
    }
    
    // Calculate difference
    val difference = done.time - start.time
    
    // Return appropriate format
    return when {
        totalMonths > 0 -> "$totalMonths month${if (totalMonths > 1) "s" else ""}"
        totalDays > 0 -> "$totalDays day${if (totalDays > 1) "s" else ""}"
        totalHours > 0 -> "$totalHours hour${if (totalHours > 1) "s" else ""}"
        totalMinutes > 0 -> "$totalMinutes minute${if (totalMinutes > 1) "s" else ""}"
        else -> "Less than 1 minute"
    }
}
```

## Format Tanggal dan Waktu

Format yang digunakan konsisten dengan Flutter:
- **Input:** "yyyy-MM-dd HH:mm:ss"
- **Output:** "dd MMM yyyy - HH:mm"
- **Empty Date:** Menampilkan "-" untuk tanggal kosong

## Photo Viewer Enhancement

### ImageViewerDialog (Dimodifikasi)

**Fitur Baru:**
- Mendukung parameter title untuk menampilkan label foto
- Title ditampilkan di pojok kiri atas saat foto dibuka
- Konsisten dengan implementasi Flutter

**Penggunaan:**
```kotlin
// Single photo without title
val dialog = ImageViewerDialog(context, imageUrl)

// Photo with title (Before/After)
val dialog = ImageViewerDialog(context, imageUrl, "Before")
```

## Cara Penggunaan

1. **Dari Home Fragment:**
   - Klik card work order
   - Pilih menu "Detail"
   - Halaman detail akan terbuka

2. **Dari Outbox Fragment:**
   - Klik card work order
   - Pilih menu "Detail"
   - Halaman detail akan terbuka

3. **Navigasi:**
   - Gunakan tombol back di toolbar untuk kembali
   - Atau gunakan tombol back sistem Android

## Konsistensi dengan Flutter

Implementasi Android mengikuti struktur dan fungsionalitas Flutter:

- **Layout:** Background gelap, format label-value yang sama
- **Data Display:** Semua field yang sama dengan Flutter
- **Photo Handling:** Single/dual photo dengan viewer
- **Time Calculation:** Logika yang sama untuk time spent
- **Date Formatting:** Format yang konsisten
- **Navigation:** Back button yang berfungsi sama

## Testing

- Build berhasil tanpa error
- Aplikasi berhasil diinstall ke device
- Navigasi dari Home dan Outbox berfungsi
- Photo viewer dengan title berfungsi
- Perhitungan time spent akurat
