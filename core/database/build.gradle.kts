@file:Suppress("MagicNumber")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.lochanpthakke.empee3.core.database"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore-proto"))

    implementation(libs.androidx.core.ktx)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.common)

    implementation(libs.jaudiotagger)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
}
