plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI sets GITHUB_RUN_NUMBER; every pushed build gets a higher versionCode than the last.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

// Bump this when you start a new feature round; CI appends the run number (0.15.123).
val baseVersion = "0.15"

android {
    namespace = "com.squeve.redail"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.squeve.redail"
        minSdk = 28            // endCall() needs API 28+
        targetSdk = 34
        versionCode = if (runNumber != null) runNumber + 100 else 15
        versionName = if (runNumber != null) "$baseVersion.$runNumber" else "$baseVersion-dev"
    }

    // Same key on every build, so new APKs install over old ones.
    signingConfigs {
        getByName("debug") {
            storeFile = file("squeve-debug.store")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Optimized build: Compose is dramatically smoother than in a debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Installs the Compose baseline profiles on the device so first launches are fast.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (the Android stubs throw).
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
