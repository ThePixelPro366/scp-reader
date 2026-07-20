package com.foundation.scpreader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundation.scpreader.AppState
import com.foundation.scpreader.R
import com.foundation.scpreader.data.toScpItem
import com.foundation.scpreader.database.DlStatus
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.classBadgeRes
import com.foundation.scpreader.ui.components.Divider1
import com.foundation.scpreader.ui.components.Dot
import com.foundation.scpreader.ui.components.FilterChip
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.classColors

private val libDefs = listOf("all" to "All", "downloaded" to "Downloaded", "audio" to "With audio")

private data class LibRow(val item: com.foundation.scpreader.data.ScpItem, val size: String?, val downloaded: Boolean, val bookmarked: Boolean, val audio: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(app: AppState) {
    val c = LocalScpScheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val done = app.downloads.filter { it.status == DlStatus.DONE }
    val downloadedBytes = done.sumOf { it.sizeBytes }
    // Whole-device storage, split into the app's articles/audio and everything else ("Other").
    val (capacity, freeBytes) = remember {
        runCatching { val s = android.os.StatFs(ctx.filesDir.absolutePath); s.totalBytes to s.availableBytes }.getOrDefault(0L to 0L)
    }
    // Actual audio-file bytes on disk; article text is whatever downloaded bytes remain.
    val audioBytes = remember(done.size, downloadedBytes) {
        runCatching { java.io.File(ctx.filesDir, "audio").listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)
    }
    val articleBytes = (downloadedBytes - audioBytes).coerceAtLeast(0L)
    val usedBytes = (capacity - freeBytes).coerceAtLeast(0L)
    val otherBytes = (usedBytes - articleBytes - audioBytes).coerceAtLeast(0L)
    fun frac(b: Long) = if (capacity > 0) (b.toFloat() / capacity).coerceIn(0f, 1f) else 0f
    val articleFrac = frac(articleBytes)
    val audioFrac = frac(audioBytes)
    val otherFrac = frac(otherBytes)
    val doneUrls = done.map { it.url }.toSet()
    val rows = buildList {
        done.forEach { add(LibRow(it.toScpItem(), formatSize(it.sizeBytes), downloaded = true, bookmarked = app.bookmarkedUrls.contains(it.url), audio = it.hasAudio)) }
        app.bookmarks.filter { it.url !in doneUrls }.forEach { add(LibRow(it.toScpItem(), null, downloaded = false, bookmarked = true, audio = false)) }
    }
    val items = when (app.libTab) {
        "downloaded" -> rows.filter { it.downloaded }
        "audio" -> rows.filter { it.audio }
        else -> rows
    }.filter { app.libClassFilter == "all" || it.item.objectClass == app.libClassFilter }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 108.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (app.offlineMode) 8.dp else 22.dp, end = 22.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Offline mode hides the bottom nav, so give Library a back button to return Home.
            if (app.offlineMode) {
                Box(
                    Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { app.go(com.foundation.scpreader.Screen.Home) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ArrowBack, "Back", Modifier.size(24.dp), tint = c.onSurface)
                }
            }
            Text("Library", fontSize = 28.sp, color = c.onSurface)
        }

        // storage card
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)).background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(22.dp))
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (capacity > 0) formatGb(usedBytes) + " of " + formatGb(capacity) + " used" else formatSize(downloadedBytes) + " used",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.onSurface,
                )
                Text("${done.size} items", fontSize = 14.sp, color = c.onSurfaceVariant)
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceCHighest)) {
                if (articleFrac > 0f) Box(Modifier.fillMaxWidth(articleFrac).height(8.dp).background(c.primary))
                if (audioFrac > 0f) Box(Modifier.fillMaxWidth(audioFrac).height(8.dp).background(c.tertiaryContainer))
                if (otherFrac > 0f) Box(Modifier.fillMaxWidth(otherFrac).height(8.dp).background(c.onSurfaceVariant))
            }
            FlowRow(
                Modifier.padding(top = 10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StorageLegend(c.primary, "Articles", formatSize(articleBytes))
                StorageLegend(c.tertiaryContainer, "Audio", formatSize(audioBytes))
                StorageLegend(c.onSurfaceVariant, "Other", formatGb(otherBytes))
            }
        }

        BrowseByClass(app, rows.map { it.item.objectClass })

        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            libDefs.forEach { (k, label) ->
                val active = app.libTab == k
                FilterChip(
                    label, active,
                    bg = if (active) c.secondaryContainer else Color.Transparent,
                    fg = if (active) c.onSecondaryContainer else c.onSurfaceVariant,
                    border = if (active) Color.Transparent else c.outlineVariant,
                    showCheck = false, height = 34, corner = 9,
                ) { app.selectLibTab(k) }
            }
        }

        if (items.isEmpty()) {
            Text("Nothing here yet. Bookmark an article (or download it) from the reader to save it to your library.",
                fontSize = 14.sp, color = c.onSurfaceVariant, lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        } else {
            Column(Modifier.padding(horizontal = 16.dp)) {
                items.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().clickable { app.openReaderItem(row.item) }.padding(horizontal = 6.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(if (row.audio) AppIcons.Headphones else AppIcons.Article, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(row.item.displayTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.item.number + " · " + row.item.objectClass, fontSize = 13.sp, color = c.onSurfaceVariant)
                        }
                        if (row.bookmarked) Icon(AppIcons.Bookmark, null, Modifier.size(18.dp), tint = c.primary)
                        if (row.downloaded && row.size != null) Text(row.size, fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                    Divider1()
                }
            }
        }
    }
}

/** Canonical class order for the "browse offline by class" tiles; unknown classes sort last. */
private val classOrder = listOf("Safe", "Euclid", "Keter", "Thaumiel", "Archon", "Neutralized", "Explained", "Apollyon", "Esoteric", "Maksur")

/** "BROWSE BY CLASS" tile row — one tile per object class present in the library, plus All. */
@Composable
private fun BrowseByClass(app: AppState, classesInLibrary: List<String>) {
    if (classesInLibrary.isEmpty()) return
    val c = LocalScpScheme.current
    val counts = classesInLibrary.groupingBy { it }.eachCount()
    val classes = counts.keys.sortedWith(
        compareBy({ classOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it })
    )
    Text(
        "BROWSE BY CLASS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c.primary,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 10.dp),
    )
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ClassTile(app, "all", "All", R.drawable.class_all, classesInLibrary.size, null)
        classes.forEach { cls ->
            ClassTile(app, cls, cls, classBadgeRes(cls), counts[cls] ?: 0, classColors(cls, app.isDark).second)
        }
    }
}

@Composable
private fun ClassTile(
    app: AppState,
    key: String,
    label: String,
    badgeRes: Int,
    count: Int,
    accent: Color?,
) {
    val c = LocalScpScheme.current
    val active = app.libClassFilter == key
    Column(
        Modifier.width(74.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) c.primaryContainer else c.surfaceCLow)
            .border(1.dp, if (active) c.primary else c.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { app.selectLibClass(key) }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The SCP containment badge is a black gear + white symbol — draw it untinted so it
        // renders in its true colours rather than being flattened to a single tint. Classes with
        // no dedicated art resolve to the "?" unknown badge inside classBadgeRes().
        Image(painterResource(badgeRes), null, Modifier.size(30.dp))
        // Count keeps the per-class accent colour (the association the filter previously showed via
        // the icon tint), except on the active tile where onPrimaryContainer stays legible.
        Text(
            "$count", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (active) c.onPrimaryContainer else (accent ?: c.onSurface),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(label, fontSize = 12.sp, color = if (active) c.onPrimaryContainer else c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun StorageLegend(color: Color, label: String, value: String) {
    val c = LocalScpScheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Dot(color)
        Text(label, fontSize = 12.sp, color = c.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes < 1024 * 1024 -> "${(bytes / 1024).coerceAtLeast(1)} KB"
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

/** Device-scale sizes in decimal GB, matching how storage capacity is normally quoted. */
fun formatGb(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000f)
