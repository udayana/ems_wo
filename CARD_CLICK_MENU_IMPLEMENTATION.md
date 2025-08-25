# Implementasi Card Click Menu

## Deskripsi Perubahan

Perubahan ini mengganti menu popup (titik tiga) dengan onClick pada card di halaman Home dan Outbox. Sekarang ketika user mengklik card work order, akan muncul menu popup yang sama dengan yang sebelumnya ada di tombol titik tiga.

## File yang Dimodifikasi

### 1. `app/src/main/java/com/sofindo/ems/adapters/WorkOrderAdapter.kt`

**Perubahan:**
- Menghilangkan tombol menu (titik tiga) dengan `holder.btnMenu.visibility = View.GONE`
- Mengubah click listener dari tombol menu menjadi click listener pada seluruh card
- Menyederhanakan ViewHolder dengan menghapus referensi ke view yang tidak ada di layout
- Menambahkan parameter `isHomeFragment` untuk membedakan menu Home dan Outbox
- Menambahkan callback `onFollowUpClick` untuk menu Follow Up

**Kode yang diubah:**
```kotlin
// Hide the menu button since we're using card click instead
holder.btnMenu.visibility = View.GONE

// Set click listener for entire card to show popup menu
holder.itemView.setOnClickListener {
    showPopupMenu(holder.itemView, workOrder)
}
```

### 2. `app/src/main/java/com/sofindo/ems/fragments/HomeFragment.kt`

**Perubahan:**
- Menambahkan callback untuk menu actions (detail, follow up)
- Menambahkan fungsi handler untuk setiap menu action
- Menggunakan menu khusus Home dengan opsi Detail dan Follow Up

**Fungsi baru yang ditambahkan:**
```kotlin
private fun onDetailWorkOrder(workOrder: Map<String, Any>) {
    // TODO: Navigate to work order detail
    val woNumber = workOrder["nour"]?.toString() ?: ""
    Toast.makeText(context, "Detail WO: $woNumber", Toast.LENGTH_SHORT).show()
}

private fun onFollowUpWorkOrder(workOrder: Map<String, Any>) {
    // TODO: Navigate to follow up work order
    val woNumber = workOrder["nour"]?.toString() ?: ""
    Toast.makeText(context, "Follow Up WO: $woNumber", Toast.LENGTH_SHORT).show()
}
```

### 3. `app/src/main/java/com/sofindo/ems/fragments/OutboxFragment.kt`

**Perubahan:**
- Memperbarui adapter initialization untuk menambahkan parameter yang hilang
- OutboxFragment tetap menggunakan menu lama (Detail, Edit, Delete)
- Menambahkan fungsi handler Follow Up untuk kompatibilitas (tidak digunakan)

### 4. `app/src/main/res/menu/work_order_home_menu.xml` (File Baru)

**Deskripsi:**
- Menu khusus untuk halaman Home dengan opsi Detail dan Follow Up
- Menggunakan icon yang sesuai untuk setiap opsi

### 5. `app/src/main/res/layout/fragment_outbox.xml` (Dimodifikasi)

**Perubahan:**
- Menghapus header "OUTBOX - Departemen Name" untuk memberikan area yang lebih luas
- Layout sekarang konsisten dengan HomeFragment yang tidak memiliki header

## Menu yang Tersedia

### Halaman Home
Menu popup yang muncul saat card diklik berisi:
1. **Detail** - Untuk melihat detail work order
2. **Follow Up** - Untuk melakukan follow up work order

### Halaman Outbox
Menu popup yang muncul saat card diklik berisi:
1. **Detail** - Untuk melihat detail work order
2. **Edit** - Untuk mengedit work order  
3. **Delete** - Untuk menghapus work order

## Cara Kerja

1. User mengklik card work order di halaman Home atau Outbox
2. Popup menu muncul dengan opsi yang berbeda:
   - **Home**: Detail dan Follow Up
   - **Outbox**: Detail, Edit, dan Delete
3. User memilih salah satu opsi
4. Fungsi handler yang sesuai dipanggil (saat ini menampilkan Toast, bisa dikembangkan lebih lanjut)

## Keuntungan

1. **UX yang lebih baik** - User tidak perlu mencari tombol kecil untuk mengakses menu
2. **Konsistensi** - Semua card memiliki perilaku yang sama
3. **Aksesibilitas** - Area klik yang lebih besar memudahkan user dengan keterbatasan motorik

## Catatan Implementasi

- Tombol menu (titik tiga) disembunyikan tapi tidak dihapus dari layout untuk memudahkan rollback jika diperlukan
- Semua fungsi handler saat ini menampilkan Toast sebagai placeholder
- Implementasi ini kompatibel dengan fitur yang sudah ada seperti infinite scroll, search, dan filter
- Menu berbeda untuk Home dan Outbox untuk memberikan pengalaman yang sesuai dengan konteks masing-masing halaman
- Header tab di OutboxFragment dihapus untuk memberikan area yang lebih luas dan konsistensi dengan HomeFragment
