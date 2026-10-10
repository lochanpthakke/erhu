package com.lochanpthakke.empee3.core.model

enum class VisualizerMode {
    SPECTRUM_BARS,
    CIRCULAR_SPECTRUM,
    OSCILLOSCOPE,
}

/** How strongly the visualizer reacts: how much loudness range fills the display. */
enum class VisualizerSensitivity {
    LOW,
    NORMAL,
    HIGH,
}

/** How quickly levels follow the music. */
enum class VisualizerSmoothing {
    RESPONSIVE,
    BALANCED,
    SMOOTH,
}

/** The user's visualizer preferences. [reduceMotion]: a lower frame rate and calmer movement. */
data class VisualizerSettings(
    val mode: VisualizerMode = VisualizerMode.SPECTRUM_BARS,
    val sensitivity: VisualizerSensitivity = VisualizerSensitivity.NORMAL,
    val smoothing: VisualizerSmoothing = VisualizerSmoothing.BALANCED,
    val reduceMotion: Boolean = false,
)
