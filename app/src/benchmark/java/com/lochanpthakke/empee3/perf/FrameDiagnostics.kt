package com.lochanpthakke.empee3.perf

import androidx.activity.ComponentActivity

/** Benchmark builds: no frame diagnostics, so measurements see the release app (Macrobenchmark measures frames itself). */
internal object FrameDiagnostics {
    @Suppress("UNUSED_PARAMETER")
    fun install(activity: ComponentActivity) = Unit
}
