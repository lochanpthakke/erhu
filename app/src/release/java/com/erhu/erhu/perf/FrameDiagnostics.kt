package com.erhu.erhu.perf

import androidx.activity.ComponentActivity

/** Release builds: no frame diagnostics (the debug build's version uses JankStats). */
internal object FrameDiagnostics {
    @Suppress("UNUSED_PARAMETER")
    fun install(activity: ComponentActivity) = Unit
}
