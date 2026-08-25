plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lochanpthakke.empee3.core.playback"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:common"))

    testImplementation(libs.junit)
}