package com.foundation.scpreader.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 -> v6: switch narration identity from the (ephemeral) audio URL to a stable, source-tagged
 * mediaId, and add the tables/columns the YouTube + SponsorBlock work needs. Hand-written so
 * existing bookmarks, downloads and playback positions are preserved (no destructive fallback).
 *
 * The CREATE TABLE statements below must match Room's generated schema exactly (column order,
 * NOT NULL, primary keys) or Room's post-migration validation will fail.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- playback_positions: re-key primary key audioUrl -> mediaId ---
        // Existing rows were all podcast episodes, so map audioUrl -> "pod:" + audioUrl.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playback_positions_new` (" +
                "`mediaId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaId`))"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO `playback_positions_new` (`mediaId`, `positionMs`, `durationMs`, `updatedAt`) " +
                "SELECT 'pod:' || `audioUrl`, `positionMs`, `durationMs`, `updatedAt` FROM `playback_positions`"
        )
        db.execSQL("DROP TABLE `playback_positions`")
        db.execSQL("ALTER TABLE `playback_positions_new` RENAME TO `playback_positions`")

        // --- downloads: new nullable narration-metadata columns ---
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `mediaId` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `source` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `videoId` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `sponsorSegmentsJson` TEXT")

        // --- new tables ---
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sponsor_segments` (" +
                "`videoId` TEXT NOT NULL, `segmentsJson` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`videoId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `narration_index` (" +
                "`mediaId` TEXT NOT NULL, `scpNumber` INTEGER, `source` TEXT NOT NULL, " +
                "`videoId` TEXT, `title` TEXT NOT NULL, `durationSec` INTEGER NOT NULL, " +
                "`publishedMillis` INTEGER NOT NULL, `thumbnailUrl` TEXT, `syncedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaId`))"
        )
    }
}

/** v6 -> v7: add the reading-progress fraction to recents (backs the Continue-reading bar). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recents` ADD COLUMN `progress` REAL NOT NULL DEFAULT 0")
    }
}

/**
 * v7 -> v8: add the descriptive listing name (`altTitle`) to every stored-article table, so the
 * Library/Downloads/Recents lists can show the SCP's real name as the primary label the same way
 * the live feed and search do. Nullable — old rows fall back to the wikidot title.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `altTitle` TEXT")
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `altTitle` TEXT")
        db.execSQL("ALTER TABLE `recents` ADD COLUMN `altTitle` TEXT")
        db.execSQL("ALTER TABLE `search_recents` ADD COLUMN `altTitle` TEXT")
    }
}
