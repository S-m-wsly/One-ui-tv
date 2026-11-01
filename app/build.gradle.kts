plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wes.oneuitv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wes.oneuitv"
        minSdk = 24
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    // The same committed key signs every build, so new APKs always update the old one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "oneuitv"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
