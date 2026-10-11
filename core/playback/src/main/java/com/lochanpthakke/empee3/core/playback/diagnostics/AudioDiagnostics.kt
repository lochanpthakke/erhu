package com.lochanpthakke.empee3.core.playback.diagnostics

/** Where a displayed value comes from, so verified facts are never confused with metadata or guesses. */
enum class Provenance(
    val label: String,
) {
    /** Parsed from the file's own audio header (e.g. FLAC STREAMINFO, WAV fmt, MP3 frame header). */
    FILE_HEADER("File header"),

    /** Reported by Android's media extractor for the file's container. */
    CONTAINER("Container"),

    /** From the library index (MediaStore), written when the file was scanned. */
    LIBRARY("Library index"),

    /** Reported by the player (Media3) for what it is actually doing now. */
    PLAYER("Player"),

    /** Reported by Android for the output devices. */
    PLATFORM("Android"),

    /** Measured by the app from the audio it is playing. */
    MEASURED("Measured"),

    /** Calculated from settings; a prediction, not an observation. */
    ESTIMATE("Estimate"),
}

/** One displayed value with its origin. A missing value is null, never 0 or a guess. */
data class Fact(
    val value: String,
    val source: Provenance,
    val note: String? = null,
)

/** The playing file, as far as it can be established without decoding it. */
data class SourceDiagnostics(
    val title: String,
    val artist: String,
    val album: String,
    val container: Fact?,
    val codec: Fact?,
    val sampleRate: Fact?,
    val channels: Fact?,
    /** Only for formats that store one (FLAC, WAV); null otherwise, with [bitDepthNote] saying why. */
    val bitDepth: Fact?,
    val bitDepthNote: String?,
    val bitrate: Fact?,
    val bitrateMode: Fact?,
    val duration: Fact?,
    val fileSize: Fact?,
    /** Why some values are missing (file unreadable, no permission), or null. */
    val problem: String? = null,
)

/** A Media3 format, unknown fields kept as null (Media3's NO_VALUE), never 0. */
data class FormatSummary(
    val sampleMimeType: String?,
    val codecs: String?,
    val sampleRateHz: Int?,
    val channelCount: Int?,
    /** PCM encoding name, for raw audio formats only. */
    val pcmEncoding: String?,
    val averageBitrate: Int?,
    val peakBitrate: Int?,
)

/** What the player configured on the Android AudioTrack (after the app's processing). */
data class AudioTrackSummary(
    val encoding: String,
    val sampleRateHz: Int?,
    val channelCount: Int?,
    val offload: Boolean,
    val tunneling: Boolean,
    val bufferBytes: Int?,
)

/** The decoder in use, as reported by Media3 and Android's codec list. */
data class DecoderInfo(
    val name: String,
    /** Null when Android does not say (before API 29) or the codec is not in the list. */
    val hardwareAccelerated: Boolean? = null,
    val softwareOnly: Boolean? = null,
    val vendor: Boolean? = null,
    val maxInputChannels: Int? = null,
)

enum class PlayerActivity { PLAYING, PAUSED, BUFFERING, IDLE, ENDED }

/** The player's own report of the current audio. */
data class PlayerDiagnostics(
    val activity: PlayerActivity = PlayerActivity.IDLE,
    val speed: Float? = null,
    val inputFormat: FormatSummary? = null,
    val decoderName: String? = null,
    val audioTrack: AudioTrackSummary? = null,
    val underruns: Int = 0,
    val lastSinkError: String? = null,
)

enum class OutputKind(
    val label: String,
) {
    SPEAKER("Phone speaker"),
    EARPIECE("Earpiece"),
    WIRED("Wired headphones"),
    BLUETOOTH("Bluetooth"),
    USB("USB audio"),
    HDMI("HDMI"),
    HEARING_AID("Hearing aid"),
    OTHER("Other output"),
}

/** An output device as Android describes it. Empty lists mean "Android lists none", not "none supported". */
data class OutputDevice(
    val kind: OutputKind,
    val typeName: String,
    val productName: String?,
    val sampleRatesHz: List<Int>,
    val channelCounts: List<Int>,
    val encodings: List<String>,
)

data class SpatializerInfo(
    val available: Boolean,
    val enabled: Boolean,
)

data class OutputDiagnostics(
    /** Devices media audio is routed to; exact on Android 13+ ([routeSource] PLATFORM), a best guess before (ESTIMATE). */
    val route: List<OutputDevice>,
    val routeSource: Provenance,
    val connected: List<OutputDevice>,
    val spatializer: SpatializerInfo?,
)
