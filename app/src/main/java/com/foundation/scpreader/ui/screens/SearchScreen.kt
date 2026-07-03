package com.foundation.scpreader.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foundation.scpreader.AppState
import com.foundation.scpreader.data.ScpItem
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Divider1
import com.foundation.scpreader.ui.components.FilterChip
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.components.chipStyle
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.classColors

private val classDefs = listOf("Safe", "Euclid", "Keter", "Thaumiel")
private val tagDefs = listOf("humanoid", "keter", "hostile", "building", "location", "sapient", "extradimensional", "reality-bending")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: AppState) {
    val c = LocalScpScheme.current
    val suggestions = app.searchSuggestions
    val results = app.searchResults
    val filtersActive = app.typeFilter != "all" || app.classFilter != "any" || app.activeTags.isNotEmpty() || app.audioOnly

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 108.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.surfaceCHigh)
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcons.Search, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (app.searchQuery.isEmpty()) Text("Search the archive", fontSize = 16.sp, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = app.searchQuery,
                        onValueChange = { app.onSearchQuery(it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = c.onSurface),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (app.searchQuery.isNotEmpty()) Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { app.onSearchQuery("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Close, "Clear search", Modifier.size(20.dp), tint = c.onSurfaceVariant)
                }
                if (app.searchLoading) com.foundation.scpreader.ui.components.ScpSpinner(size = 28)
                else Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .then(if (filtersActive) Modifier.background(c.secondaryContainer) else Modifier)
                        .clickable { app.searchFiltersOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Tune, "Filters", Modifier.size(22.dp), tint = if (filtersActive) c.primary else c.onSurfaceVariant)
                }
            }
            if (suggestions.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp)),
                ) {
                    suggestions.forEach { sug ->
                        Row(
                            Modifier.fillMaxWidth().clickable { app.openReaderItem(sug) }.padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(AppIcons.NorthWest, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(sug.number, fontFamily = Mono, fontSize = 12.sp, color = c.primary)
                                Text(sug.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val (bg, txt) = classColors(sug.objectClass, app.isDark)
                            Text(sug.objectClass.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = txt,
                                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            val n = results.size
            Text(
                if (app.searchQuery.isBlank()) "SEARCH THE ARCHIVE" else "$n RESULT${if (n == 1) "" else "S"}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = c.onSurfaceVariant,
            )
            if (app.searchQuery.isNotBlank()) SortSelector(app)
        }

        if (app.searchQuery.isBlank()) {
            if (app.searchRecentlyViewed.isEmpty()) {
                Text("Type to search SCPs, tales and GoI documents across the whole wiki.",
                    fontSize = 14.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RECENTLY VIEWED", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.primary,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { app.clearSearchRecents() }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    app.searchRecentlyViewed.forEach { item -> ResultRow(app, item) }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp)) { results.forEach { item -> ResultRow(app, item) } }
        }
    }

    if (app.searchFiltersOpen) FilterSheet(app)
}

private val searchTypeDefs = listOf("all" to "All", "scp" to "SCP", "tale" to "Tales", "goi" to "GoI")

/** Popup with all search filters — type, object class and tags — sharing one chip style. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(app: AppState) {
    val c = LocalScpScheme.current
    val filtersActive = app.typeFilter != "all" || app.classFilter != "any" || app.activeTags.isNotEmpty() || app.audioOnly
    Dialog(onDismissRequest = { app.searchFiltersOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(c.surfaceContainer).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.weight(1f))
                if (filtersActive) Text("Reset", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.primary,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { app.clearSearchFilters() }.padding(horizontal = 12.dp, vertical = 6.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceCHigh).clickable { app.searchFiltersOpen = false }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, "Close", Modifier.size(22.dp), tint = c.onSurface)
                }
            }

            SectionLabel("Type")
            FilterFlow {
                searchTypeDefs.forEach { (k, label) ->
                    val active = app.typeFilter == k
                    val st = c.chipStyle(active)
                    FilterChip(label, active, st.bg, st.fg, st.border) { app.setType(k) }
                }
            }

            SectionLabel("Availability")
            FilterFlow {
                val active = app.audioOnly
                val st = c.chipStyle(active)
                FilterChip("Narration available", active, st.bg, st.fg, st.border) { app.toggleAudioOnly() }
            }

            SectionLabel("Object class")
            FilterFlow {
                classDefs.forEach { label ->
                    val active = app.classFilter == label
                    val (bg, txt) = classColors(label, app.isDark)
                    FilterChip(
                        label = label.uppercase(), active = active,
                        bg = if (active) bg else Color.Transparent,
                        fg = if (active) txt else c.onSurfaceVariant,
                        border = if (active) Color.Transparent else c.outlineVariant,
                        showCheck = false,
                    ) { app.setClass(label) }
                }
            }

            SectionLabel("Tags")
            var tagQuery by remember { mutableStateOf("") }
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceCLow)
                    .border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Sell, null, Modifier.size(19.dp), tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (tagQuery.isEmpty()) Text("Filter tags, e.g. humanoid", fontSize = 15.sp, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = tagQuery,
                        onValueChange = { tagQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = c.onSurface),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (tagQuery.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable { tagQuery = "" }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, "Clear", Modifier.size(18.dp), tint = c.onSurfaceVariant)
                }
            }
            Box(Modifier.height(14.dp))
            val q = tagQuery.trim().lowercase()
            val matches = if (q.isEmpty()) tagDefs else app.tagVocab.filter { it.contains(q) }.take(30)
            // Active tags stay visible (and removable) even when they don't match the filter text.
            val shownTags = (app.activeTags + matches).distinct()
            FilterFlow {
                if (shownTags.isEmpty()) Text("No matching tags", fontSize = 13.sp, color = c.onSurfaceVariant)
                shownTags.forEach { label ->
                    val active = app.activeTags.contains(label)
                    val st = c.chipStyle(active)
                    FilterChip(label, active, st.bg, st.fg, st.border) { app.toggleTag(label) }
                }
            }

            Row(
                Modifier.padding(top = 22.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.primary)
                    .clickable { app.searchFiltersOpen = false },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterFlow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.padding(bottom = 4.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SortSelector(app: AppState) {
    val c = LocalScpScheme.current
    var open by remember { mutableStateOf(false) }
    val label = when (app.searchSort) {
        com.foundation.scpreader.SortMode.Relevance -> "Relevance"
        com.foundation.scpreader.SortMode.Rating -> "Top rated"
        com.foundation.scpreader.SortMode.Newest -> "Newest"
    }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.primary)
            Text("▾", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                com.foundation.scpreader.SortMode.Relevance to "Relevance",
                com.foundation.scpreader.SortMode.Rating to "Top rated",
                com.foundation.scpreader.SortMode.Newest to "Newest",
            ).forEach { (mode, l) ->
                DropdownMenuItem(
                    text = { Text(l, fontWeight = if (app.searchSort == mode) FontWeight.SemiBold else FontWeight.Normal, color = if (app.searchSort == mode) c.primary else c.onSurface) },
                    onClick = { app.selectSearchSort(mode); open = false },
                )
            }
        }
    }
}

@Composable
private fun ResultRow(app: AppState, item: ScpItem) {
    val c = LocalScpScheme.current
    Row(
        Modifier.fillMaxWidth().clickable { app.openReaderItem(item) }.padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val (bg, txt) = classColors(item.objectClass, app.isDark)
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(item.classShort, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txt)
        }
        Column(Modifier.weight(1f)) {
            Text(item.number, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.primary)
            Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.metaLine, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.podcast) Icon(AppIcons.Headphones, null, Modifier.size(20.dp), tint = c.primary)
        if (item.downloaded) Icon(AppIcons.DownloadDone, null, Modifier.size(20.dp), tint = c.primary)
    }
    Divider1()
}

@Composable
fun SectionLabel(text: String) {
    val c = LocalScpScheme.current
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = c.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp))
}
