package com.lochanpthakke.empee3.core.playback.transition

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.onError
import com.lochanpthakke.empee3.core.datastore.TransitionSettingsPersistence
import com.lochanpthakke.empee3.core.model.CollectionRef
import com.lochanpthakke.empee3.core.model.GlobalTransitionSettings
import com.lochanpthakke.empee3.core.model.QueueOrigin
import com.lochanpthakke.empee3.core.model.ResolvedTransition
import com.lochanpthakke.empee3.core.model.TransitionChoice
import com.lochanpthakke.empee3.core.model.TransitionConfig
import com.lochanpthakke.empee3.core.model.TransitionPairKey
import com.lochanpthakke.empee3.core.model.TransitionResolver
import com.lochanpthakke.empee3.core.model.TransitionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transition settings at every level: global master switch and default, per album, per playlist,
 * per pair. Each change touches only its own level and entry, applies at once and is saved once
 * (the editor saves on Save, never while dragging).
 */
@Singleton
class TransitionSettingsRepository
    @Inject
    constructor(
        private val persistence: TransitionSettingsPersistence,
        dispatchers: DispatcherProvider,
        private val logger: Logger,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
        private val _config = MutableStateFlow(TransitionConfig())

        val config: StateFlow<TransitionConfig> = _config.asStateFlow()

        init {
            scope.launch {
                try {
                    persistence.observe().collect { _config.value = it }
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.w(TAG, "Could not read transition settings; using defaults: ${e.message}", e)
                }
            }
        }

        fun resolve(
            outgoing: QueueOrigin?,
            incoming: QueueOrigin?,
        ): ResolvedTransition = TransitionResolver.resolve(_config.value, outgoing, incoming)

        fun setGlobal(settings: GlobalTransitionSettings) {
            val clean =
                settings.copy(
                    default =
                        settings.default.sanitized().takeIf { it != TransitionChoice.Inherit } ?: TransitionChoice.Off,
                )
            _config.update { it.copy(global = clean) }
            save { persistence.saveGlobal(clean) }
        }

        /** Only [collection] changes; every other album and playlist keeps its own setting. */
        fun setCollection(
            collection: CollectionRef,
            choice: TransitionChoice,
        ) {
            val clean = choice.sanitized()
            _config.update { config ->
                config.copy(
                    collections =
                        if (clean ==
                            TransitionChoice.Inherit
                        ) {
                            config.collections - collection
                        } else {
                            config.collections + (collection to clean)
                        },
                )
            }
            save { persistence.saveCollection(collection, clean) }
        }

        /** Only this exact pair changes. */
        fun setPair(
            pair: TransitionPairKey,
            choice: TransitionChoice,
        ) {
            val clean = choice.sanitized()
            _config.update { config ->
                config.copy(pairs = if (clean == TransitionChoice.Inherit) config.pairs - pair else config.pairs + (pair to clean))
            }
            save { persistence.savePair(pair, clean) }
        }

        private fun save(write: suspend () -> AppResult<Unit>) {
            scope.launch { write().onError { logger.w(TAG, "Could not save transition settings: ${it.message}") } }
        }

        private fun TransitionChoice.sanitized(): TransitionChoice =
            if (this is TransitionChoice.Custom) TransitionChoice.Custom(spec.sanitized()) else this

        private companion object {
            const val TAG = "TransitionSettings"
        }
    }

/** The spec a level would play, or null for Off / Inherit. */
fun TransitionChoice.specOrNull(): TransitionSpec? = (this as? TransitionChoice.Custom)?.spec
