import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lochanpthakke.empee3"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lochanpthakke.empee3"
        // Measurement builds (-PperfApp) install beside the real app instead of over it.
        if (project.hasProperty("perfApp")) applicationIdSuffix = ".perf"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // Optional device-specific sideload; omitted = the existing universal APK.
        // Keep every library for the selected ABI, including VYBE's two MediaPipe runtimes.
        providers.gradleProperty("targetAbi").orNull?.let { abi ->
            require(abi in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
                "Unsupported targetAbi '$abi'; use arm64-v8a, armeabi-v7a, x86 or x86_64, or omit it for universal."
            }
            ndk.abiFilters.add(abi)
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystorePropertiesFile.exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val properties = Properties()
                FileInputStream(keystorePropertiesFile).use { properties.load(it) }
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        // Measurement only (the :benchmark module): release code and R8, profileable, debug-signed,
        // and always its own package so it can never replace an installed copy of the app.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".bench"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        debug {
            // -PbenchApp: the same ".bench" package as a debug build, used to seed its data
            // (run-as) and to compare debug with release-like frame timing on identical data.
            if (project.hasProperty("benchApp")) applicationIdSuffix = ".bench"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.hilt.android.compiler)
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Frame diagnostics (perf/FrameDiagnostics.kt): debug builds only.
    debugImplementation(libs.androidx.metrics.performance)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(project(":core:playback"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:transfer"))
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
}
