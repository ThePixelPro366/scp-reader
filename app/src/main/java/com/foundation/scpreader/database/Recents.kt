package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An article the user has opened. Backs the "Continue reading" / "Recently viewed" hero and
 * remembers the last scroll offset so the reader can resume where the user left off.
 */
@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val url: String,
    val number: String,
    val title: String,
    val objectClass: String,
    val typeLabel: String,
    val tagsCsv: String,
    val rating: Int,
    val imageUrl: String?,
    val scroll: Int,        // last vertical scroll offset in px (exact resume position)
    val progress: Float = 0f, // 0f‥1f reading fraction (scroll / content height), for the Continue-reading bar
    val updatedAt: Long,
)

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents ORDER BY updatedAt DESC LIMIT 20")
    fun observeAll(): Flow<List<RecentEntity>>

    @Query("SELECT * FROM recents WHERE url = :url")
    suspend fun get(url: String): RecentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentEntity)

    @Query("UPDATE recents SET scroll = :scroll, progress = :progress, updatedAt = :ts WHERE url = :url")
    suspend fun updateScroll(url: String, scroll: Int, progress: Float, ts: Long)
}
