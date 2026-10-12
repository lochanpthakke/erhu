plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.lochanpthakke.empee3.feature.library"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:playback"))
    implementation(project(":core:sound"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.coil.compose)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
}
