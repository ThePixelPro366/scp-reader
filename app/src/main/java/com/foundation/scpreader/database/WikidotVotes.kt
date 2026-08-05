package com.foundation.scpreader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's own Wikidot rating for a page (+1 / -1), keyed by the wiki pageId. Lets the
 * reader light up the arrow you picked and decide same-value vs change-of-mind without re-querying
 * the site. Cleared on logout.
 */
@Entity(tableName = "wikidot_votes")
data class WikidotVoteEntity(
    @PrimaryKey val pageId: String,
    val points: Int,
)

@Dao
interface WikidotVoteDao {
    @Query("SELECT points FROM wikidot_votes WHERE pageId = :pageId")
    fun observe(pageId: String): Flow<Int?>

    @Query("SELECT points FROM wikidot_votes WHERE pageId = :pageId")
    suspend fun get(pageId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WikidotVoteEntity)

    @Query("DELETE FROM wikidot_votes WHERE pageId = :pageId")
    suspend fun delete(pageId: String)

    @Query("DELETE FROM wikidot_votes")
    suspend fun clearAll()
}
