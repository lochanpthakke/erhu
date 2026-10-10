package com.lochanpthakke.empee3.core.model

/**
 * Temporary A–B loop of the current track. [startMs] (A) and [endMs] (B) are set separately;
 * the loop can only be enabled once both are set with A before B. It belongs to one queue entry
 * of song [songId] and is dropped when another track starts. Never persisted.
 */
data class AbLoopState(
    val songId: Long,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val enabled: Boolean = false,
) {
    val isComplete: Boolean get() = startMs != null && endMs != null
}

/** Result of an A–B loop command, for the message shown to the user. */
enum class AbLoopEdit {
    OK,

    /** Nothing is playing. */
    NO_TRACK,

    /** A would not be at least a second before B. */
    A_NOT_BEFORE_B,

    /** Enabling needs both markers, A before B, on the current track. */
    NOT_READY,

    /** The player cannot schedule the loop. */
    UNSUPPORTED,
}
