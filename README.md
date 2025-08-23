# EMS WO - Android Application

## 📱 Project Overview
EMS WO (Work Order) adalah aplikasi Android untuk manajemen work order yang dibangun dengan Kotlin dan menggunakan Material Design 3.

## 🚀 Quick Start

### Prerequisites
- Android Studio (latest version)
- JDK 17
- Android SDK (API 24+)
- Git

### Project Setup
1. **Clone Repository**
   ```bash
   git clone <repository-url>
   cd ems_wo
   ```

2. **Open in Android Studio**
   - Buka Android Studio
   - Pilih "Open" (bukan "New Project")
   - Navigate ke folder: `/Users/gedeudayana/ems_wo`
   - Klik "Open"
   - Tunggu Gradle sync selesai

3. **Run Application**
   - Hubungkan Android device atau buat emulator
   - Tekan tombol Run (▶️) di Android Studio
   - Atau gunakan shortcut: `Shift + F10`

## 🔑 Login Credentials (Hard-coded for Testing)
- **Email:** `engineer@demo.com`
- **Password:** `123456`
- **Note:** Form sudah terisi otomatis, langsung klik Login

## 📚 Flutter Reference (WAJIB DIBACA)
**Setiap development HARUS mengikuti referensi Flutter:**
- **Lokasi:** `/Users/gedeudayana/Downloads/EMS-Workorder-main 2/`
- **File Penting:**
  - `lib/screens/main/home.dart` → HomeFragment
  - `lib/screens/main/outbox.dart` → OutboxFragment
  - `lib/screens/main/tambah_wo.dart` → AddWOFragment
  - `lib/screens/auth/profile_view.dart` → ProfileFragment
  - `lib/screens/main/main_tab_view.dart` → MainActivity navigation
- **Yang Harus Sama Persis:**
  - Layout UI dan styling
  - Function logic dan state management
  - API endpoints dan data flow
  - Navigation dan user experience

## 📁 Project Structure

```
ems_wo/
├── app/
│   ├── src/main/
│   │   ├── java/com/sofindo/ems/
│   │   │   ├── api/                    # API Services
│   │   │   │   ├── ApiService.kt       # API endpoints
│   │   │   │   └── RetrofitClient.kt   # HTTP client
│   │   │   ├── auth/                   # Authentication
│   │   │   │   └── LoginActivity.kt    # Login screen
│   │   │   ├── fragments/              # Main app screens
│   │   │   │   ├── HomeFragment.kt     # Dashboard
│   │   │   │   ├── OutboxFragment.kt   # Outbox
│   │   │   │   ├── AddWOFragment.kt    # Add work order
│   │   │   │   ├── MaintenanceFragment.kt # Maintenance
│   │   │   │   └── ProfileFragment.kt  # User profile
│   │   │   ├── models/                 # Data models
│   │   │   │   └── User.kt             # User model
│   │   │   ├── services/               # Business logic
│   │   │   │   └── UserService.kt      # User management
│   │   │   ├── camera/                 # Camera functionality
│   │   │   │   ├── CameraFragment.kt   # QR scanner
│   │   │   │   └── QrAnalyzer.kt       # QR code analyzer
│   │   │   ├── App.kt                  # Application class
│   │   │   └── MainActivity.kt         # Main activity
│   │   ├── res/
│   │   │   ├── layout/                 # UI layouts
│   │   │   ├── drawable/               # Icons & images
│   │   │   ├── values/                 # Colors, strings, styles
│   │   │   └── mipmap-*/               # App icons
│   │   └── AndroidManifest.xml         # App configuration
│   └── build.gradle.kts                # App dependencies
├── build.gradle.kts                    # Project configuration
└── README.md                           # This file
```

## 🎯 Features

### ✅ Implemented
- **Authentication**
  - Login dengan hard-coded credentials
  - User session management
  - Auto-fill login form

- **Navigation**
  - Bottom navigation dengan 5 tabs
  - Fragment-based navigation
  - Tab switching functionality

- **Screens**
  - **Home:** Dashboard dengan list work orders
  - **Outbox:** List outbox work orders
  - **Add WO:** Form tambah work order
  - **Maintenance:** Placeholder (Coming Soon)
  - **Profile:** User profile & settings

- **UI/UX**
  - Material Design 3
  - Dark/Light theme support
  - Responsive layouts
  - Pull-to-refresh
  - Search & filter functionality

### 🚧 In Progress / TODO
- RecyclerView adapters untuk list work orders
- Image loading untuk profile pictures
- Toast/Snackbar feedback
- Work order detail views
- Edit work order functionality
- Camera integration untuk QR scanning
- API integration (saat ini menggunakan mock data)

## 🛠 Development Workflow

### 1. Daily Development
```bash
# 1. Buka Android Studio
# 2. Open project: /Users/gedeudayana/ems_wo
# 3. Tunggu Gradle sync
# 4. Run aplikasi untuk test
# 5. Develop fitur baru
# 6. Test di device/emulator
# 7. Commit changes
```

### 2. Flutter Reference Comparison (WAJIB)
**Setiap kali masuk project, selalu bandingkan dengan referensi Flutter:**
- **Lokasi Referensi:** `/Users/gedeudayana/Downloads/EMS-Workorder-main 2/`
- **File Referensi:** `lib/screens/main/` dan `lib/screens/auth/`
- **Yang Dibandingkan:**
  - Layout UI (sama persis dengan Flutter)
  - Function logic (sama persis dengan Flutter)
  - Endpoint API (sama persis dengan Flutter)
  - Data flow dan state management

### 3. Implementation Standards
**Buat yang sama persis dengan Flutter:**
- **Layout:** Copy exact UI dari Flutter ke Android XML
- **Function:** Implementasi logic yang sama dengan Flutter
- **API Endpoints:** Gunakan endpoint yang sama dengan Flutter
- **Data Models:** Struktur data yang sama dengan Flutter
- **Navigation:** Flow yang sama dengan Flutter

### 4. Git Commit Strategy
**Simpan ke git setiap kali satu halaman selesai penuh:**
- **Commit setiap halaman lengkap:** Layout + Function + API
- **Commit message format:** `feat: complete [page_name] page`
- **Contoh commit:**
  ```bash
  git add .
  git commit -m "feat: complete home page with work order list"
  git commit -m "feat: complete profile page with user data"
  git commit -m "feat: complete add WO page with form validation"
  ```
- **Jangan commit:** Work in progress atau halaman yang belum selesai

### 5. Adding New Features (Following Flutter Reference)
1. **Analyze Flutter Reference First**
   - Buka file Flutter yang sesuai di `/Users/gedeudayana/Downloads/EMS-Workorder-main 2/lib/screens/`
   - Analisis layout, function, dan API endpoints
   - Catat semua fitur yang perlu diimplementasi

2. **Create Fragment** (jika perlu screen baru)
   - Buat file di `app/src/main/java/com/sofindo/ems/fragments/`
   - Buat layout di `app/src/main/res/layout/`
   - Copy exact UI dari Flutter ke Android XML
   - Update navigation di `MainActivity.kt`

3. **Add API Endpoints** (sama persis dengan Flutter)
   - Update `ApiService.kt` dengan endpoint yang sama
   - Gunakan parameter dan response yang sama
   - Test dengan mock data dulu

4. **Implement Function Logic** (sama persis dengan Flutter)
   - Copy logic dari Flutter ke Kotlin
   - Implementasi state management yang sama
   - Gunakan data flow yang sama

5. **Update UI** (sama persis dengan Flutter)
   - Modify layouts di `res/layout/` sesuai Flutter
   - Add icons di `res/drawable/` jika diperlukan
   - Update colors/styles di `res/values/` sesuai Flutter

### 3. Testing
```bash
# Build project
./gradlew build

# Run tests
./gradlew test

# Build APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

## 🔧 Configuration

### Build Configuration
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35
- **Java Version:** 17

### Dependencies
- **UI:** Material Design 3, AppCompat, ConstraintLayout
- **Networking:** Retrofit, OkHttp, Moshi
- **Async:** Kotlin Coroutines
- **Camera:** CameraX, ML Kit Barcode Scanning
- **Other:** SwipeRefreshLayout

## 📱 Testing

### Manual Testing
1. **Login Test**
   - Buka aplikasi
   - Form sudah terisi: `engineer@demo.com` / `123456`
   - Klik Login
   - Harus masuk ke MainActivity

2. **Navigation Test**
   - Test semua 5 tabs: Home, Outbox, Add WO, Maintenance, Profile
   - Pastikan fragment berubah dengan benar

3. **Feature Test**
   - Test search di Home/Outbox
   - Test filter dropdown
   - Test pull-to-refresh
   - Test form validation di Add WO

### Device Testing
- **Physical Device:** Samsung Galaxy A04s (SM-A045F)
- **Emulator:** API 30+ recommended
- **Test Scenarios:** Login, navigation, basic functionality

## 🐛 Common Issues & Solutions

### Build Issues
```bash
# Clean & rebuild
./gradlew clean
./gradlew build

# Invalidate caches in Android Studio
File > Invalidate Caches > Invalidate and Restart
```

### Runtime Issues
- **Login tidak berhasil:** Pastikan credentials `engineer@demo.com` / `123456`
- **Fragment tidak muncul:** Cek navigation di `MainActivity.kt`
- **API errors:** Saat ini menggunakan mock data, cek `ApiService.kt`

### Device Issues
- **Device tidak terdeteksi:** 
  ```bash
  adb devices
  adb kill-server && adb start-server
  ```

## 📋 Development Checklist

### Before Starting Work
- [ ] Buka Android Studio
- [ ] Open project: `/Users/gedeudayana/ems_wo`
- [ ] Tunggu Gradle sync selesai
- [ ] **Buka Flutter reference:** `/Users/gedeudayana/Downloads/EMS-Workorder-main 2/`
- [ ] **Bandingkan dengan Flutter:** Layout, function, dan API endpoints
- [ ] Test login dengan credentials
- [ ] Pastikan semua tabs berfungsi

### Before Committing
- [ ] Test semua fitur utama
- [ ] Build project tanpa error
- [ ] Test di device/emulator
- [ ] Update documentation jika perlu

### Before Release
- [ ] Complete feature testing
- [ ] Performance testing
- [ ] UI/UX review
- [ ] Build signed APK
- [ ] Update version number

## 📞 Support

### Development Team
- **Lead Developer:** [Your Name]
- **Project Manager:** [PM Name]
- **QA Tester:** [QA Name]

### Resources
- **API Documentation:** [API URL]
- **Design System:** [Figma URL]
- **Project Management:** [Jira/Trello URL]

## 📝 Changelog

### Version 1.7.3 (Current)
- ✅ Implemented bottom navigation
- ✅ Created all main fragments (Home, Outbox, Add WO, Maintenance, Profile)
- ✅ Added hard-coded login system
- ✅ Implemented Material Design 3 UI
- ✅ Added UserService for session management
- ✅ Created API service structure
- 🚧 TODO: RecyclerView adapters, image loading, API integration

### Version 1.7.2
- Initial project setup
- Basic login functionality
- Camera integration

---

**Last Updated:** August 23, 2025  
**Next Review:** August 30, 2025
