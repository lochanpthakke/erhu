plugins {
    alias(libs.plugins.android.test)
}

/*
 * Macrobenchmarks of erhu's everyday journeys, run against the ".bench" package only (see the
 * app's "benchmark" build type), so they never touch an installed copy of the real app.
 *
 * Install and run with adb rather than connected*AndroidTest, which uninstalls the target
 * afterwards and would discard its seeded library: see benchmark/README.md.
 */
android {
    namespace = "com.erhu.erhu.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Targets the app's release-like "benchmark" build.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    // Only the benchmark variant is meaningful; the debug one (a debuggable test of a debuggable
    // app) is built only on request, for the debug-versus-release comparison.
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" || project.hasProperty("benchApp") }
}
