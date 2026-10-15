plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Genres: the library's stored genre list ("Electronic; Synth-pop") parsed the same way everywhere.
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
