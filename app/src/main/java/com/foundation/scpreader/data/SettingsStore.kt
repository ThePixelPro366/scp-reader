package com.foundation.scpreader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foundation.scpreader.DownloadPref
import com.foundation.scpreader.HeroMode
import com.foundation.scpreader.ThemeMode
import com.foundation.scpreader.ui.theme.SeedKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences that persist across sessions. Defaults mirror the AppState initial values. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.Light,
    val dynamicColor: Boolean = true,
    val seed: SeedKey = SeedKey.Violet,
    val fontScale: Float = 1f,
    val loadImages: Boolean = true,
    val wifiOnly: Boolean = true,
    val downloadPref: DownloadPref = DownloadPref.Ask,
    val autoDownloadBookmarks: Boolean = false,
    val excludedClasses: Set<String> = emptySet(),
    val heroMode: HeroMode = HeroMode.ContinueReading,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Reads/writes [Settings] to a Preferences DataStore. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val seed = stringPreferencesKey("seed")
        val font = floatPreferencesKey("font_scale")
        val images = booleanPreferencesKey("load_images")
        val wifi = booleanPreferencesKey("wifi_only")
        val dlPref = stringPreferencesKey("download_pref")
        val autoDl = booleanPreferencesKey("auto_dl_bookmarks")
        val excluded = stringSetPreferencesKey("excluded_classes")
        val hero = stringPreferencesKey("hero_mode")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Light,
            dynamicColor = p[Keys.dynamic] ?: true,
            seed = p[Keys.seed]?.let { runCatching { SeedKey.valueOf(it) }.getOrNull() } ?: SeedKey.Violet,
            fontScale = p[Keys.font] ?: 1f,
            loadImages = p[Keys.images] ?: true,
            wifiOnly = p[Keys.wifi] ?: true,
            downloadPref = p[Keys.dlPref]?.let { runCatching { DownloadPref.valueOf(it) }.getOrNull() } ?: DownloadPref.Ask,
            autoDownloadBookmarks = p[Keys.autoDl] ?: false,
            excludedClasses = p[Keys.excluded] ?: emptySet(),
            heroMode = p[Keys.hero]?.let { runCatching { HeroMode.valueOf(it) }.getOrNull() } ?: HeroMode.ContinueReading,
        )
    }

    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[Keys.theme] = s.themeMode.name
            p[Keys.dynamic] = s.dynamicColor
            p[Keys.seed] = s.seed.name
            p[Keys.font] = s.fontScale
            p[Keys.images] = s.loadImages
            p[Keys.wifi] = s.wifiOnly
            p[Keys.dlPref] = s.downloadPref.name
            p[Keys.autoDl] = s.autoDownloadBookmarks
            p[Keys.excluded] = s.excludedClasses
            p[Keys.hero] = s.heroMode.name
        }
    }
}
