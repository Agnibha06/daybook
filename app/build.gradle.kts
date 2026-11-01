plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.agnibha.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.agnibha.tracker"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    // One fixed key for every build (Android Studio and GitHub), so updates install
    // over the old app instead of forcing an uninstall that would wipe your data.
    // Personal-use key only; it is fine for it to live in the repo.
    signingConfigs {
        create("shared") {
            storeFile = file("daybook.keystore")
            storePassword = "daybook123"
            keyAlias = "daybook"
            keyPassword = "daybook123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
