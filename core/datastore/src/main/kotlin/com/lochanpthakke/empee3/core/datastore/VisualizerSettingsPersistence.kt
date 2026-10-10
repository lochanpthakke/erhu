package com.lochanpthakke.empee3.core.datastore

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.datastore.proto.VisualizerSettingsProto
import com.lochanpthakke.empee3.core.model.VisualizerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Visualizer preferences, kept across restarts in the app's settings store. */
interface VisualizerSettingsPersistence {
    fun observe(): Flow<VisualizerSettings>

    suspend fun save(settings: VisualizerSettings): AppResult<Unit>
}

class VisualizerSettingsPersistenceImpl(
    private val dataStore: DataStore<PlaybackStateProto>,
) : VisualizerSettingsPersistence {
    override fun observe(): Flow<VisualizerSettings> = dataStore.data.map { it.toVisualizerSettings() }.distinctUntilChanged()

    override suspend fun save(settings: VisualizerSettings): AppResult<Unit> =
        appResultOf {
            // Merge, don't replace: playback, theme and sound state live in the same proto.
            dataStore.updateData { settings.applyTo(it) }
            Unit
        }
}

/** Saved preferences, or the defaults for anything missing or unknown. */
fun PlaybackStateProto.toVisualizerSettings(): VisualizerSettings {
    if (!hasVisualizerSettings()) return VisualizerSettings()
    val proto = visualizerSettings
    val defaults = VisualizerSettings()
    return VisualizerSettings(
        mode = enumOrDefault(proto.mode, defaults.mode),
        sensitivity = enumOrDefault(proto.sensitivity, defaults.sensitivity),
        smoothing = enumOrDefault(proto.smoothing, defaults.smoothing),
        reduceMotion = proto.reduceMotion,
    )
}

fun VisualizerSettings.applyTo(state: PlaybackStateProto): PlaybackStateProto =
    state
        .toBuilder()
        .setVisualizerSettings(
            VisualizerSettingsProto
                .newBuilder()
                .setMode(mode.name)
                .setSensitivity(sensitivity.name)
                .setSmoothing(smoothing.name)
                .setReduceMotion(reduceMotion)
                .build(),
        ).build()

private inline fun <reified E : Enum<E>> enumOrDefault(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default
