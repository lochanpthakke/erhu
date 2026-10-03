package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kept across scans: explicit alias evidence (manual or external). Empty until a later plan fills it. */
@Entity(tableName = "artist_alias")
data class ArtistAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_key")
    val aliasKey: String,
    @ColumnInfo(name = "canonical_key")
    val canonicalKey: String,
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
