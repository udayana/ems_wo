# 🚀 EMS WO Android Development SOP

## 📋 **STANDARD OPERATING PROCEDURE (SOP)**

### **🎯 PRINSIP DASAR**
> **"TIRU HABIS YANG DI FLUTTER"** - Setiap implementasi Android harus identik dengan Flutter

---

## **📁 PROJECT STRUCTURE**
```
wo_kotlin/
├── app/src/main/
│   ├── java/com/sofindo/ems/
│   │   ├── models/          # Data classes (mirip Flutter models)
│   │   ├── api/            # Retrofit services (mirip Flutter api_service)
│   │   ├── adapter/        # RecyclerView adapters
│   │   ├── fragment/       # UI fragments
│   │   └── auth/           # Authentication
│   └── res/
│       ├── layout/         # XML layouts
│       ├── drawable/       # Icons & backgrounds
│       └── values/         # Colors, strings, etc.
```

---

## **🔄 DEVELOPMENT WORKFLOW**

### **1. ANALISIS FLUTTER DULU** ⚠️ **WAJIB**
```bash
# SEBELUM implement Android, WAJIB:
1. Cek ../MyFlutter/ems_flutter/lib/ untuk referensi
2. Baca file Flutter yang relevan
3. Copy endpoint API yang sama
4. Copy data structure yang sama
5. Copy UI layout yang sama
```

### **2. IMPLEMENTASI ANDROID**
```bash
# Setelah analisis Flutter:
1. Buat data models (sesuai Flutter)
2. Buat API service (copy endpoint Flutter)
3. Buat UI layouts (mirip Flutter)
4. Buat fragments/activities
5. Test build
```

### **3. TESTING & DEBUGGING**
```bash
# Setelah implementasi:
1. ./gradlew assembleDebug
2. ./gradlew installDebug
3. Test di device
4. Fix jika ada error
5. Commit jika sudah bagus
```

---

## **📱 UI/UX STANDARDS**

### **🎨 Colors (Sesuai Flutter)**
```xml
<!-- app/src/main/res/values/colors.xml -->
<color name="primary">#FF1ABC9C</color>        <!-- Flutter AppColors.primary -->
<color name="primary_dark">#FF16A085</color>   <!-- Flutter AppColors.primaryDark -->
<color name="error_red">#FFE74C3C</color>      <!-- Flutter AppColors.error -->
<color name="text_secondary">#FF7F8C8D</color> <!-- Flutter AppColors.textSecondary -->
```

### **📐 Layout Standards**
- **Padding**: 24dp (sesuai Flutter)
- **Border Radius**: 8dp (sesuai Flutter)
- **Card Elevation**: 2dp (sesuai Flutter)
- **Text Sizes**: 12sp, 14sp, 16sp (sesuai Flutter)

---

## **🔌 API INTEGRATION**

### **📡 Endpoints (Copy dari Flutter)**
```kotlin
// SELALU cek Flutter dulu:
// ../MyFlutter/ems_flutter/lib/services/api_service.dart

// Contoh endpoints yang sudah benar:
@GET("baca_wo.php")           // Flutter: baca_wo.php
@GET("get_all_statuses.php")  // Flutter: get_all_statuses.php  
@GET("search_wo.php")         // Flutter: search_wo.php
@POST("login.php")            // Flutter: login.php
```

### **📦 Data Models (Sesuai Flutter)**
```kotlin
// Copy structure dari Flutter models:
// ../MyFlutter/ems_flutter/lib/models/

@JsonClass(generateAdapter = true)
data class WorkOrder(
    @Json(name = "woId") val woId: String? = null,
    @Json(name = "nour") val nour: String? = null,
    // ... copy semua fields dari Flutter
)
```

---

## **🎯 FEATURE IMPLEMENTATION CHECKLIST**

### **✅ Login Screen**
- [ ] Icon user bulat (bukan logo EMS)
- [ ] Background putih (bukan light gray)
- [ ] Input fields dengan background light gray
- [ ] Password toggle visibility
- [ ] Error handling dengan retry 3x
- [ ] Navigasi ke MainActivity setelah login

### **✅ Home Screen**
- [ ] Search bar dengan debounce 500ms
- [ ] Filter dropdown dengan status counts
- [ ] Work order cards dengan before/after photos
- [ ] Infinite scroll pagination
- [ ] Empty state dengan icon
- [ ] Loading state

### **✅ Work Order Card**
- [ ] Alternating background colors
- [ ] Status badges dengan colors
- [ ] Priority badges
- [ ] Photo loading dengan Picasso
- [ ] Before/After image labels
- [ ] Click untuk detail, long press untuk change status

---

## **🔧 TECHNICAL STANDARDS**

### **📚 Dependencies (build.gradle.kts)**
```kotlin
// UI Components
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("androidx.cardview:cardview:1.0.0")
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

// Network
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Image Loading
implementation("com.squareup.picasso:picasso:2.8")

// JSON
implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
```

### **⚙️ Build Configuration**
```kotlin
android {
    compileSdk = 35
    minSdk = 24
    targetSdk = 35
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

---

## **🚨 ERROR HANDLING**

### **📱 Common Issues & Solutions**
```bash
# 1. "No work orders" muncul
- Cek endpoint API (harus sama dengan Flutter)
- Cek propID dan dept (ambil dari user login)
- Cek network permissions

# 2. Build error
- ./gradlew clean
- ./gradlew assembleDebug
- Cek dependencies

# 3. App crash
- adb logcat untuk debug
- Cek null safety
- Cek API response format
```

---

## **📝 GIT WORKFLOW**

### **🔒 Commit Rules**
```bash
# 1. JANGAN langsung push
# 2. Tunggu user bilang "bagus" dulu
# 3. Baru commit dan push

git add .
git commit -m "Descriptive message"
# TUNGGU: User approval
git push
```

### **📋 Commit Message Format**
```
Feature: Add home screen with work order list

- Implement HomeFragment with search and filter
- Add WorkOrder data model and API integration  
- Create RecyclerView adapter with card layout
- Add infinite scroll and empty states
- Copy endpoints from Flutter (baca_wo.php, etc.)
```

---

## **🎯 QUALITY ASSURANCE**

### **✅ Pre-Implementation Checklist**
- [ ] Sudah cek Flutter reference?
- [ ] Sudah copy endpoint yang sama?
- [ ] Sudah copy data structure yang sama?
- [ ] Sudah copy UI layout yang sama?

### **✅ Post-Implementation Checklist**
- [ ] Build successful?
- [ ] Install successful?
- [ ] UI sesuai Flutter?
- [ ] Functionality working?
- [ ] Error handling proper?

### **✅ Before Commit Checklist**
- [ ] User sudah bilang "bagus"?
- [ ] Semua features working?
- [ ] No obvious bugs?
- [ ] Code clean dan readable?

---

## **📞 SUPPORT REFERENCES**

### **🔗 Flutter Reference Paths**
```
../MyFlutter/ems_flutter/lib/
├── models/           # Data structures
├── services/         # API endpoints
├── screens/          # UI layouts
├── constants/        # Colors, strings
└── utils/           # Helper functions
```

### **🔗 Android Implementation Paths**
```
wo_kotlin/app/src/main/
├── java/com/sofindo/ems/
│   ├── models/       # Data classes
│   ├── api/         # Retrofit services
│   ├── fragment/    # UI fragments
│   └── adapter/     # RecyclerView adapters
└── res/
    ├── layout/      # XML layouts
    └── drawable/    # Icons & backgrounds
```

---

## **🎯 REMEMBER: TIRU HABIS YANG DI FLUTTER!**

**Setiap implementasi Android harus:**
1. **Identik dengan Flutter** - UI, functionality, behavior
2. **Copy endpoint yang sama** - Tidak pakai endpoint yang berbeda
3. **Copy data structure yang sama** - Models harus sama persis
4. **Copy UI layout yang sama** - Colors, spacing, typography

**JANGAN:**
- ❌ Pakai endpoint yang berbeda
- ❌ Buat UI yang berbeda
- ❌ Implement tanpa cek Flutter dulu
- ❌ Push sebelum user approve

**SELALU:**
- ✅ Cek Flutter reference dulu
- ✅ Copy yang sama persis
- ✅ Test dan fix
- ✅ Tunggu user approval
