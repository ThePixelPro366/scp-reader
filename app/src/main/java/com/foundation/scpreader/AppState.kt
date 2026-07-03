package com.foundation.scpreader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.foundation.scpreader.data.Article
import com.foundation.scpreader.data.Episode
import com.foundation.scpreader.data.ScpItem
import com.foundation.scpreader.data.ScpRepository
import com.foundation.scpreader.data.Settings
import com.foundation.scpreader.data.SettingsStore
import kotlinx.coroutines.flow.first
import com.foundation.scpreader.database.DlStatus
import com.foundation.scpreader.database.DownloadEntity
import com.foundation.scpreader.playback.PlayerController
import com.foundation.scpreader.ui.theme.SeedKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Screen { Home, Search, Library, Downloads, Settings }
enum class ThemeMode { Light, Auto, Dark }
enum class ReaderDlState { Idle, Downloading, Done }
enum class RandomType { Scp, Tale, Goi }
enum class RandomMode { Pool, TopRated, Entire, Series }
enum class DownloadPref { TextOnly, TextAudio, AudioOnly, Ask }
enum class HeroMode { ContinueReading, ScpOfTheDay, Trending, RecentlyViewed }
enum class SortMode { Relevance, Rating, Newest }

data class ReaderDl(val state: ReaderDlState = ReaderDlState.Idle, val pct: Int = 0, val audio: Boolean = false)

/**
 * Single state holder for the app (Compose-idiomatic UDF via mutableStateOf). Owns UI/preference
 * state and coordinates the [ScpRepository] for live browse/search/article/download data.
 */
class AppState(
    val repo: ScpRepository,
    val player: PlayerController,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private var settingsLoaded = false

    // ---- navigation / prefs ----
    var screen by mutableStateOf(Screen.Home)
    var readerItem by mutableStateOf<ScpItem?>(null)
    var randomOpen by mutableStateOf(false)

    var themeMode by mutableStateOf(ThemeMode.Light)
    var systemDark by mutableStateOf(false)
    var dynamicColor by mutableStateOf(true)
    var seed by mutableStateOf(SeedKey.Violet)

    var fontScale by mutableStateOf(1f)
    var loadImages by mutableStateOf(true)
    var wifiOnly by mutableStateOf(true)
    var downloadPref by mutableStateOf(DownloadPref.Ask)
    var autoDownloadBookmarks by mutableStateOf(false)

    var searchQuery by mutableStateOf("")
    var typeFilter by mutableStateOf("all")
    var classFilter by mutableStateOf("any")
    var activeTags by mutableStateOf(listOf<String>())
    var searchFiltersOpen by mutableStateOf(false)
    var searchSort by mutableStateOf(SortMode.Relevance)
    var audioOnly by mutableStateOf(false)
    var libTab by mutableStateOf("all")

    // Full-screen narration player overlay.
    var playerFullScreen by mutableStateOf(false)

    var randomType by mutableStateOf(RandomType.Scp)
    var randomMode by mutableStateOf(RandomMode.Pool)
    var randomTags by mutableStateOf(listOf("humanoid"))
    var randomTagInput by mutableStateOf("")
    var randomCount by mutableStateOf(50)
    var randomSeries by mutableStateOf(1)

    var heroMode by mutableStateOf(HeroMode.ContinueReading)
    var excludedClasses by mutableStateOf(setOf<String>())

    var downloadsPaused by mutableStateOf(false)
    var readerMenuOpen by mutableStateOf(false)

    // ---- live data ----
    var feed by mutableStateOf<List<ScpItem>>(emptyList()); private set
    var feedLoading by mutableStateOf(false); private set
    var feedError by mutableStateOf<String?>(null); private set
    private var feedRaw = listOf<ScpItem>()
    private var feedCursor: String? = null
    var feedHasMore by mutableStateOf(false); private set
    private var loadedTag = ""

    var searchResults by mutableStateOf<List<ScpItem>>(emptyList()); private set
    var searchLoading by mutableStateOf(false); private set
    private var searchRaw = listOf<ScpItem>()
    private var searchJob: Job? = null

    var article by mutableStateOf<Article?>(null); private set
    var articleLoading by mutableStateOf(false); private set

    var episodes by mutableStateOf<List<Episode>>(emptyList()); private set
    var heroRefreshing by mutableStateOf(false); private set
    // hero-slot content (driven by heroMode)
    var scpOfDay by mutableStateOf<ScpItem?>(null); private set
    var trending by mutableStateOf<ScpItem?>(null); private set
    var recentlyViewed by mutableStateOf<List<ScpItem>>(emptyList()); private set
    var searchRecentlyViewed by mutableStateOf<List<ScpItem>>(emptyList()); private set
    val continueItem: ScpItem? get() = recentlyViewed.firstOrNull()
    // Reader scroll restore: offset to jump to when the current article's content is laid out.
    var readerScrollRestore by mutableStateOf(0); private set
    var readerScrollConsumed by mutableStateOf(false)
    var bookmarks by mutableStateOf<List<com.foundation.scpreader.database.BookmarkEntity>>(emptyList()); private set
    var bookmarkedUrls by mutableStateOf<Set<String>>(emptySet()); private set
    var downloads by mutableStateOf<List<DownloadEntity>>(emptyList()); private set
    var tagVocab by mutableStateOf(com.foundation.scpreader.data.FALLBACK_TAG_VOCAB); private set

    val isDark: Boolean
        get() = themeMode == ThemeMode.Dark || (themeMode == ThemeMode.Auto && systemDark)

    init {
        viewModelScope.launch { repo.downloads.collect { downloads = it; redecorate() } }
        viewModelScope.launch { repo.bookmarks.collect { bookmarks = it; bookmarkedUrls = it.map { b -> b.url }.toSet() } }
        viewModelScope.launch { repo.recents.collect { recentlyViewed = repo.decorate(it) } }
        viewModelScope.launch { repo.searchRecents.collect { searchRecentlyViewed = repo.decorate(it) } }
        viewModelScope.launch { repo.tagVocab.collect { tagVocab = it } }
        viewModelScope.launch { episodes = repo.episodes(); redecorate() }
        // Load persisted settings first, then kick off the feed/hero, then persist future changes.
        viewModelScope.launch {
            applySettings(settingsStore.settings.first())
            settingsLoaded = true
            loadFeed()
            loadHero()
            androidx.compose.runtime.snapshotFlow { currentSettings() }.collect { settingsStore.save(it) }
        }
    }

    private fun applySettings(s: Settings) {
        themeMode = s.themeMode; dynamicColor = s.dynamicColor; seed = s.seed
        fontScale = s.fontScale; loadImages = s.loadImages; wifiOnly = s.wifiOnly
        downloadPref = s.downloadPref; autoDownloadBookmarks = s.autoDownloadBookmarks
        excludedClasses = s.excludedClasses; heroMode = s.heroMode
        repo.setWifiOnly(s.wifiOnly)
    }

    private fun currentSettings() = Settings(
        themeMode, dynamicColor, seed, fontScale, loadImages, wifiOnly,
        downloadPref, autoDownloadBookmarks, excludedClasses, heroMode,
    )

    private fun daySeed(): Long {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 1000L + cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** Loads the content the hero slot needs (SCP-of-the-Day + Trending). */
    fun loadHero() {
        viewModelScope.launch { if (scpOfDay == null) scpOfDay = repo.scpOfDay(daySeed(), excludedClasses) }
        viewModelScope.launch { if (trending == null) trending = repo.trendingItem() }
    }

    fun selectHeroMode(m: HeroMode) { heroMode = m; loadHero() }

    // ---- feed ----
    private fun feedTagFor(type: String) = when (type) {
        "tale" -> "tale"; "goi" -> "goi-format"; else -> "scp"
    }

    /** All/SCP → an infinite "Random entries" discover feed; Tales/GoI → ranked CROM listing. */
    private val feedIsRandom: Boolean get() = typeFilter == "all" || typeFilter == "scp"

    fun loadFeed(reset: Boolean = true) {
        if (reset) { feedRaw = emptyList(); feedCursor = null; feed = emptyList() }
        feedLoading = true; feedError = null
        viewModelScope.launch {
            if (feedIsRandom) {
                runCatching { repo.randomItems(12, excludedClasses) }
                    .onSuccess { items ->
                        feedRaw = (feedRaw + items).distinctBy { it.url }
                        feed = repo.decorate(feedRaw)
                        feedHasMore = true
                    }
                    .onFailure { feedError = it.message ?: "Couldn't load random entries" }
            } else {
                runCatching { repo.topRated(tag = feedTagFor(typeFilter), after = feedCursor) }
                    .onSuccess { page ->
                        feedRaw = feedRaw + page.items
                        feedCursor = page.endCursor
                        feedHasMore = page.hasNext
                        feed = repo.decorate(feedRaw)
                    }
                    .onFailure { feedError = it.message ?: "Couldn't load the archive" }
            }
            feedLoading = false
            heroRefreshing = false
        }
    }

    fun loadMoreFeed() { if (!feedLoading && feedHasMore) loadFeed(reset = false) }

    /** Pull-to-refresh on Home: pull fresh random entries and refresh the trending hero. */
    fun refreshDiscover() {
        heroRefreshing = true
        viewModelScope.launch { trending = repo.trendingItem() }
        loadFeed(reset = true)
    }

    fun toggleExcludedClass(cls: String) {
        excludedClasses = if (excludedClasses.contains(cls)) excludedClasses - cls else excludedClasses + cls
    }
    fun selectRandomSeries(n: Int) { randomSeries = n }

    fun setType(t: String) {
        if (typeFilter == t) return
        typeFilter = t
        if (screen == Screen.Home) loadFeed()
        applySearchFilters()
    }

    // ---- search ----
    fun onSearchQuery(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.isBlank()) { searchRaw = emptyList(); searchResults = emptyList(); searchLoading = false; return }
        searchLoading = true
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(280) // debounce
            runCatching { repo.search(q) }
                .onSuccess { searchRaw = it; applySearchFilters() }
                .onFailure { searchRaw = emptyList(); searchResults = emptyList() }
            searchLoading = false
        }
    }

    /** Reset all search filters (type/class/tags/audio) back to their defaults. */
    fun clearSearchFilters() {
        typeFilter = "all"; classFilter = "any"; activeTags = emptyList(); audioOnly = false
        applySearchFilters()
    }

    fun toggleAudioOnly() { audioOnly = !audioOnly; applySearchFilters() }

    fun selectSearchSort(m: SortMode) { searchSort = m; applySearchFilters() }

    fun clearSearchRecents() { viewModelScope.launch { repo.clearSearchRecents() } }

    fun applySearchFilters() {
        val filtered = repo.decorate(searchRaw).filter { item ->
            (typeFilter == "all" || item.typeLabel.equals(typeFilter, true) ||
                (typeFilter == "scp" && item.typeLabel == "SCP") ||
                (typeFilter == "tale" && item.typeLabel == "Tale") ||
                (typeFilter == "goi" && item.typeLabel == "GoI")) &&
                (classFilter == "any" || item.objectClass == classFilter) &&
                (activeTags.isEmpty() || activeTags.any { item.tags.contains(it) }) &&
                (!audioOnly || item.podcast)
        }
        searchResults = when (searchSort) {
            SortMode.Relevance -> filtered // keep the search's own relevance order
            SortMode.Rating -> filtered.sortedByDescending { it.rating }
            SortMode.Newest -> filtered.sortedByDescending { it.createdAt ?: "" }
        }
    }

    // Quick preview under the search field: the top of the filtered + sorted results.
    val searchSuggestions: List<ScpItem>
        get() = if (searchQuery.isBlank()) emptyList() else searchResults.take(6)

    // ---- reader ----
    fun openReaderItem(item: ScpItem) {
        // Search-scoped history: only articles opened while on the search screen.
        if (screen == Screen.Search) viewModelScope.launch { repo.recordSearchRecent(item) }
        readerItem = item; readerMenuOpen = false; article = null; articleLoading = true
        readerScrollRestore = 0; readerScrollConsumed = false
        viewModelScope.launch { repo.recordRecent(item) }
        viewModelScope.launch { readerScrollRestore = repo.recentScroll(item.url) }
        viewModelScope.launch {
            if (episodes.isEmpty()) episodes = repo.episodes()
            runCatching { repo.article(item) }
                .onSuccess { article = it; readerItem = it.item }
                .onFailure { articleLoading = false }
            articleLoading = false
        }
    }

    /**
     * Persist the reader's scroll offset for [url]. Takes the url explicitly because saving
     * happens as the reader closes, by which point [readerItem] has already been cleared.
     */
    fun saveReaderScroll(url: String, offset: Int) {
        viewModelScope.launch { repo.saveRecentScroll(url, offset) }
    }

    /** Open a tapped in-article link: SCP pages route to the in-app reader, else to the browser. */
    fun openLink(url: String, context: android.content.Context) {
        val slug = url.substringAfterLast('/').substringBefore('#').substringBefore('?').lowercase()
        if (Regex("^scp-\\d+[a-z0-9-]*$").matches(slug)) {
            val num = "SCP-" + slug.removePrefix("scp-").uppercase()
            openReaderItem(ScpItem(url = url.replaceFirst("https://", "http://"), number = num, title = num, objectClass = "Unknown", typeLabel = "SCP", tags = emptyList()))
        } else {
            com.foundation.scpreader.ui.screens.openUrl(context, url)
        }
    }

    fun closeReader() { readerItem = null; readerMenuOpen = false; article = null }

    fun toggleReaderMenu() { readerMenuOpen = if (readerDl.state == ReaderDlState.Downloading) false else !readerMenuOpen }
    fun closeReaderMenu() { readerMenuOpen = false }

    /** Reader download button state, derived from the offline table. */
    val readerDl: ReaderDl
        get() {
            val item = readerItem ?: return ReaderDl()
            val e = downloads.firstOrNull { it.url == item.url } ?: return ReaderDl()
            return when (e.status) {
                DlStatus.DONE -> ReaderDl(ReaderDlState.Done, 100, e.hasAudio)
                else -> ReaderDl(ReaderDlState.Downloading, e.progress, e.hasAudio)
            }
        }

    fun startDownload(audio: Boolean) {
        readerMenuOpen = false
        readerItem?.let { repo.enqueue(it, audio) }
    }

    private fun readerHasEpisode(): Boolean = episodeForReader() != null
    private fun prefWantsAudio(): Boolean =
        readerHasEpisode() && (downloadPref == DownloadPref.TextAudio || downloadPref == DownloadPref.AudioOnly)

    /** Reader download-button tap: honors the download-content preference; only asks when audio exists. */
    fun onReaderDownloadTap() {
        when (readerDl.state) {
            ReaderDlState.Downloading -> {}
            ReaderDlState.Done -> readerMenuOpen = !readerMenuOpen
            else -> when (downloadPref) {
                DownloadPref.TextOnly -> startDownload(false)
                DownloadPref.TextAudio, DownloadPref.AudioOnly -> startDownload(readerHasEpisode())
                DownloadPref.Ask -> if (readerHasEpisode()) readerMenuOpen = true else startDownload(false)
            }
        }
    }

    // ---- bookmarks ----
    val readerBookmarked: Boolean get() = readerItem?.let { bookmarkedUrls.contains(it.url) } ?: false
    fun toggleReaderBookmark() {
        val item = readerItem ?: return
        viewModelScope.launch { repo.toggleBookmark(item, autoDownloadBookmarks, prefWantsAudio()) }
    }
    fun selectDownloadPref(p: DownloadPref) { downloadPref = p }
    fun toggleAutoDownloadBookmarks() { autoDownloadBookmarks = !autoDownloadBookmarks }

    fun removeDownload() {
        readerMenuOpen = false
        readerItem?.let { url -> viewModelScope.launch { repo.removeDownload(url.url) } }
    }

    fun cancelDownload(url: String) { viewModelScope.launch { repo.removeDownload(url) } }

    // ---- podcast ----
    fun episodeForReader(): Episode? = readerItem?.let { repo.episodeFor(it) }
    fun playReaderNarration() { episodeForReader()?.let { playWithResume(it) } }
    fun play(episode: Episode) { playWithResume(episode) }

    /** Start an episode from its last-saved position (0 if none / finished). */
    private fun playWithResume(episode: Episode) {
        viewModelScope.launch { player.play(episode, repo.resumePosition(episode.audioUrl)) }
    }

    // ---- full-screen player ----
    fun openPlayer() { if (player.state.value.hasContent) playerFullScreen = true }
    fun closePlayer() { playerFullScreen = false }

    /** Episodes in a stable order (newest first) for previous/next navigation. */
    private fun orderedEpisodes(): List<Episode> = episodes.sortedByDescending { it.publishedMillis }

    private fun currentEpisodeIndex(list: List<Episode>): Int {
        val url = player.state.value.episode?.audioUrl ?: return -1
        return list.indexOfFirst { it.audioUrl == url }
    }

    fun playerNext() {
        val list = orderedEpisodes()
        val i = currentEpisodeIndex(list)
        if (i >= 0 && i + 1 < list.size) playWithResume(list[i + 1])
    }

    fun playerPrevious() {
        val list = orderedEpisodes()
        val i = currentEpisodeIndex(list)
        if (i > 0) playWithResume(list[i - 1])
    }

    // ---- misc reducers ----
    fun go(s: Screen) {
        screen = s; readerItem = null; randomOpen = false
        if (s == Screen.Home && feedRaw.isEmpty() && !feedLoading) loadFeed()
    }
    fun goSearch() { screen = Screen.Search }
    fun togglePauseAll() { downloadsPaused = !downloadsPaused; repo.setPaused(downloadsPaused) }
    fun toggleDynamic() { dynamicColor = !dynamicColor }
    fun selectSeed(k: SeedKey) { seed = k }
    fun incFont() { fontScale = (fontScale + 0.1f).coerceAtMost(1.4f).round2() }
    fun decFont() { fontScale = (fontScale - 0.1f).coerceAtLeast(0.85f).round2() }
    fun toggleImages() { loadImages = !loadImages }
    fun toggleWifi() { wifiOnly = !wifiOnly; repo.setWifiOnly(wifiOnly) }
    fun setClass(c: String) { classFilter = if (classFilter == c) "any" else c; applySearchFilters() }
    fun toggleTag(t: String) {
        activeTags = if (activeTags.contains(t)) activeTags - t else activeTags + t
        applySearchFilters()
    }
    fun selectLibTab(t: String) { libTab = t }
    fun selectRandomType(t: RandomType) { randomType = t }
    fun selectRandomMode(m: RandomMode) { randomMode = m }
    fun addRandomTag(raw: String) {
        val t = raw.trim().lowercase()
        randomTagInput = ""
        if (t.isEmpty() || randomTags.contains(t)) return
        randomTags = randomTags + t
    }
    fun removeRandomTag(t: String) { randomTags = randomTags - t }

    /** Kick off a bulk download: random pool, top-rated N, or the entire branch. */
    fun startRandomDownload() {
        randomOpen = false
        val typeTag = when (randomType) { RandomType.Scp -> "scp"; RandomType.Tale -> "tale"; RandomType.Goi -> "goi-format" }
        val tag = randomTags.firstOrNull() ?: typeTag
        when (randomMode) {
            RandomMode.Pool -> repo.enqueueRandom(randomCount, randomTags, typeTag)
            RandomMode.TopRated -> repo.enqueueTopRated(randomCount, tag)
            RandomMode.Entire -> repo.enqueueEntireWiki(tag)
            RandomMode.Series -> repo.enqueueEntireWiki("crom:series-$randomSeries")
        }
        screen = Screen.Downloads
    }

    private fun redecorate() {
        if (feedRaw.isNotEmpty()) feed = repo.decorate(feedRaw)
        if (searchRaw.isNotEmpty()) applySearchFilters()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                AppState(container.repository, container.player, container.settingsStore) as T
        }
    }
}

private fun Float.round2(): Float = Math.round(this * 100f) / 100f
