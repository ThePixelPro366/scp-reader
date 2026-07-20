package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** An article the user has bookmarked to their library. */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val number: String,
    val title: String,
    val objectClass: String,
    val typeLabel: String,
    val tagsCsv: String,
    val rating: Int,
    val imageUrl: String?,
    val addedAt: Long,
    val altTitle: String? = null, // descriptive listing name (added in v8); null for old rows
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun remove(url: String)
}
