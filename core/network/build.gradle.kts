import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

/**
 * Last.fm API key: `lastfm.apiKey=…` in local.properties (git-ignored) or the LASTFM_API_KEY
 * environment variable. Never committed; blank means Last.fm is reported as not configured.
 */
val lastFmApiKey: String =
    rootProject
        .file("local.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
        ?.getProperty("lastfm.apiKey")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("LASTFM_API_KEY")?.trim().orEmpty()

android {
    namespace = "com.lochanpthakke.empee3.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastFmApiKey.replace("\"", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:model"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    api(libs.jaudiotagger)
    // Reads LRCLIB's Lyricsfile (YAML) for word-timed lyrics; data only, no class instantiation.
    implementation(libs.snakeyaml.engine)
    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
