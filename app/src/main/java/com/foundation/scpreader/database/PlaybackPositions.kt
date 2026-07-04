package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The last-known playback position for a narration episode, keyed by its stable [mediaId]
 * (source-tagged, e.g. "pod:<audioUrl>" or "yt:<videoId>"). Lets the player resume where the user
 * left off after the app is closed and reopened, independent of ephemeral stream URLs.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,   // 0 if unknown; used to reset near-complete episodes
    val updatedAt: Long,
)

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)
}
