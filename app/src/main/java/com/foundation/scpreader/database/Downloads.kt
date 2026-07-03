package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Download lifecycle used by the Downloads screen. */
object DlStatus {
    const val QUEUED = "QUEUED"
    const val ACTIVE = "ACTIVE"
    const val DONE = "DONE"
}

/**
 * A downloaded (or queued) archive entry. This table is the offline source of truth:
 * the Library and Downloads screens observe it, and the reader reads saved articles from it.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val url: String,
    val number: String,
    val title: String,
    val objectClass: String,
    val typeLabel: String,
    val tagsCsv: String,
    val rating: Int,
    val imageUrl: String?,
    val blocksJson: String?,     // serialized List<ContentBlock>, null until scraped
    val audioPath: String?,      // local file path once narration is saved
    val hasAudio: Boolean,
    val sizeBytes: Long,
    val status: String,
    val progress: Int,           // 0..100
    val updatedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE url = :url")
    suspend fun get(url: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress, updatedAt = :ts WHERE url = :url")
    suspend fun updateProgress(url: String, status: String, progress: Int, ts: Long)

    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun delete(url: String)
}

@Database(entities = [DownloadEntity::class, BookmarkEntity::class, RecentEntity::class, SearchRecentEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentDao(): RecentDao
    abstract fun searchRecentDao(): SearchRecentDao
}
