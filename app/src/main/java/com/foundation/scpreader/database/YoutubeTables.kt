package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Cached SponsorBlock segments for a YouTube video, so sponsor-skipping can work without a network
 * round-trip (and offline, once the owning download stores its own copy). Created in v6; wired up
 * in a later phase.
 */
@Entity(tableName = "sponsor_segments")
data class SponsorSegmentEntity(
    @PrimaryKey val videoId: String,
    val segmentsJson: String,   // serialized list of {category, startSec, endSec}
    val fetchedAt: Long,
)

/**
 * Cached narration discovery index so audio availability survives offline and startup doesn't
 * re-scrape every launch. One row per known episode, keyed by stable [mediaId]. Created in v6;
 * populated in a later phase.
 */
@Entity(tableName = "narration_index")
data class NarrationIndexEntity(
    @PrimaryKey val mediaId: String,
    val scpNumber: Int?,
    val source: String,          // "YOUTUBE" | "PODCAST"
    val videoId: String?,
    val title: String,
    val durationSec: Int,
    val publishedMillis: Long,
    val thumbnailUrl: String?,
    val syncedAt: Long,
)

@Dao
interface SponsorSegmentDao {
    @Query("SELECT * FROM sponsor_segments WHERE videoId = :videoId")
    suspend fun get(videoId: String): SponsorSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SponsorSegmentEntity)
}

@Dao
interface NarrationIndexDao {
    @Query("SELECT * FROM narration_index")
    suspend fun getAll(): List<NarrationIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NarrationIndexEntity>)
}
