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
import com.foundation.scpreader.update.UpdateCheckResult
import com.foundation.scpreader.update.UpdateDownloadState
import com.foundation.scpreader.update.UpdateManager
import kotlinx.coroutines.flow.first
import com.foundation.scpreader.database.DlStatus
import com.foundation.scpreader.database.DownloadEntity
import com.foundation.scpreader.playback.PlayerController
import com.foundation.scpreader.ui.theme.SeedKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

enum class Screen { Home, Search, Library, Downloads, Friends, Settings }
enum class ThemeMode { Light, Auto, Dark }
enum class ReaderDlState { Idle, Downloading, Done }
enum class RandomType { Scp, Tale, Goi }
enum class RandomMode { Pool, TopRated, Entire, Series }
enum class DownloadPref { TextOnly, TextAudio, AudioOnly, Ask }
enum class HeroMode { ContinueReading, ScpOfTheDay, Trending, RecentlyViewed, FriendRecommendation }
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
    private val updateManager: UpdateManager,
    private val friends: com.foundation.scpreader.data.FriendsRepository,
    private val isOnline: () -> Boolean = { true },
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
    var sponsorCategories by mutableStateOf(com.foundation.scpreader.playback.SponsorCategory.DEFAULT_ENABLED)

    // ---- in-app updates (GitHub Releases, public repo — no auth needed) ----
    var updateStatus by mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Idle); private set
    var updateDownload by mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle); private set
    var updateBannerDismissed by mutableStateOf(false)
    // Set when Settings is opened via the update banner so it auto-scrolls to the Updates section.
    var scrollSettingsToUpdates by mutableStateOf(false)

    // ---- friends / recommendations (anonymous device token; backend in /webserver) ----
    var friendCode by mutableStateOf<String?>(null); private set
    var friendsList by mutableStateOf<List<com.foundation.scpreader.network.FriendsApi.Friend>>(emptyList()); private set
    var recommendations by mutableStateOf<List<com.foundation.scpreader.network.FriendsApi.Rec>>(emptyList()); private set
    var friendsLoading by mutableStateOf(false); private set
    var friendsError by mutableStateOf<String?>(null); private set
    var friendsNotice by mutableStateOf<String?>(null); private set
    private var friendsNoticeJob: Job? = null
    var addFriendInput by mutableStateOf("")
    var serverUrl by mutableStateOf(com.foundation.scpreader.data.DEFAULT_FRIENDS_SERVER)
    // Reader "recommend to friend" sheet.
    var recommendSheetOpen by mutableStateOf(false); private set
    var recommendNote by mutableStateOf("")

    var searchQuery by mutableStateOf("")
    var typeFilter by mutableStateOf("all")
    var classFilter by mutableStateOf("any")
    var activeTags by mutableStateOf(listOf<String>())
    var searchFiltersOpen by mutableStateOf(false)
    var searchSort by mutableStateOf(SortMode.Relevance)
    var audioOnly by mutableStateOf(false)
    var libTab by mutableStateOf("all")
    /** Library "browse offline by class" filter — an object class name, or "all". */
    var libClassFilter by mutableStateOf("all")

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

    // Transient one-line player notice (fallback reason / "skipped sponsor"); auto-clears.
    var playerNotice by mutableStateOf<String?>(null); private set
    private var noticeJob: Job? = null

    // ---- live data ----
    var feed by mutableStateOf<List<ScpItem>>(emptyList()); private set
    var feedLoading by mutableStateOf(false); private set
    var feedError by mutableStateOf<String?>(null); private set
    private var feedRaw = listOf<ScpItem>()
    private var feedCursor: String? = null
    var feedHasMore by mutableStateOf(false); private set
    /** True when the device has no internet connection — surfaced as a banner/message on Home. */
    var offline by mutableStateOf(false); private set
    /** Wall-clock time of the last successful feed load, for the offline screen's "last synced" line. */
    var lastFeedSyncAt by mutableStateOf<Long?>(null); private set

    /**
     * Offline mode: no connection AND nothing cached to browse. In this mode the app collapses to
     * just Home (the offline screen) ↔ Library ↔ Settings, and the bottom nav is hidden.
     */
    val offlineMode: Boolean get() = offline && feed.isEmpty()
    /** When on, the random discover feed only surfaces entries that have narration available. */
    var narratedOnly by mutableStateOf(false); private set
    private var loadedTag = ""

    var searchResults by mutableStateOf<List<ScpItem>>(emptyList()); private set
    var searchLoading by mutableStateOf(false); private set
    private var searchRaw = listOf<ScpItem>()
    private var searchJob: Job? = null

    // Search zero-state: top-rated SCPs shown before the user types anything.
    var searchTopRated by mutableStateOf<List<ScpItem>>(emptyList()); private set
    var searchTopRatedLoading by mutableStateOf(false); private set
    var searchTopRatedError by mutableStateOf(false); private set

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
    /** Reading-progress fraction (0f‥1f) per article url, for the Continue-reading hero bar. */
    var recentProgress by mutableStateOf<Map<String, Float>>(emptyMap()); private set
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
        // Surface a transient toast whenever the player auto-skips a SponsorBlock segment.
        player.onSponsorSkipped = { category ->
            notice("Skipped ${com.foundation.scpreader.playback.SponsorCategory.label(category).lowercase()}")
        }
        // A YouTube stream URL expired mid-playback: re-extract a fresh one and resume in place.
        player.onSourceError = { episode, positionMs -> reResolveAndResume(episode, positionMs) }
        // Surface background audio-download stage failures as a transient notice.
        repo.onDownloadNotice = { message -> notice(message) }
        // Silent check on cold start so a returning user sees the banner without tapping anything;
        // the repo is public, so this needs no credentials at all.
        checkForUpdates()
        viewModelScope.launch { repo.downloads.collect { downloads = it; redecorate() } }
        viewModelScope.launch { repo.bookmarks.collect { bookmarks = it; bookmarkedUrls = it.map { b -> b.url }.toSet() } }
        viewModelScope.launch { repo.recents.collect { recentlyViewed = repo.decorate(it) } }
        viewModelScope.launch { repo.recentRows.collect { rows -> recentProgress = rows.associate { it.url to it.progress } } }
        viewModelScope.launch { repo.searchRecents.collect { searchRecentlyViewed = repo.decorate(it) } }
        viewModelScope.launch { repo.tagVocab.collect { tagVocab = it } }
        // Redecorate feed/search whenever narration availability changes (e.g. after YouTube sync).
        viewModelScope.launch { repo.availability.collect { redecorate() } }
        viewModelScope.launch { episodes = repo.episodes(); redecorate() }
        viewModelScope.launch { friends.serverUrlFlow.collect { serverUrl = it } }
        // Register this device (anonymous token) in the background so the friend code is ready,
        // then pull recommendations for the Home card.
        viewModelScope.launch {
            friendCode = friends.cachedFriendCode()
            runCatching { friends.ensureRegistered() }.onSuccess { friendCode = it }
            refreshRecommendationsForHome()
        }
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
        sponsorCategories = s.sponsorCategories
        repo.setWifiOnly(s.wifiOnly)
    }

    private fun currentSettings() = Settings(
        themeMode, dynamicColor, seed, fontScale, loadImages, wifiOnly,
        downloadPref, autoDownloadBookmarks, excludedClasses, heroMode, sponsorCategories,
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

    /** Which SCP series the Home "Series" filter is showing (1 = SCP-001‥999, 2 = 1000‥1999, …). */
    var feedSeries by mutableStateOf(1); private set

    // ---- feed ----
    private fun feedTagFor(type: String) = when (type) {
        "tale" -> "tale"; "goi" -> "goi-format"; "series" -> "crom:series-$feedSeries"; else -> "scp"
    }

    /** All/SCP → an infinite "Random entries" discover feed; Tales/GoI/Series → ranked CROM listing. */
    val feedIsRandom: Boolean get() = typeFilter == "all" || typeFilter == "scp"

    /** Switch which series the Home "Series" filter lists, reloading the feed if it's active. */
    fun selectFeedSeries(n: Int) {
        if (feedSeries == n) return
        feedSeries = n
        if (screen == Screen.Home && typeFilter == "series") loadFeed()
    }

    /** Re-check connectivity; call before/around network work so the Home banner stays current. */
    fun refreshConnectivity() { offline = !isOnline() }

    fun loadFeed(reset: Boolean = true) {
        refreshConnectivity()
        if (reset) { feedRaw = emptyList(); feedCursor = null; feed = emptyList() }
        feedLoading = true; feedError = null
        viewModelScope.launch {
            if (feedIsRandom) {
                runCatching { if (narratedOnly) repo.randomNarratedItems(12, excludedClasses) else repo.randomItems(12, excludedClasses) }
                    .onSuccess { items ->
                        feedRaw = (feedRaw + items).distinctBy { it.url }
                        feed = repo.decorate(feedRaw)
                        // Narrated pool is finite: stop paging once a fetch adds nothing new.
                        feedHasMore = !narratedOnly || items.isNotEmpty()
                        if (feedRaw.isEmpty() && narratedOnly)
                            feedError = "No narrated entries yet — narration may still be syncing. Turn off the Narrated filter to browse everything."
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
            if (feedError == null && feed.isNotEmpty()) {
                lastFeedSyncAt = System.currentTimeMillis()
                offline = false
            }
        }
    }

    fun loadMoreFeed() { if (!feedLoading && feedHasMore) loadFeed(reset = false) }

    /** Pull-to-refresh on Home: pull fresh random entries and refresh the trending hero. */
    fun refreshDiscover() {
        refreshConnectivity()
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

    /** Toggle the "narrated only" filter on the random discover feed. */
    fun toggleNarratedOnly() {
        narratedOnly = !narratedOnly
        if (screen == Screen.Home && feedIsRandom) loadFeed()
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

    /**
     * Fetch top-rated SCPs for the search zero-state. Idempotent — a no-op once loaded or while a
     * fetch is in flight — and fails soft (sets [searchTopRatedError]) so an offline open just
     * falls back to recents / a message rather than crashing.
     */
    fun loadSearchTopRated() {
        if (searchTopRated.isNotEmpty() || searchTopRatedLoading) return
        searchTopRatedLoading = true
        searchTopRatedError = false
        viewModelScope.launch {
            runCatching { repo.topRated(tag = "scp") }
                .onSuccess { searchTopRated = it.items.take(12) }
                .onFailure { searchTopRatedError = true }
            searchTopRatedLoading = false
        }
    }

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
    fun saveReaderScroll(url: String, offset: Int, progress: Float) {
        viewModelScope.launch { repo.saveRecentScroll(url, offset, progress) }
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

    /** Open a wiki article URL delivered by an external VIEW intent (tapped link). */
    fun openFromWikiUrl(raw: String) {
        val url = raw.trim().replaceFirst("https://", "http://")
        val slug = url.substringAfterLast('/').substringBefore('#').substringBefore('?').lowercase()
        // Bare domain / non-article paths: just bring the app to Home.
        if (slug.isBlank() || slug.contains("wikidot.com") || slug.startsWith("system:") || slug.startsWith("forum")) {
            go(Screen.Home); return
        }
        val isScp = Regex("^scp-\\d+[a-z0-9-]*$").matches(slug)
        val number = if (isScp) "SCP-" + slug.removePrefix("scp-").uppercase()
        else slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
        go(Screen.Home)
        openReaderItem(
            ScpItem(
                url = url, number = number, title = number, objectClass = "Unknown",
                typeLabel = if (isScp) "SCP" else "Tale", tags = emptyList(),
            )
        )
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
        readerItem?.let { repo.enqueue(it, audio, sponsorCategories) }
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

    /**
     * Resolve a concrete playable URI (local file → YouTube stream → Apple MP3), then start it from
     * its last-saved position and apply SponsorBlock segments. The resolved episode may differ from
     * [episode] if a YouTube video was missing/unextractable and we fell back to Apple.
     */
    private fun playWithResume(episode: Episode) {
        // Same episode already loaded → just toggle (avoids a needless re-resolve/network hit).
        if (player.state.value.episode?.mediaId == episode.mediaId) { player.togglePlayPause(); return }
        viewModelScope.launch {
            val playable = repo.resolvePlayable(episode)
            if (playable == null) { notice("Narration unavailable"); return@launch }
            playable.fallbackReason?.let { notice(it) }
            val resolved = playable.episode
            val pos = repo.resumePosition(resolved.mediaId)
            player.play(resolved, playable.uri, pos, playable.origin, playable.cleaned)
            // Live SponsorBlock skipping applies ONLY to YouTube streaming. A downloaded file is
            // either already trimmed (clean) or, if trim failed, left as-is — either way its
            // timestamps wouldn't match the original, so we never live-skip a local file.
            val vid = resolved.videoId
            val segments = if (playable.origin == com.foundation.scpreader.playback.PlaybackOrigin.YOUTUBE && vid != null)
                runCatching { repo.sponsorSegments(vid, sponsorCategories) }.getOrDefault(emptyList())
            else emptyList()
            player.setSponsorSegments(resolved.mediaId, segments)
        }
    }

    /** Re-extract a fresh YouTube URL after an expiry error and resume [episode] at [positionMs]. */
    private fun reResolveAndResume(episode: Episode, positionMs: Long) {
        viewModelScope.launch {
            episode.videoId?.let { repo.invalidateStream(it) }
            val playable = repo.resolvePlayable(episode)
            if (playable == null) { notice("Playback failed"); return@launch }
            playable.fallbackReason?.let { notice(it) }
            val resolved = playable.episode
            player.play(resolved, playable.uri, positionMs, playable.origin, playable.cleaned, forceReload = true)
            val vid = resolved.videoId
            val segments = if (playable.origin == com.foundation.scpreader.playback.PlaybackOrigin.YOUTUBE && vid != null)
                runCatching { repo.sponsorSegments(vid, sponsorCategories) }.getOrDefault(emptyList())
            else emptyList()
            player.setSponsorSegments(resolved.mediaId, segments)
        }
    }

    /** Show a transient one-line player notice; auto-clears after a couple of seconds. */
    private fun notice(message: String) {
        playerNotice = message
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch { kotlinx.coroutines.delay(2600); playerNotice = null }
    }

    fun toggleSponsorCategory(category: String) {
        sponsorCategories = if (category in sponsorCategories) sponsorCategories - category else sponsorCategories + category
    }

    // ---- in-app updates ----
    /** Manual "Check for updates" entry point, also run silently once on app start. No credentials needed. */
    fun checkForUpdates() {
        updateStatus = UpdateCheckResult.Checking
        updateBannerDismissed = false
        viewModelScope.launch {
            updateStatus = updateManager.checkForUpdate(BuildConfig.VERSION_NAME)
        }
    }

    /** Downloads the release APK found by [checkForUpdates] into the cache dir. No credentials needed. */
    fun downloadUpdate(context: android.content.Context) {
        val available = updateStatus as? UpdateCheckResult.Available ?: return
        updateDownload = UpdateDownloadState.Downloading(0)
        viewModelScope.launch {
            val dest = java.io.File(java.io.File(context.cacheDir, "updates"), "scp-reader-update.apk")
            updateDownload = runCatching {
                updateManager.downloadAsset(available.asset, dest) { pct ->
                    updateDownload = UpdateDownloadState.Downloading(pct)
                }
            }.fold(
                onSuccess = { UpdateDownloadState.ReadyToInstall(dest) },
                onFailure = { UpdateDownloadState.Failed(it.message ?: "Download failed") },
            )
        }
    }

    // ---- full-screen player ----
    fun openPlayer() { if (player.state.value.hasContent) playerFullScreen = true }
    fun closePlayer() { playerFullScreen = false }

    /** Episodes in a stable order (newest first) for previous/next navigation. */
    private fun orderedEpisodes(): List<Episode> = episodes.sortedByDescending { it.publishedMillis }

    private fun currentEpisodeIndex(list: List<Episode>): Int {
        val id = player.state.value.episode?.mediaId ?: return -1
        return list.indexOfFirst { it.mediaId == id }
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

    // ---- friends / recommendations ----
    /** Load friends + incoming recommendations together; also (re)registers to backfill the code. */
    fun loadFriends() {
        friendsLoading = true; friendsError = null
        viewModelScope.launch {
            if (friendCode == null) runCatching { friends.ensureRegistered() }.onSuccess { friendCode = it }
            // friends + recommendations are independent — fetch them concurrently.
            runCatching {
                val friendsDef = async { friends.friends() }
                val recsDef = async { friends.recommendations() }
                friendsList = friendsDef.await()
                recommendations = recsDef.await()
                // Opening the Friends tab counts as seeing them — stop the poller re-notifying.
                recommendations.maxOfOrNull { it.id }?.let { friends.setLastSeenRecId(it.toLong()) }
            }.onFailure { friendsError = it.message ?: "Couldn't reach the server" }
            friendsLoading = false
        }
    }

    fun addFriend() {
        val code = addFriendInput.trim()
        if (code.length != 6) { postFriendsNotice("Enter a 6-character code"); return }
        viewModelScope.launch {
            runCatching { friends.addFriend(code) }
                .onSuccess { added ->
                    addFriendInput = ""
                    postFriendsNotice("Added ${added.name.ifBlank { added.friend_code }}")
                    // add_friend.php returns the new friend, so append locally instead of a second
                    // round-trip to re-fetch the whole list (dedup in case they were already added).
                    if (friendsList.none { it.friend_code == added.friend_code }) {
                        friendsList = (friendsList + added).sortedBy { it.name.ifBlank { it.friend_code } }
                    }
                }
                .onFailure { postFriendsNotice(it.message ?: "Couldn't add friend") }
        }
    }

    /** Quietly refresh recommendations for the Home card (no loading/error UI, no seen-marking). */
    fun refreshRecommendationsForHome() {
        viewModelScope.launch { runCatching { recommendations = friends.recommendations() } }
    }

    fun removeFriend(code: String) {
        viewModelScope.launch {
            runCatching { friends.removeFriend(code) }
                .onSuccess {
                    friendsList = friendsList.filterNot { it.friend_code == code }
                    // Their recommendations are deleted server-side too — drop them locally to match.
                    recommendations = recommendations.filterNot { it.from_code == code }
                    postFriendsNotice("Removed")
                }
                .onFailure { postFriendsNotice(it.message ?: "Couldn't remove friend") }
        }
    }

    fun updateServerUrl(url: String) { serverUrl = url }
    fun saveServerUrl() { viewModelScope.launch { friends.setServerUrl(serverUrl); loadFriends() } }

    /** Open a recommended SCP in the reader from its stored url/number/title. */
    fun openRecommendation(rec: com.foundation.scpreader.network.FriendsApi.Rec) {
        openReaderItem(
            ScpItem(
                url = rec.scp_url, number = rec.scp_number.ifBlank { rec.scp_title },
                title = rec.scp_title.ifBlank { rec.scp_number }, objectClass = "Unknown",
                typeLabel = "SCP", tags = emptyList(),
            )
        )
    }

    // Reader → "Recommend to friend" flow.
    fun openRecommendSheet() {
        recommendNote = ""; recommendSheetOpen = true; readerMenuOpen = false
        if (friendsList.isEmpty()) loadFriends()
    }
    fun closeRecommendSheet() { recommendSheetOpen = false }

    fun sendRecommendation(friendCode: String) {
        val item = readerItem ?: return
        val note = recommendNote.trim()
        recommendSheetOpen = false
        viewModelScope.launch {
            runCatching { friends.recommend(friendCode, item, note) }
                .onSuccess { postFriendsNotice("Recommendation sent") }
                .onFailure { postFriendsNotice(it.message ?: "Couldn't send") }
        }
    }

    private fun postFriendsNotice(message: String) {
        friendsNotice = message
        friendsNoticeJob?.cancel()
        friendsNoticeJob = viewModelScope.launch { kotlinx.coroutines.delay(2600); friendsNotice = null }
    }

    // ---- misc reducers ----
    fun go(s: Screen) {
        screen = s; readerItem = null; randomOpen = false
        if (s == Screen.Home && feedRaw.isEmpty() && !feedLoading) loadFeed()
        if (s == Screen.Home) refreshRecommendationsForHome()
        if (s == Screen.Friends) loadFriends()
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
    fun selectLibClass(cls: String) { libClassFilter = if (libClassFilter == cls) "all" else cls }
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
                AppState(container.repository, container.player, container.settingsStore, container.updateManager, container.friendsRepository, container::isOnline) as T
        }
    }
}

private fun Float.round2(): Float = Math.round(this * 100f) / 100f
