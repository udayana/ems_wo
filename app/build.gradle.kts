plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("kotlin-kapt")
}

android {
    namespace = "com.sofindo.ems"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sofindo.ems"
        minSdk = 23
        targetSdk = 35
        versionCode = 26
        versionName = "1.0.25"
        multiDexEnabled = true
        
        // Support for 16 KB page size (Required by Google Play Store starting Nov 2025)
        // This application has been configured to support 16 KB memory page sizes.
        // NDK ABI filters are configured to ensure compatibility with devices using 16 KB page sizes.
        // Only arm64-v8a is supported for 16KB page size compliance
        ndk {
            abiFilters.clear()
            abiFilters += listOf("arm64-v8a")
        }
    }
    
    // Set NDK version for 16KB page size support
    ndkVersion = "26.1.10909125"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../ems_wo.keystore")
            storePassword = "Jasm!n2025"
            keyAlias = "ems_wo"
            keyPassword = "Jasm!n2025"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
        // Support for 16 KB page size
        // This application has been configured to support 16 KB memory page sizes.
        // JNI libraries use modern packaging (not legacy) to ensure compatibility with 16 KB page size devices.
        // This is required for Google Play Store submissions starting November 2025.
        jniLibs {
            useLegacyPackaging = false
            // Exclude non-arm64-v8a ABIs to ensure only 16KB-compatible libraries are included
            // This is required for 16 KB page size support (Google Play requirement Nov 2025)
            excludes += listOf(
                // Exclude all non-arm64-v8a ABIs (required for 16KB support)
                "**/armeabi-v7a/**",
                "**/x86/**",
                "**/x86_64/**",
                // Exclude potentially incompatible native libraries
                "**/arm64-v8a/libimage_processing_util_jni.so",  // CameraX library that may not be 16KB aligned
                "**/libimage_processing_util_jni.so"  // Exclude from all ABIs
                // Note: libbarhopper_v3.so (ML Kit) removed from exclude since we now use ZXing (pure Java)
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.browser:browser:1.8.0")

    // Updated CameraX to latest version with 16KB page size support
    val cameraX = "1.4.0"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // ZXing Barcode Scanning - Pure Java implementation, 100% compatible with 16KB page size
    // No native libraries, so no 16KB alignment issues
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    
    // MultiDex support
    implementation("androidx.multidex:multidex:2.0.1")

    // Firebase
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.0")
    
    // Room Database for offline storage
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    
    // WorkManager for background sync
    val workVersion = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workVersion")
}
