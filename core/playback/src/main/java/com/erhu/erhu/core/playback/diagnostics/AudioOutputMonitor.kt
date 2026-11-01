package com.erhu.erhu.core.playback.diagnostics

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Output devices as Android reports them, refreshed when a device is added or removed. Uses only
 * AudioManager (no Bluetooth or other permissions). The route for media is exact on Android 13+
 * (getAudioDevicesForAttributes); on older versions it is a best guess by usual priority and
 * labelled as an estimate. Device capabilities are what the device advertises, not necessarily the
 * format it is being sent right now.
 */
@Singleton
class AudioOutputMonitor
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)

        /** Current output information, then again on every device change. The callback is removed when collection stops. */
        fun observe(): Flow<OutputDiagnostics> =
            callbackFlow {
                val manager = audioManager
                if (manager == null) {
                    close()
                    return@callbackFlow
                }
                val callback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                            trySend(snapshot())
                        }

                        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                            trySend(snapshot())
                        }
                    }
                trySend(snapshot())
                manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
                awaitClose { manager.unregisterAudioDeviceCallback(callback) }
            }.distinctUntilChanged()

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun snapshot(): OutputDiagnostics {
            val manager = audioManager ?: return OutputDiagnostics(emptyList(), Provenance.ESTIMATE, emptyList(), null)
            val connected =
                try {
                    manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
                } catch (e: RuntimeException) {
                    emptyList()
                }
            val exactRoute =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching { manager.getAudioDevicesForAttributes(MEDIA_ATTRIBUTES) }.getOrNull()
                } else {
                    null
                }
            val route = exactRoute?.map(::describe) ?: OutputDevices.likelyRoute(connected.map(::describe))
            return OutputDiagnostics(
                route = route,
                routeSource = if (exactRoute != null) Provenance.PLATFORM else Provenance.ESTIMATE,
                connected = connected.map(::describe).distinct(),
                spatializer = spatializer(manager),
            )
        }

        private fun spatializer(manager: AudioManager): SpatializerInfo? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                runCatching { manager.spatializer.let { SpatializerInfo(it.isAvailable, it.isEnabled) } }.getOrNull()
            } else {
                null
            }

        private fun describe(device: AudioDeviceInfo): OutputDevice =
            OutputDevice(
                kind = OutputDevices.kindOf(device.type),
                typeName = OutputDevices.typeName(device.type),
                productName = device.productName?.toString()?.takeIf { it.isNotBlank() },
                sampleRatesHz = device.sampleRates.sorted().distinct(),
                channelCounts = device.channelCounts.sorted().distinct(),
                encodings = device.encodings.map { MediaFormatsAndroid.encodingName(it) }.distinct(),
            )

        private companion object {
            val MEDIA_ATTRIBUTES: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
        }
    }

/** Device-type mapping, separate from Android calls so it can be tested. */
object OutputDevices {
    @Suppress("CyclomaticComplexMethod")
    fun kindOf(type: Int): OutputKind =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputKind.SPEAKER
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.EARPIECE
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputKind.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            BLE_HEADSET, BLE_SPEAKER, BLE_BROADCAST,
            -> OutputKind.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, HDMI_EARC -> OutputKind.HDMI
            AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.HEARING_AID
            else -> OutputKind.OTHER
        }

    @Suppress("CyclomaticComplexMethod")
    fun typeName(type: Int): String =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Built-in speaker (safe)"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog line out"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (SCO, calls)"
            BLE_HEADSET -> "Bluetooth LE Audio headset"
            BLE_SPEAKER -> "Bluetooth LE Audio speaker"
            BLE_BROADCAST -> "Bluetooth LE Audio broadcast"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            HDMI_EARC -> "HDMI eARC"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
            AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
            else -> "Output type $type"
        }

    /** Before Android 13 the media route cannot be asked for: the usual priority (Bluetooth, USB, wired, speaker). */
    fun likelyRoute(devices: List<OutputDevice>): List<OutputDevice> {
        val order =
            listOf(OutputKind.BLUETOOTH, OutputKind.USB, OutputKind.WIRED, OutputKind.HDMI, OutputKind.HEARING_AID, OutputKind.SPEAKER)
        val kind = order.firstOrNull { k -> devices.any { it.kind == k } } ?: return emptyList()
        return listOfNotNull(
            devices.firstOrNull { it.kind == kind && it.typeName != "Bluetooth (SCO, calls)" } ?: devices.first { it.kind == kind },
        )
    }

    // Constants added in later API levels (inlined values, safe to compare on any version).
    private const val BLE_HEADSET = 26
    private const val BLE_SPEAKER = 27
    private const val HDMI_EARC = 29
    private const val BLE_BROADCAST = 30
}

/** Android AudioFormat encodings (device capabilities), named. */
internal object MediaFormatsAndroid {
    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    fun encodingName(encoding: Int): String =
        when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
            android.media.AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
            21 -> "PCM 24-bit packed"
            22 -> "PCM 32-bit"
            android.media.AudioFormat.ENCODING_AC3 -> "AC-3"
            android.media.AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
            android.media.AudioFormat.ENCODING_DTS -> "DTS"
            android.media.AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
            9 -> "MP3"
            10 -> "AAC LC"
            20 -> "Opus"
            else -> "Encoding $encoding"
        }
}
