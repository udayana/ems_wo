# Swipe Gesture Implementation untuk Outbox Page (iOS Style)

## Fitur yang Diimplementasikan

### 1. Swipe Gesture pada Work Order Cards (iOS Style)
- **Lokasi**: Halaman Outbox (`OutboxFragment`)
- **Fungsi**: Setiap card work order dapat di-swipe ke kiri untuk menampilkan action buttons
- **Implementasi**: Menggunakan `ItemTouchHelper` dan `SwipeableWorkOrderAdapter` dengan style iOS
- **Visual**: Gradient background dan rounded action buttons seperti di iPhone

### 2. Menu Aksi yang Tersedia
- **Edit** (✏️): Untuk mengedit work order dengan icon dan background biru
- **Delete** (🗑️): Untuk menghapus work order dengan icon dan background merah

### 3. Visual Feedback (iOS Style)
- **Gradient Background**: Background gradient dari merah ke biru saat swipe
- **Rounded Buttons**: Action buttons dengan corner radius dan warna iOS
- **Smooth Animation**: Animasi smooth saat swipe dan reset
- **iOS Dialog**: Dialog dengan style iOS untuk konfirmasi aksi

## File yang Dimodifikasi/Dibuat

### 1. `SwipeableWorkOrderAdapter.kt`
- Adapter baru yang mendukung swipe gesture
- Menggunakan `ItemTouchHelper.Callback` untuk mendeteksi swipe
- Menampilkan visual feedback saat swipe

### 2. `OutboxFragment.kt`
- Menggunakan `SwipeableWorkOrderAdapter` sebagai pengganti `WorkOrderAdapter`
- Menambahkan callback untuk handle edit dan delete actions
- Setup `ItemTouchHelper` untuk swipe gesture

### 3. `SwipeCallback.kt` (dalam SwipeableWorkOrderAdapter.kt)
- Class untuk menangani swipe gesture
- Menampilkan dialog menu saat swipe terdeteksi
- Menggambar visual feedback saat swipe

## Cara Penggunaan

1. **Buka halaman Outbox**
2. **Swipe ke kiri** pada card work order manapun
3. **Pilih aksi**:
   - **Edit**: Untuk mengedit work order
   - **Delete**: Untuk menghapus work order (dengan konfirmasi)

## Implementasi Teknis

### Swipe Detection
```kotlin
override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
    val swipeFlags = ItemTouchHelper.START // Hanya swipe ke kiri
    return makeMovementFlags(0, swipeFlags)
}
```

### Visual Feedback (iOS Style)
```kotlin
override fun onChildDraw(c: Canvas, ...) {
    // Gradient background seperti iOS
    val backgroundGradient = LinearGradient(
        itemView.right.toFloat() + dX,
        itemView.top.toFloat(),
        itemView.right.toFloat(),
        itemView.bottom.toFloat(),
        intArrayOf(
            Color.parseColor("#FF3B30"), // Merah untuk delete
            Color.parseColor("#007AFF")  // Biru untuk edit
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    paint.shader = backgroundGradient
    
    // Rounded action buttons
    drawIOSStyleActionButtons(c, itemView, dX, paint)
    
    // Smooth animation
    itemView.translationX = dX * 0.3f
}
```

### Action Handling (iOS Style)
```kotlin
private fun showIOSStyleActions(context: Context, position: Int) {
    val dialog = AlertDialog.Builder(context, R.style.IOSStyleDialog)
        .setTitle("Work Order Actions")
        .setItems(arrayOf("✏️ Edit", "🗑️ Delete")) { _, which ->
            when (which) {
                0 -> adapter.onEdit(position)
                1 -> adapter.onDelete(position)
            }
        }
        .setNegativeButton("Cancel", null)
        .create()
    dialog.show()
}
```

## Status Implementasi

- ✅ **Swipe Gesture (iOS Style)**: Berhasil diimplementasikan dengan visual yang lebih baik
- ✅ **Gradient Background**: Background gradient merah-biru seperti iOS
- ✅ **Rounded Action Buttons**: Buttons dengan corner radius dan warna iOS
- ✅ **Smooth Animation**: Animasi smooth saat swipe dan reset
- ✅ **iOS Style Dialog**: Dialog dengan style iOS dan emoji icons
- ✅ **Edit Action**: Callback tersedia (perlu implementasi navigasi)
- ✅ **Delete Action**: Callback tersedia dengan konfirmasi dialog
- ⏳ **API Integration**: Perlu implementasi untuk edit dan delete

## Catatan

- Fitur ini hanya tersedia di halaman Outbox
- Swipe hanya berfungsi ke arah kiri
- Visual feedback menggunakan style iOS dengan gradient dan rounded buttons
- Smooth animation saat swipe dan reset
- Delete action memerlukan konfirmasi sebelum eksekusi
- Edit action saat ini hanya menampilkan toast (perlu implementasi navigasi ke halaman edit)
- Dialog menggunakan style iOS dengan emoji icons
