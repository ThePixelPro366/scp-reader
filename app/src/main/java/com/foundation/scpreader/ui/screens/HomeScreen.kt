package com.foundation.scpreader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foundation.scpreader.AppState
import com.foundation.scpreader.HeroMode
import com.foundation.scpreader.data.ScpItem
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.ClassBadge
import com.foundation.scpreader.ui.components.ClassBadgeIcon
import com.foundation.scpreader.ui.components.FilterChip
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.components.ScpPullSpinner
import com.foundation.scpreader.ui.components.ScpSpinner
import com.foundation.scpreader.ui.components.chipStyle
import com.foundation.scpreader.ui.theme.LocalScpScheme
import kotlin.math.roundToInt

private val typeDefs = listOf("all" to "All", "scp" to "SCP", "tale" to "Tales", "goi" to "GoI", "series" to "Series")

@Composable
fun TypeChipRow(app: AppState, padding: Modifier) {
    val c = LocalScpScheme.current
    Row(padding.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        typeDefs.forEach { (k, label) ->
            val active = app.typeFilter == k
            val st = c.chipStyle(active)
            FilterChip(label, active, st.bg, st.fg, st.border) { app.setType(k) }
        }
    }
}

@Composable
private fun SeriesPickerRow(app: AppState) {
    val c = LocalScpScheme.current
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..9).forEach { n ->
            val active = app.feedSeries == n
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (active) c.primary else Color.Transparent)
                    .then(if (active) Modifier else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp)))
                    .clickable { app.selectFeedSeries(n) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Series $n", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (active) c.onPrimary else c.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppState) {
    val c = LocalScpScheme.current
    // Offline with nothing cached to show: dedicated full-screen offline state.
    if (app.offlineMode && !app.feedLoading) { OfflineScreen(app); return }
    val ptrState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = app.heroRefreshing,
        onRefresh = { app.refreshDiscover() },
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            val frac = ptrState.distanceFraction
            if (app.heroRefreshing || frac > 0f) {
                // Floating disc that travels down with the pull; ring draws on, then spins while refreshing.
                val y = if (app.heroRefreshing) 76.dp else (frac.coerceIn(0f, 1.3f) * 96).dp
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = y)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(c.surfaceContainer)
                        .padding(9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ScpPullSpinner(fraction = frac, refreshing = app.heroRefreshing, size = 34)
                }
            }
        },
    ) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 108.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("FOUNDATION ARCHIVE", fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp, color = c.primary)
                Text("Secure. Contain. Protect.", fontSize = 27.sp, color = c.onSurface, lineHeight = 30.sp)
            }
            // Settings lives here (top-right action), not in the bottom navigation bar.
            Box(
                Modifier.clip(CircleShape).clickable { app.go(com.foundation.scpreader.Screen.Settings) }.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Settings, "Settings", Modifier.size(26.dp), tint = c.onSurfaceVariant)
            }
        }

        // search entry
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)
                .fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(c.surfaceContainer).clickable { app.goSearch() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.Search, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
            Text("Search items, tales, GoI…", fontSize = 15.sp, color = c.onSurfaceVariant)
        }

        if (app.offline) OfflineBanner()

        HeroSlot(app)

        TypeChipRow(app, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth())

        if (app.typeFilter == "series") SeriesPickerRow(app)

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.Shuffle, null, Modifier.size(16.dp), tint = c.primary)
            Text("RANDOM ENTRIES", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c.primary)
            if (app.feedIsRandom) {
                Box(Modifier.weight(1f))
                val active = app.narratedOnly
                val st = c.chipStyle(active)
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(st.bg)
                        .then(if (st.border == Color.Transparent) Modifier else Modifier.border(1.dp, st.border, RoundedCornerShape(16.dp)))
                        .clickable { app.toggleNarratedOnly() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(AppIcons.Headphones, null, Modifier.size(16.dp), tint = st.fg)
                    Text("Narrated", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = st.fg)
                }
            }
        }

        when {
            app.feedLoading && app.feed.isEmpty() -> LoadingBlock()
            app.feedError != null && app.feed.isEmpty() -> ErrorBlock(app.feedError!!) { app.loadFeed() }
            else -> Column(Modifier.padding(horizontal = 16.dp)) {
                app.feed.forEach { item -> FeedCard(app, item) }
                if (app.feedHasMore) LoadMore(app.feedLoading) { app.loadMoreFeed() }
            }
        }
    }
    }
}

@Composable
private fun HeroSlot(app: AppState) {
    val c = LocalScpScheme.current
    val (label, icon) = when (app.heroMode) {
        HeroMode.ContinueReading -> "CONTINUE READING" to AppIcons.History
        HeroMode.ScpOfTheDay -> "SCP OF THE DAY" to AppIcons.Casino
        HeroMode.Trending -> "TRENDING" to AppIcons.Bolt
        HeroMode.RecentlyViewed -> "RECENTLY VIEWED" to AppIcons.History
        HeroMode.FriendRecommendation -> "FRIEND RECOMMENDATION" to AppIcons.Group
    }

    if (app.heroMode == HeroMode.FriendRecommendation) {
        HeroHeader(label, icon)
        val rec = app.recommendations.firstOrNull()
        if (rec == null) {
            HeroPlaceholder("When a friend recommends an SCP it shows up here. Share your friend code from the Friends tab.")
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(c.primaryContainer)
                    .clickable { app.openRecommendation(rec) }.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Text("FROM ${(rec.from_name.ifBlank { rec.from_code }).uppercase()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c.onPrimaryContainer)
                Text(rec.scp_number.ifBlank { rec.scp_title }, fontFamily = Mono, fontSize = 13.sp, color = c.onPrimaryContainer, modifier = Modifier.padding(top = 8.dp))
                Text(rec.scp_title.ifBlank { rec.scp_number }, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (rec.note.isNotBlank()) {
                    Text("“${rec.note}”", fontSize = 14.sp, color = c.onPrimaryContainer.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        return
    }

    if (app.heroMode == HeroMode.RecentlyViewed) {
        val items = app.recentlyViewed
        HeroHeader(label, icon)
        if (items.isEmpty()) {
            HeroPlaceholder("Open any entry — the ones you read show up here.")
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    Column(
                        Modifier.width(180.dp).clip(RoundedCornerShape(20.dp)).background(c.primaryContainer)
                            .clickable { app.openReaderItem(item) }.padding(16.dp),
                    ) {
                        Text(item.number, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer)
                        Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(item.objectClass, fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        return
    }

    val heroItem = when (app.heroMode) {
        HeroMode.ContinueReading -> app.continueItem
        HeroMode.ScpOfTheDay -> app.scpOfDay
        HeroMode.Trending -> app.trending
        else -> null
    }
    HeroHeader(label, icon)
    // Animate whenever the highlighted item changes.
    AnimatedContent(
        targetState = heroItem,
        transitionSpec = { (fadeIn(tween(350)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(200)) },
        label = "hero",
    ) { item ->
        if (item == null) {
            HeroPlaceholder(
                when (app.heroMode) {
                    HeroMode.ContinueReading -> "Open any entry to start reading — you can jump back in here."
                    else -> "Loading…"
                }
            )
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(c.primaryContainer)
                    .clickable { app.openReaderItem(item) }.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Text(item.number, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer)
                Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val frac = if (app.heroMode == HeroMode.ContinueReading) (app.recentProgress[item.url] ?: 0f) else 0f
                if (frac > 0.01f) {
                    // Reading-progress bar (persisted scroll fraction) — resumes where you left off.
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.onPrimaryContainer.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.primary))
                    }
                    Text("${(frac * 100).roundToInt()}% read", fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.85f), modifier = Modifier.padding(top = 7.dp))
                } else {
                    Text("★ ${item.rating} · ${item.objectClass}", fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.85f), modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = LocalScpScheme.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.primary)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c.primary)
    }
}

@Composable
private fun HeroPlaceholder(text: String) {
    val c = LocalScpScheme.current
    Box(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 18.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(c.surfaceCLow).padding(20.dp),
    ) {
        Text(text, fontSize = 14.sp, color = c.onSurfaceVariant, lineHeight = 20.sp)
    }
}

@Composable
private fun OfflineBanner() {
    val c = LocalScpScheme.current
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.CloudOff, null, Modifier.size(20.dp), tint = c.primary)
        Column(Modifier.weight(1f)) {
            Text("No internet connection", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            Text("Showing what's saved — new entries need a connection.", fontSize = 12.sp, color = c.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

/** Full-screen "You're offline" state (mockup: Offline Screen) shown when nothing is cached. */
@Composable
private fun OfflineScreen(app: AppState) {
    val c = LocalScpScheme.current
    val done = app.downloads.filter { it.status == com.foundation.scpreader.database.DlStatus.DONE }
    val sizeBytes = done.sumOf { it.sizeBytes }
    val hasAudio = done.any { it.hasAudio }
    Column(Modifier.fillMaxSize()) {
        // top bar: settings entry (replaces the bottom nav, which is hidden while offline)
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).clickable { app.go(com.foundation.scpreader.Screen.Settings) }.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Settings, "Settings", Modifier.size(26.dp), tint = c.onSurfaceVariant)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f))

            // signal-lost glyph with an "available offline" badge
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(132.dp).clip(RoundedCornerShape(40.dp)).background(c.surfaceCLow)
                        .border(1.dp, c.outlineVariant, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.WifiOff, null, Modifier.size(66.dp), tint = c.onSurfaceVariant)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp)
                        .size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.primaryContainer)
                        .border(3.dp, c.surface, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.DownloadDone, null, Modifier.size(20.dp), tint = c.onPrimaryContainer)
                }
            }

            Text("SIGNAL LOST", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = c.primary, modifier = Modifier.padding(top = 30.dp))
            Text("You're offline", fontSize = 27.sp, fontWeight = FontWeight.Medium, color = c.onSurface, lineHeight = 31.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Can't reach the Foundation network right now. Everything you've saved is still fully readable.",
                fontSize = 14.sp, lineHeight = 22.sp, color = c.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp).width(270.dp),
            )

            // retry
            Row(
                Modifier.padding(top = 22.dp).height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.primary)
                    .clickable { app.loadFeed() }.padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Refresh, null, Modifier.size(21.dp), tint = c.onPrimary)
                Text("Retry connection", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
            }
            app.lastFeedSyncAt?.let { ts ->
                Text("Last synced ${relativeTime(ts)}", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }

            Box(Modifier.weight(1f))

            // available-offline card
            Column(
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.DownloadForOffline, null, Modifier.size(23.dp), tint = c.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${done.size} item${if (done.size == 1) "" else "s"} ready offline", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                        Text(
                            if (done.isEmpty()) "Nothing saved yet" else formatSize(sizeBytes) + if (hasAudio) " · text & narration" else " · text",
                            fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(c.secondaryContainer)
                            .clickable { app.go(com.foundation.scpreader.Screen.Library) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(AppIcons.LibraryBooks, null, Modifier.size(19.dp), tint = c.onSecondaryContainer)
                        Text("Open library", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondaryContainer, modifier = Modifier.padding(start = 8.dp))
                    }
                    val recent = app.recentlyViewed.firstOrNull()
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable(enabled = recent != null) { recent?.let { app.openReaderItem(it) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.History, null, Modifier.size(21.dp), tint = if (recent != null) c.onSurfaceVariant else c.outlineVariant)
                    }
                }
            }
        }
    }
}

/** Coarse "N min/hour/day ago" label for the offline screen's last-synced line. */
private fun relativeTime(ts: Long): String {
    val mins = ((System.currentTimeMillis() - ts) / 60000L).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${mins / 60} h ago"
        else -> "${mins / 1440} d ago"
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        ScpSpinner(size = 84)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val c = LocalScpScheme.current
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontSize = 14.sp, color = c.onSurfaceVariant)
        Text("Retry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.primary,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onRetry).padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

@Composable
private fun LoadMore(loading: Boolean, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (loading) ScpSpinner(size = 40)
        else Text("Load more", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.primary,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

@Composable
private fun FeedCard(app: AppState, item: ScpItem) {
    val c = LocalScpScheme.current
    Column(
        Modifier.padding(bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(26.dp))
            .clickable { app.openReaderItem(item) },
    ) {
        if (item.imageUrl != null) {
            Box(Modifier.fillMaxWidth().height(148.dp).background(c.surfaceCHigh)) {
                AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(148.dp), contentScale = ContentScale.Crop)
                Text(
                    item.number, fontFamily = Mono, fontSize = 11.sp, color = c.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)).background(c.surface.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 16.dp)) {
            // Reads left-to-right: class badge icon, class name, then the SCP id.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClassBadgeIcon(item.objectClass, size = 24)
                ClassBadge(item.objectClass, app.isDark)
                Text(item.number, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.primary)
                Box(Modifier.weight(1f))
                if (item.podcast) Icon(AppIcons.Headphones, null, Modifier.size(20.dp), tint = c.primary)
                if (item.downloaded) Icon(AppIcons.DownloadDone, null, Modifier.size(20.dp), tint = c.primary)
            }
            Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = c.onSurface, lineHeight = 23.sp, modifier = Modifier.padding(top = 10.dp))
            if (item.excerpt.isNotEmpty()) {
                Text(item.excerpt, fontSize = 14.sp, color = c.onSurfaceVariant, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.contentTags.take(3).forEach { tag ->
                    Text(tag, fontSize = 12.sp, color = c.onSurfaceVariant, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.surfaceContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}
