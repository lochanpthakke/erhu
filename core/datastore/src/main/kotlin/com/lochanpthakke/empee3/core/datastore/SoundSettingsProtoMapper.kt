package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.EqPresetProto
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.datastore.proto.SoundSettingsProto
import com.lochanpthakke.empee3.core.model.PersistedEqPreset
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings

fun PersistedSoundSettings.toProto(): SoundSettingsProto =
    SoundSettingsProto
        .newBuilder()
        .setEqEnabled(eqEnabled)
        .addAllBandGains(bandGains)
        .setActivePresetId(activePresetId)
        .addAllCustomPresets(customPresets.map { it.toProto() })
        .setBassBoostEnabled(bassBoostEnabled)
        .setBassBoostStrength(bassBoostStrength)
        .setVirtualizerEnabled(virtualizerEnabled)
        .setVirtualizerStrength(virtualizerStrength)
        .setLoudnessEnabled(loudnessEnabled)
        .setLoudnessGainMb(loudnessGainMb)
        .addAllBandFrequencies(bandFrequencies)
        .addAllBandQs(bandQs)
        .addAllFilterTypes(filterTypes)
        .setActiveBandCount(activeBandCount)
        .setSelectedBandIndex(selectedBandIndex)
        .build()

fun SoundSettingsProto.toDomain(): PersistedSoundSettings =
    PersistedSoundSettings(
        eqEnabled = eqEnabled,
        bandGains = bandGainsList,
        activePresetId = activePresetId,
        customPresets = customPresetsList.map { it.toDomain() },
        bassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        loudnessEnabled = loudnessEnabled,
        loudnessGainMb = loudnessGainMb,
        bandFrequencies = bandFrequenciesList,
        bandQs = bandQsList,
        filterTypes = filterTypesList,
        activeBandCount = if (activeBandCount > 0) activeBandCount else 8,
        selectedBandIndex = selectedBandIndex,
    )

private fun PersistedEqPreset.toProto(): EqPresetProto =
    EqPresetProto
        .newBuilder()
        .setId(id)
        .setName(name)
        .addAllBandGains(bandGains)
        .addAllBandFrequencies(bandFrequencies)
        .addAllBandQs(bandQs)
        .addAllFilterTypes(filterTypes)
        .build()

private fun EqPresetProto.toDomain(): PersistedEqPreset =
    PersistedEqPreset(
        id = id,
        name = name,
        bandGains = bandGainsList,
        bandFrequencies = bandFrequenciesList,
        bandQs = bandQsList,
        filterTypes = filterTypesList,
    )

/** Writes the sound settings into [existing], keeping playback and theme fields. */
fun PersistedSoundSettings.applyTo(existing: PlaybackStateProto): PlaybackStateProto =
    existing
        .toBuilder()
        .setSoundSettings(toProto())
        .build()

/** Null when sound settings were never saved (fresh install / older app version). */
fun PlaybackStateProto.toSoundSettingsOrNull(): PersistedSoundSettings? = if (hasSoundSettings()) soundSettings.toDomain() else null
