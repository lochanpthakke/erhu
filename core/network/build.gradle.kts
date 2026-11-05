import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// Provision only with licensed subtitle access; never commit credentials.
val musixmatchApiKey: String =
    rootProject
        .file("local.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
        ?.getProperty("musixmatch.apiKey")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("MUSIXMATCH_API_KEY")?.trim().orEmpty()

// Optional app-owner production access. Public test key is confined to debug builds.
val audioDbApiKey =
    rootProject
        .file("local.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } }.getProperty("theaudiodb.apiKey") }
        ?.trim()
        .orEmpty()
        .ifBlank { System.getenv("THEAUDIODB_API_KEY")?.trim().orEmpty() }

android {
    namespace = "com.erhu.erhu.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        val announcementUrl =
            providers.gradleProperty("erhu.announcements.url").orNull
                ?: System.getenv("ERHU_ANNOUNCEMENTS_URL").orEmpty()
        buildConfigField("String", "ANNOUNCEMENT_API_URL", groovy.json.JsonOutput.toJson(announcementUrl))
        buildConfigField("String", "THEAUDIODB_API_KEY", "\"${audioDbApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "MUSIXMATCH_API_KEY", "\"${musixmatchApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
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
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
