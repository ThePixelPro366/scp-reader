package com.foundation.scpreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.foundation.scpreader.data.InlineSpan
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foundation.scpreader.AppState
import com.foundation.scpreader.ReaderDlState
import com.foundation.scpreader.data.ContentBlock
import com.foundation.scpreader.data.ScpItem
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.classColors
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(app: AppState, item: ScpItem) {
    val c = LocalScpScheme.current
    val dl = app.readerDl
    val bodyPx = (16 * app.fontScale).roundToInt()
    val headPx = (18 * app.fontScale).roundToInt()
    val (classBg, classText) = classColors(item.objectClass, app.isDark)
    val episode = app.episodeForReader()
    val playback by app.player.state.collectAsStateWithLifecycle()
    val thisPlaying = episode != null && playback.episode?.mediaId == episode.mediaId && playback.isPlaying

    val offlineLabel = when (dl.state) {
        ReaderDlState.Done -> if (dl.audio) "Article + audio" else "Article"
        ReaderDlState.Downloading -> "Downloading ${dl.pct}%"
        else -> "Not downloaded"
    }
    val offlineColor = if (dl.state == ReaderDlState.Done) c.primary else c.onSurfaceVariant

    val linkCtx = androidx.compose.ui.platform.LocalContext.current
    val onLink: (String) -> Unit = { url -> app.openLink(url, linkCtx) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val showScrollTop by remember { derivedStateOf { scrollState.value > 1200 } }
    // Restore the saved scroll offset once the article content has been laid out.
    LaunchedEffect(item.url, app.article, scrollState.maxValue) {
        if (app.article != null && !app.readerScrollConsumed && scrollState.maxValue > 0) {
            val target = app.readerScrollRestore.coerceAtMost(scrollState.maxValue)
            if (target > 0) scrollState.scrollTo(target)
            app.readerScrollConsumed = true
        }
    }
    // Persist the scroll offset only when the user leaves — closing/switching the article
    // (onDispose) or backgrounding the app (ON_STOP) — never mid-scroll, to keep scrolling smooth.
    // Guarded by readerScrollConsumed so a quick open/close can't clobber a saved position with 0.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, item.url) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && app.readerScrollConsumed) app.saveReaderScroll(item.url, scrollState.value)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (app.readerScrollConsumed) app.saveReaderScroll(item.url, scrollState.value)
        }
    }

    Column(Modifier.fillMaxSize().background(c.surface)) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars))

        // app bar
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(AppIcons.ArrowBack, 24) { app.closeReader() }
            Spacer(Modifier.weight(1f))
            IconBtn(AppIcons.TextDecrease, 20) { app.decFont() }
            IconBtn(AppIcons.TextIncrease, 24) { app.incFont() }
            val bookmarked = app.readerBookmarked
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { app.toggleReaderBookmark() }, contentAlignment = Alignment.Center) {
                Icon(if (bookmarked) AppIcons.Bookmark else AppIcons.BookmarkBorder, "Bookmark", Modifier.size(24.dp), tint = if (bookmarked) c.primary else c.onSurface)
            }
            Box(Modifier.size(44.dp).clickable { app.onReaderDownloadTap() }, contentAlignment = Alignment.Center) {
                when (dl.state) {
                    ReaderDlState.Downloading -> Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { dl.pct / 100f }, modifier = Modifier.size(38.dp), color = c.primary, trackColor = c.surfaceCHighest, strokeWidth = 3.dp)
                        Text("${dl.pct}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.primary)
                    }
                    ReaderDlState.Done -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.DownloadDone, null, Modifier.size(22.dp), tint = c.onPrimary)
                    }
                    else -> Icon(AppIcons.Download, null, Modifier.size(24.dp), tint = c.onSurface)
                }
            }
        }

        // Reading-progress bar. Scroll position is read in the draw phase (drawBehind), so the
        // bar repaints on scroll WITHOUT recomposing the reader — keeps scrolling smooth.
        if (app.article != null) {
            val progressColor = c.primary
            Box(
                Modifier.fillMaxWidth().height(2.dp).background(c.surfaceCHighest).drawBehind {
                    val max = scrollState.maxValue
                    val frac = if (max > 0) (scrollState.value.toFloat() / max).coerceIn(0f, 1f) else 0f
                    if (frac > 0f) drawRect(color = progressColor, size = Size(size.width * frac, size.height))
                }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (app.articleLoading && app.article == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { com.foundation.scpreader.ui.components.ScpSpinner(size = 84) }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
                    Text(
                        "OBJECT CLASS · ${item.objectClass.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = classText,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(classBg).padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                    Text(item.number, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.primary, modifier = Modifier.padding(top = 18.dp))
                    Text(item.title, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = c.onSurface, lineHeight = 34.sp, modifier = Modifier.padding(top = 4.dp))

                    if (episode != null) {
                        Row(
                            Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.primaryContainer)
                                .clickable { app.playReaderNarration() }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                                Icon(if (thisPlaying) AppIcons.Pause else AppIcons.PlayArrow, null, Modifier.size(26.dp), tint = c.onPrimary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (thisPlaying) "Playing narration" else "Narration available", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer)
                                Text("SCP Archives · ${fmtDuration(episode.durationSec)}", fontSize = 13.sp, color = c.onPrimaryContainer.copy(alpha = 0.85f))
                            }
                            Icon(AppIcons.Headphones, null, Modifier.size(22.dp), tint = c.onPrimaryContainer)
                        }
                    }

                    Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetaCard(Modifier.weight(1f), "Type", item.typeLabel, c.onSurface)
                        MetaCard(Modifier.weight(1f), "Offline", offlineLabel, offlineColor)
                    }

                    // article body blocks
                    val blocks = app.article?.blocks.orEmpty()
                    // Revealed redaction keys, reset per article.
                    val revealed = remember(item.url) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
                    val onReveal: (String) -> Unit = { k -> revealed[k] = revealed[k] != true }
                    blocks.forEachIndexed { idx, block ->
                        ArticleBlock(app, block, "b$idx", bodyPx, headPx, onLink, revealed, onReveal)
                    }
                    if (blocks.isEmpty()) {
                        Text("No content available.", fontSize = bodyPx.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
                    }

                    // related — only real crosslinks referenced within this article
                    val related = app.article?.crosslinks.orEmpty()
                    if (related.isNotEmpty()) {
                        Text("Related", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, modifier = Modifier.padding(top = 26.dp))
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            related.forEach { r -> RelatedRow(app, r) }
                        }
                    }

                    // open the source page on the wiki
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        Modifier.padding(top = 26.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { openUrl(ctx, item.url) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(AppIcons.NorthEast, null, Modifier.size(20.dp), tint = c.primary)
                        Text("Open original wiki page", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.primary, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Scroll-to-top button, shown once the reader is well down the article.
            val fab by animateFloatAsState(if (showScrollTop) 1f else 0f, label = "scrollTopFab")
            if (fab > 0f) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(20.dp)
                        .graphicsLayer { scaleX = fab; scaleY = fab; alpha = fab }
                        .size(48.dp).clip(CircleShape).background(c.primary)
                        .clickable { scope.launch { scrollState.animateScrollTo(0) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.KeyboardArrowUp, "Scroll to top", Modifier.size(28.dp), tint = c.onPrimary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }

    if (app.readerMenuOpen) ReaderDownloadMenu(app, hasEpisode = episode != null, offlineLabel = offlineLabel)
}

/**
 * Build a styled [AnnotatedString] from scraped inline spans, falling back to plain text.
 * Linked runs become clickable, routed through [onLink]; [linkColor] tints them.
 */
private fun markup(
    spans: List<InlineSpan>,
    fallback: String,
    linkColor: Color,
    onLink: (String) -> Unit,
    redactColor: Color = Color.Black,
    keyPrefix: String = "",
    revealed: Map<String, Boolean> = emptyMap(),
    onReveal: (String) -> Unit = {},
): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(fallback)
    return buildAnnotatedString {
        spans.forEachIndexed { idx, s ->
            // Redacted run: a tap-to-reveal black bar (bar hides the text under matching fg/bg).
            if (s.redacted) {
                val key = "$keyPrefix#$idx"
                val isShown = revealed[key] == true
                val redStyle = if (isShown) SpanStyle(background = redactColor.copy(alpha = 0.18f))
                else SpanStyle(color = redactColor, background = redactColor)
                val link = LinkAnnotation.Clickable(tag = key) { onReveal(key) }
                withLink(link) { withStyle(redStyle) { append(s.text) } }
                return@forEachIndexed
            }
            val decoration = when {
                s.underline && s.strike -> TextDecoration.Underline + TextDecoration.LineThrough
                s.underline -> TextDecoration.Underline
                s.strike -> TextDecoration.LineThrough
                else -> null
            }
            val style = SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = decoration,
            )
            val href = s.link
            if (href != null) {
                val link = LinkAnnotation.Clickable(
                    tag = href,
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ) { onLink(href) }
                withLink(link) { withStyle(style) { append(s.text) } }
            } else {
                withStyle(style) { append(s.text) }
            }
        }
    }
}

/** Renders one article block. Recurses for collapsible content, so it must be a composable. */
@Composable
private fun ArticleBlock(
    app: AppState,
    block: ContentBlock,
    key: String,
    bodyPx: Int,
    headPx: Int,
    onLink: (String) -> Unit,
    revealed: Map<String, Boolean>,
    onReveal: (String) -> Unit,
) {
    val c = LocalScpScheme.current
    when (block) {
        is ContentBlock.Heading -> Text(markup(block.spans, block.text, c.primary, onLink, c.onSurface, key, revealed, onReveal), fontSize = headPx.sp, fontWeight = FontWeight.SemiBold, color = c.primary, modifier = Modifier.padding(top = 24.dp))
        is ContentBlock.Paragraph -> Text(markup(block.spans, block.text, c.primary, onLink, c.onSurface, key, revealed, onReveal), fontSize = bodyPx.sp, lineHeight = (bodyPx * 1.62f).sp, color = c.onSurface, modifier = Modifier.padding(top = 12.dp))
        is ContentBlock.Quote -> Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceContainer).height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(c.primary))
            Text(markup(block.spans, block.text, c.primary, onLink, c.onSurface, key, revealed, onReveal), fontSize = bodyPx.sp, lineHeight = (bodyPx * 1.62f).sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        is ContentBlock.Image -> if (app.loadImages) {
            Column(Modifier.padding(top = 20.dp)) {
                AsyncImage(model = block.url, contentDescription = block.caption, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceCHigh))
                if (block.caption.isNotEmpty()) Text(block.caption, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        is ContentBlock.Acs -> AcsBar(block)
        is ContentBlock.Collapsible -> {
            var expanded by remember(key) { androidx.compose.runtime.mutableStateOf(false) }
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(12.dp))) {
                Row(
                    Modifier.fillMaxWidth().background(c.surfaceContainer).clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown, null, Modifier.size(22.dp), tint = c.primary)
                    Text(block.title, fontSize = (bodyPx - 1).sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, modifier = Modifier.weight(1f))
                }
                if (expanded) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                        block.blocks.forEachIndexed { i, inner ->
                            ArticleBlock(app, inner, "$key.$i", bodyPx, headPx, onLink, revealed, onReveal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcsBar(acs: ContentBlock.Acs) {
    val c = LocalScpScheme.current
    val fields = listOfNotNull(
        acs.containment?.let { "Containment" to it },
        acs.secondary?.let { "Secondary" to it },
        acs.disruption?.let { "Disruption" to it },
        acs.risk?.let { "Risk" to it },
    )
    if (fields.isEmpty()) return
    Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainer).padding(14.dp)) {
        Text("ANOMALY CLASSIFICATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = c.primary)
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fields.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

internal fun openUrl(context: android.content.Context, url: String) {
    val https = url.replaceFirst("http://", "https://")
    val uri = android.net.Uri.parse(https)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
    // We register as a VIEW handler for wiki hosts, so we must target a real browser explicitly —
    // otherwise this loops back into our own app. Enumerate handlers for a non-wiki URL (a set we
    // are NOT part of) and pin the intent to one of those browser packages.
    runCatching {
        val pm = context.packageManager
        val probe = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.example.com"))
            .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        val browsers = pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }
        val preferred = pm.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val browser = listOfNotNull(preferred).plus(browsers)
            .firstOrNull { it != context.packageName && !it.contains("resolver", true) }
        if (browser != null) intent.setPackage(browser)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) } }
}

private fun fmtDuration(sec: Int): String {
    if (sec <= 0) return "audio"
    val m = sec / 60
    return if (m >= 1) "$m min" else "$sec s"
}

@Composable
private fun IconBtn(icon: ImageVector, iconSize: Int, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize.dp), tint = c.onSurface)
    }
}

@Composable
private fun MetaCard(modifier: Modifier, label: String, value: String, valueColor: Color) {
    val c = LocalScpScheme.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, fontSize = 12.sp, color = c.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = valueColor, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RelatedRow(app: AppState, r: ScpItem) {
    val c = LocalScpScheme.current
    val (bg, txt) = classColors(r.objectClass, app.isDark)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceContainer)
            .clickable { app.openReaderItem(r) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(r.objectClass.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txt,
            modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(r.number, fontFamily = Mono, fontSize = 12.sp, color = c.primary)
            Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.NorthEast, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
    }
}

@Composable
private fun ReaderDownloadMenu(app: AppState, hasEpisode: Boolean, offlineLabel: String) {
    val c = LocalScpScheme.current
    Box(Modifier.fillMaxSize().clickable { app.closeReaderMenu() })
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopEnd) {
        Column(Modifier.padding(top = 56.dp, end = 12.dp).width(244.dp).clip(RoundedCornerShape(18.dp)).background(c.surfaceCHigh).padding(6.dp)) {
            when (app.readerDl.state) {
                ReaderDlState.Done -> {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(AppIcons.DownloadDone, null, Modifier.size(22.dp), tint = c.primary)
                        Text("Saved offline · $offlineLabel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { app.removeDownload() }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(AppIcons.Delete, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
                        Text("Remove download", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                    }
                }
                ReaderDlState.Downloading -> {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(progress = { app.readerDl.pct / 100f }, modifier = Modifier.size(20.dp), color = c.primary, trackColor = c.surfaceCHighest, strokeWidth = 2.dp)
                        Text("Downloading · ${app.readerDl.pct}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { app.startDownload(false) }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(AppIcons.Article, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text("Article only", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                            Text("Text · saved offline", fontSize = 12.sp, color = c.onSurfaceVariant)
                        }
                    }
                    if (hasEpisode) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.secondaryContainer).clickable { app.startDownload(true) }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(AppIcons.Headphones, null, Modifier.size(22.dp), tint = c.onSecondaryContainer)
                            Column(Modifier.weight(1f)) {
                                Text("Article + narration", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondaryContainer)
                                Text("Text & audio", fontSize = 12.sp, color = c.onSecondaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }
}
