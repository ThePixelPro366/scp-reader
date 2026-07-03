package com.foundation.scpreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundation.scpreader.AppState
import com.foundation.scpreader.data.toScpItem
import com.foundation.scpreader.database.DlStatus
import com.foundation.scpreader.database.DownloadEntity
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Divider1
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.classColors

@Composable
fun DownloadsScreen(app: AppState) {
    val c = LocalScpScheme.current
    val active = app.downloads.filter { it.status == DlStatus.ACTIVE }
    val queued = app.downloads.filter { it.status == DlStatus.QUEUED }
    val done = app.downloads.filter { it.status == DlStatus.DONE }
    val activeMean = if (active.isNotEmpty()) active.sumOf { it.progress } / active.size else 100
    val paused = app.downloadsPaused

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 108.dp)) {
        Text("Downloads", fontSize = 28.sp, color = c.onSurface, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp))

        // bulk download entry (random pool / top-rated N / entire archive)
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(c.secondaryContainer)
                .clickable { app.randomOpen = true }.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(AppIcons.Shuffle, null, Modifier.size(30.dp), tint = c.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Bulk download", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondaryContainer)
                Text("Random, top-rated, a series, or the entire archive", fontSize = 13.sp, color = c.onSecondaryContainer.copy(alpha = 0.85f))
            }
            Icon(AppIcons.ChevronRight, null, Modifier.size(22.dp), tint = c.onSecondaryContainer)
        }

        // overall status card
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(c.primaryContainer).padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (active.isEmpty() && queued.isEmpty()) "All downloads complete"
                    else if (paused) "Paused · ${queued.size} waiting"
                    else "${active.size} downloading · ${queued.size} queued",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer, modifier = Modifier.weight(1f),
                )
                if (active.isNotEmpty() || queued.isNotEmpty()) {
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.primary)
                            .clickable { app.togglePauseAll() }.padding(start = 12.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(if (paused) AppIcons.PlayArrow else AppIcons.Pause, null, Modifier.size(18.dp), tint = c.onPrimary)
                        Text(if (paused) "Resume all" else "Pause all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.onPrimaryContainer.copy(alpha = 0.13f))) {
                    Box(Modifier.fillMaxWidth(activeMean / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.primary))
                }
                Text(if (paused) "Paused" else "$activeMean%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer)
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(AppIcons.Bolt, null, Modifier.size(16.dp), tint = c.onPrimaryContainer.copy(alpha = 0.85f))
                Text("${done.size} saved offline", fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.85f))
            }
        }

        QueueSectionLabel("Downloading · ${active.size}")
        Column(Modifier.padding(horizontal = 16.dp)) { active.forEach { d -> ActiveCard(d, app) } }

        QueueSectionLabel("In queue · ${queued.size}")
        Column(Modifier.padding(horizontal = 16.dp)) { queued.forEach { d -> QueuedRow(d, app) } }

        QueueSectionLabel("Completed · ${done.size}")
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { done.forEach { d -> DoneRow(d, app) } }
    }
}

@Composable
private fun QueueSectionLabel(text: String) {
    val c = LocalScpScheme.current
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = c.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun QueueBadge(objectClass: String, isDark: Boolean) {
    val (bg, txt) = classColors(objectClass, isDark)
    Text(objectClass.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = txt,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun ActiveCard(d: DownloadEntity, app: AppState) {
    val c = LocalScpScheme.current
    Column(
        Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QueueBadge(d.objectClass, app.isDark)
            Column(Modifier.weight(1f)) {
                Text(d.number, fontFamily = Mono, fontSize = 11.sp, color = c.primary)
                Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${d.progress}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)
            Box(Modifier.size(22.dp).clickable { app.cancelDownload(d.url) }, contentAlignment = Alignment.Center) {
                Icon(AppIcons.Close, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
            }
        }
        Box(Modifier.padding(top = 11.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.surfaceCHighest)) {
            Box(Modifier.fillMaxWidth(d.progress / 100f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.primary))
        }
    }
}

@Composable
private fun QueuedRow(d: DownloadEntity, app: AppState) {
    val c = LocalScpScheme.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(AppIcons.DragIndicator, null, Modifier.size(20.dp), tint = c.outline)
        QueueBadge(d.objectClass, app.isDark)
        Column(Modifier.weight(1f)) {
            Text(d.number, fontFamily = Mono, fontSize = 11.sp, color = c.primary)
            Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(20.dp).clickable { app.cancelDownload(d.url) }, contentAlignment = Alignment.Center) {
            Icon(AppIcons.Close, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
        }
    }
    Divider1()
}

@Composable
private fun DoneRow(d: DownloadEntity, app: AppState) {
    val c = LocalScpScheme.current
    Row(Modifier.fillMaxWidth().clickable { app.openReaderItem(d.toScpItem()) }.padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(AppIcons.DownloadDone, null, Modifier.size(22.dp), tint = c.primary)
        Column(Modifier.weight(1f)) {
            Text(d.number, fontFamily = Mono, fontSize = 11.sp, color = c.onSurfaceVariant)
            Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(20.dp).clickable { app.cancelDownload(d.url) }, contentAlignment = Alignment.Center) {
            Icon(AppIcons.Close, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
        }
    }
    Divider1()
}
