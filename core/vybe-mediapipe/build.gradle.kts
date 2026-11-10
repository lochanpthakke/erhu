plugins {
    alias(libs.plugins.android.library)
}

val proprietaryCandidate = providers.gradleProperty("proprietaryCandidate").map(String::toBoolean).getOrElse(true)

android {
    namespace = "com.erhu.erhu.core.vybe.mediapipe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

if (proprietaryCandidate) {
    extensions.configure<com.android.build.api.dsl.LibraryExtension> {
        // Built-in Kotlin uses Kotlin directories independently of Java directories.
        sourceSets.getByName("main").kotlin.directories.apply {
            clear()
            add("src/proprietaryCandidate/java")
        }
    }
}

dependencies {
    api(project(":core:vybe"))
    if (!proprietaryCandidate) implementation(libs.mediapipe.tasks.text)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
