package com.lochanpthakke.empee3.core.model

/**
 * One track that actually started playing during the current listening session.
 * [entryId] is unique within the session, so the same song played twice gives two
 * distinct entries; it is never a queue position.
 */
data class QueueHistoryEntry(
    val entryId: Long,
    val song: Song,
    val playedAtMs: Long,
)
