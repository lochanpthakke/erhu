/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Future Android releases from 1.0.18: proprietary terms in LICENSE.
 * Historical GPL grants are preserved in LICENSES/GPL-3.0-historical.txt.
 */

package com.erhu.erhu.feature.library

import android.content.Context
import com.erhu.erhu.core.network.tagger.FieldFixPlan
import com.erhu.erhu.core.network.tagger.FixField
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class PendingRepairs(
    val writes: Map<Long, TagWriteRequest> = emptyMap(),
    val renames: Map<Long, String> = emptyMap(),
    val plans: Map<Long, FieldFixPlan> = emptyMap(),
) {
    val ids: Set<Long> get() = writes.keys + renames.keys

    fun merge(
        phase: LookupPhaseResult,
        found: Map<Long, FieldFixPlan>,
    ) = copy(
        writes = writes + phase.writes.mapKeys { phase.queue[it.key].song.id },
        renames = renames + phase.renames.mapKeys { phase.queue[it.key].song.id },
        plans = plans + found,
    )
}

/** Private, atomic checkpoint for fetched edits. Artwork stays in separate files, not DataStore. */
internal class PendingRepairStore(
    context: Context,
) {
    private val directory = File(context.noBackupFilesDir, "metadata-repair")
    private val covers = mutableMapOf<Long, Pair<ByteArray, String>>()
    private val manifest get() = File(directory, "pending.json")

    fun save(pending: PendingRepairs) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create metadata repair checkpoint" }
        val retained = mutableSetOf<String>()
        val items = JSONArray()
        for (id in pending.ids) {
            val entry = JSONObject().put("id", id).put("rename", pending.renames[id])
            pending.writes[id]?.let { entry.put("write", encodeWrite(id, it, retained)) }
            pending.plans[id]?.let { plan ->
                entry.put("notes", JSONArray(plan.notes)).put("unfilled", JSONArray(plan.unfilled))
                entry.put(
                    "changes",
                    JSONArray(
                        plan.changes.map { change ->
                            JSONObject().put("field", change.field.name).put("old", change.oldValue).put("new", change.newValue)
                        },
                    ),
                )
            }
            items.put(entry)
        }
        write(
            manifest,
            JSONObject()
                .put("version", 1)
                .put("items", items)
                .toString()
                .toByteArray(Charsets.UTF_8),
        )
        // Only remove unreferenced files after the new manifest is durable.
        covers.keys.retainAll(pending.ids)
        directory.listFiles()?.filter { it.name.endsWith(".cover") && it.name !in retained }?.forEach { it.delete() }
    }

    private fun encodeWrite(
        id: Long,
        request: TagWriteRequest,
        retained: MutableSet<String>,
    ): JSONObject {
        val json =
            JSONObject()
                .put("uri", request.contentUri)
                .put("path", request.filePath)
                .put("title", request.title)
                .put("artist", request.artist)
                .put("album", request.album)
                .put("year", request.year)
                .put("releaseDate", request.releaseDate)
                .put("genre", request.genre)
                .put("trackNumber", request.trackNumber)
                .put("bpm", request.bpm)
                .put("lyrics", request.lyrics)
                .put("clearTitle", request.clearTitle)
                .put("clearArtist", request.clearArtist)
                .put("clearAlbum", request.clearAlbum)
                .put("clearYear", request.clearYear)
                .put("clearGenre", request.clearGenre)
                .put("clearArtwork", request.clearArtwork)
        request.artworkBytes?.let { bytes ->
            val name =
                covers[id]?.takeIf { it.first === bytes }?.second ?: run {
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    "$id-$hash.cover".also { covers[id] = bytes to it }
                }
            retained += name
            val cover = File(directory, name)
            if (!cover.exists()) write(cover, bytes)
            json.put("cover", name)
        }
        return json
    }

    fun load(): PendingRepairs {
        if (!manifest.isFile) return PendingRepairs()
        val json = JSONObject(manifest.readText(Charsets.UTF_8))
        require(json.getInt("version") == 1) { "Unsupported pending metadata format" }
        val items = json.getJSONArray("items")
        val writes = mutableMapOf<Long, TagWriteRequest>()
        val renames = mutableMapOf<Long, String>()
        val plans = mutableMapOf<Long, FieldFixPlan>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.getLong("id")
            item.text("rename")?.let { renames[id] = it }
            item.optJSONObject("write")?.let { w ->
                val cover =
                    w.text("cover")?.let { name ->
                        require(name.matches(Regex("-?\\d+-[a-f0-9]{64}\\.cover"))) { "Invalid checkpoint artwork" }
                        File(directory, name).readBytes()
                    }
                writes[id] =
                    TagWriteRequest(
                        contentUri = w.getString("uri"),
                        filePath = w.getString("path"),
                        title = w.text("title"),
                        artist = w.text("artist"),
                        album = w.text("album"),
                        year = w.number("year"),
                        releaseDate = w.text("releaseDate"),
                        genre = w.text("genre"),
                        trackNumber = w.number("trackNumber"),
                        bpm = w.number("bpm"),
                        lyrics = w.text("lyrics"),
                        artworkBytes = cover,
                        clearTitle = w.optBoolean("clearTitle"),
                        clearArtist = w.optBoolean("clearArtist"),
                        clearAlbum = w.optBoolean("clearAlbum"),
                        clearYear = w.optBoolean("clearYear"),
                        clearGenre = w.optBoolean("clearGenre"),
                        clearArtwork = w.optBoolean("clearArtwork"),
                    )
            }
            val changes = item.optJSONArray("changes") ?: JSONArray()
            plans[id] =
                FieldFixPlan(
                    writes[id],
                    (0 until changes.length()).map { j ->
                        changes.getJSONObject(j).let {
                            FieldFixPlan.Change(FixField.valueOf(it.getString("field")), it.text("old"), it.getString("new"))
                        }
                    },
                    item.strings("notes"),
                    item.strings("unfilled"),
                )
        }
        return PendingRepairs(writes, renames, plans)
    }

    private fun write(
        file: File,
        bytes: ByteArray,
    ) {
        val staged = File.createTempFile("repair-", ".tmp", directory)
        try {
            FileOutputStream(staged).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            staged.delete()
        }
    }

    private fun JSONObject.text(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.number(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.strings(key: String): List<String> =
        optJSONArray(key)
            ?.let { a ->
                (0 until a.length()).map(a::getString)
            }.orEmpty()
}
