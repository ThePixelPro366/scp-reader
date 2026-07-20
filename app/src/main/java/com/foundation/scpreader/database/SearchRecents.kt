package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An article opened *from the search screen*. Backs the search screen's "Recently viewed" list,
 * kept separate from the global [RecentEntity] recents so it only reflects search-driven opens.
 */
@Entity(tableName = "search_recents")
data class SearchRecentEntity(
    @PrimaryKey val url: String,
    val number: String,
    val title: String,
    val objectClass: String,
    val typeLabel: String,
    val tagsCsv: String,
    val rating: Int,
    val imageUrl: String?,
    val updatedAt: Long,
    val altTitle: String? = null, // descriptive listing name (added in v8); null for old rows
)

@Dao
interface SearchRecentDao {
    @Query("SELECT * FROM search_recents ORDER BY updatedAt DESC LIMIT 20")
    fun observeAll(): Flow<List<SearchRecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchRecentEntity)

    @Query("DELETE FROM search_recents")
    suspend fun clear()
}
