# EMS Workorder - Android Kotlin App

A modern Android application for EMS (Enterprise Management System) Workorder management, built with Kotlin and following Material Design 3 principles.

## 📱 Project Overview

This is the Android version of the EMS Workorder application, designed to provide a native mobile experience for managing work orders, maintenance tasks, and asset management. The app is built based on the existing Flutter version to maintain consistency in functionality and user experience.

## 🏗️ Architecture

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **UI Framework**: Material Design 3
- **Networking**: Retrofit + OkHttp
- **JSON Parsing**: Moshi
- **Async Operations**: Kotlin Coroutines
- **Image Loading**: Android built-in ImageView
- **Camera**: CameraX for QR scanning

## 🎯 Current Progress

### ✅ Completed Features
- [x] **Project Setup** - Android project with Kotlin
- [x] **Login Screen** - Complete authentication UI
- [x] **API Integration** - Retrofit setup for PHP backend
- [x] **User Authentication** - Login with retry mechanism
- [x] **Material Design 3** - Modern UI components
- [x] **App Icons** - Custom EMS branding
- [x] **Error Handling** - Comprehensive error management
- [x] **Password Toggle** - Show/hide password functionality

### 🔄 In Progress
- [ ] **Main Navigation** - Bottom navigation setup
- [ ] **Home Dashboard** - Work order statistics
- [ ] **Work Order List** - Display work orders
- [ ] **Work Order Detail** - View and edit work orders

### 📋 Planned Features
- [ ] **QR Scanner** - Scan work order QR codes
- [ ] **Camera Integration** - Photo capture for documentation
- [ ] **Offline Support** - Local data caching
- [ ] **Push Notifications** - Real-time updates
- [ ] **User Profile** - Profile management
- [ ] **Settings** - App configuration

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (API Level 24)
- Kotlin 1.8+
- Gradle 8.0+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/udayana/ems_wo.git
   cd ems_wo
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Configure SDK**
   - Ensure Android SDK is properly configured
   - Set up Android SDK path in `local.properties`

4. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

## 🔧 Configuration

### API Configuration
The app connects to the EMS PHP backend at:
```
Base URL: https://emshotels.net/apiKu/
```

### Key Endpoints
- `POST /login.php` - User authentication
- `POST /check_login.php` - Verify login status
- `GET /baca_wo.php` - Get work orders
- `POST /submit_wo.php` - Submit work order

### Environment Setup
Create `local.properties` file:
```properties
sdk.dir=/path/to/your/android/sdk
```

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/sofindo/ems/
│   │   ├── auth/           # Authentication
│   │   ├── api/            # API services
│   │   ├── models/         # Data models
│   │   ├── camera/         # Camera functionality
│   │   └── MainActivity.kt # Main activity
│   ├── res/
│   │   ├── layout/         # UI layouts
│   │   ├── drawable/       # Images and icons
│   │   ├── values/         # Colors, strings, styles
│   │   └── mipmap/         # App icons
│   └── AndroidManifest.xml
├── build.gradle.kts        # App-level build config
└── proguard-rules.pro      # ProGuard rules

ems_flutter/                 # Reference Flutter project
├── lib/                    # Flutter source code
├── api/                    # PHP backend files
└── assets/                 # Flutter assets
```

## 🎨 UI/UX Design

### Design System
- **Primary Color**: `#1ABC9C` (Teal)
- **Secondary Color**: `#34495E` (Dark Blue)
- **Background**: `#F8F9FA` (Light Gray)
- **Surface**: `#F1F3F4` (Input Background)
- **Error**: `#E74C3C` (Red)

### Components
- **Material Design 3** components
- **Custom input fields** with filled style
- **Circular profile images** with shadows
- **Responsive layouts** for different screen sizes

## 🔌 API Integration

### Network Layer
- **Retrofit** for HTTP requests
- **OkHttp** for network client
- **Moshi** for JSON serialization
- **Coroutines** for async operations

### Authentication Flow
1. User enters email/phone and password
2. App sends credentials to `/login.php`
3. Server validates and returns user data
4. App stores user session and navigates to main screen

### Error Handling
- **Network errors** with retry mechanism
- **Validation errors** with user-friendly messages
- **Server errors** with appropriate fallbacks

## 📱 Screenshots

### Login Screen
- Modern Material Design 3 interface
- Custom EMS branding
- Password visibility toggle
- Error message display
- Retry mechanism for failed requests

## 🛠️ Development

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Follow Material Design guidelines

### Testing
- Unit tests for business logic
- UI tests for critical user flows
- API tests for backend integration

### Performance
- Optimize image loading
- Implement efficient data caching
- Minimize network requests
- Use lazy loading for lists

## 📄 License

This project is proprietary software for EMS (Enterprise Management System).

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📞 Support

For support and questions:
- Create an issue on GitHub
- Contact the development team
- Check the documentation

## 🔄 Version History

### v1.0.0 (Current)
- ✅ Login screen implementation
- ✅ API integration setup
- ✅ Material Design 3 UI
- ✅ Basic project structure

### Next Release (v1.1.0)
- 🔄 Main navigation
- 🔄 Home dashboard
- 🔄 Work order list
- 🔄 Work order detail

---

**Built with ❤️ using Kotlin and Material Design 3**
