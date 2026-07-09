package com.foundation.scpreader.ui.screens

import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.foundation.scpreader.AppState
import com.foundation.scpreader.BuildConfig
import com.foundation.scpreader.DownloadPref
import com.foundation.scpreader.HeroMode
import com.foundation.scpreader.ThemeMode
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Divider1
import com.foundation.scpreader.ui.components.PillSwitch
import com.foundation.scpreader.ui.components.ScpSpinner
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.SeedKey
import com.foundation.scpreader.update.UpdateCheckResult
import com.foundation.scpreader.update.UpdateDownloadState
import com.foundation.scpreader.update.installApk
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val modeDefs = listOf(
    Triple(ThemeMode.Light, "Light", AppIcons.LightMode),
    Triple(ThemeMode.Auto, "Auto", AppIcons.BrightnessAuto),
    Triple(ThemeMode.Dark, "Dark", AppIcons.DarkMode),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: AppState) {
    val c = LocalScpScheme.current
    val scrollState = rememberScrollState()
    // Arriving from the update banner: jump to the Updates section at the bottom.
    androidx.compose.runtime.LaunchedEffect(app.scrollSettingsToUpdates) {
        if (app.scrollSettingsToUpdates) {
            // maxValue is 0 until the content is measured, so wait for it before scrolling.
            androidx.compose.runtime.snapshotFlow { scrollState.maxValue }
                .first { it > 0 }
            scrollState.animateScrollTo(scrollState.maxValue)
            app.scrollSettingsToUpdates = false
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(bottom = 108.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.clip(CircleShape).clickable { app.go(com.foundation.scpreader.Screen.Home) }.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.ArrowBack, "Back", Modifier.size(24.dp), tint = c.onSurface)
            }
            Text("Settings", fontSize = 28.sp, color = c.onSurface)
        }

        // ---- Appearance ----
        GroupLabel("Appearance")
        SettingsCard {
            Text("Theme", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp))
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modeDefs.forEach { (mode, label, icon) ->
                    val active = app.themeMode == mode
                    Row(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.primary else Color.Transparent)
                            .then(if (active) Modifier else Modifier.border(1.dp, c.outline, RoundedCornerShape(12.dp)))
                            .clickable { app.themeMode = mode },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, null, Modifier.size(19.dp), tint = if (active) c.onPrimary else c.onSurfaceVariant)
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (active) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
            // Dynamic color only does anything on Android 12+; hide the toggle otherwise so the
            // accent picker (which does work) is always reachable.
            val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (supportsDynamic) {
                Divider1()
                Row(
                    Modifier.fillMaxWidth().clickable { app.toggleDynamic() }.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(AppIcons.Palette, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic color", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                        Text("Use colors from your wallpaper", fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                    PillSwitch(app.dynamicColor)
                }
            }
            if (!supportsDynamic || !app.dynamicColor) {
                Divider1()
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Text("Accent color", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SeedKey.entries.forEach { sk ->
                            val active = app.seed == sk
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(sk.swatch)
                                        .then(if (active) Modifier.border(3.dp, c.primary, RoundedCornerShape(16.dp)) else Modifier)
                                        .clickable { app.selectSeed(sk) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (active) Icon(AppIcons.Check, null, Modifier.size(24.dp), tint = Color.White)
                                }
                                Text(sk.label, fontSize = 11.sp, color = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // ---- Discover ----
        GroupLabel("Discover")
        SettingsCard {
            Text("Home highlight", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp))
            Text("What the top card on Home shows", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp))
            val heroDefs = listOf(
                HeroMode.ContinueReading to "Continue reading",
                HeroMode.ScpOfTheDay to "SCP of the Day",
                HeroMode.Trending to "Trending",
                HeroMode.RecentlyViewed to "Recently viewed",
            )
            FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                heroDefs.forEach { (m, label) ->
                    val active = app.heroMode == m
                    Box(
                        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) c.secondaryContainer else Color.Transparent)
                            .then(if (active) Modifier else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp)))
                            .clickable { app.selectHeroMode(m) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (active) c.onSecondaryContainer else c.onSurfaceVariant)
                    }
                }
            }
            Divider1()
            Text("Exclude object classes", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp))
            Text("Hidden from random picks & random entries", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp))
            FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Safe", "Euclid", "Keter", "Thaumiel", "Neutralized").forEach { cls ->
                    val excluded = app.excludedClasses.contains(cls)
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (excluded) c.primary else Color.Transparent)
                            .then(if (excluded) Modifier else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp)))
                            .clickable { app.toggleExcludedClass(cls) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (excluded) Icon(AppIcons.Close, null, Modifier.size(15.dp), tint = c.onPrimary)
                        Text(cls, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (excluded) c.onPrimary else c.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- Reading ----
        GroupLabel("Reading")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Text size", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                Text("${(app.fontScale * 100).roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
            }
            Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AppIcons.TextFields, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
                Slider(
                    value = app.fontScale,
                    onValueChange = { app.fontScale = (it * 20).roundToInt() / 20f },
                    valueRange = 0.85f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary),
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.TextFields, null, Modifier.size(26.dp), tint = c.onSurfaceVariant)
            }
            Text(
                "The subject remains dormant under standard containment.",
                fontSize = (16 * app.fontScale).sp, lineHeight = (16 * app.fontScale * 1.62f).sp, color = c.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp),
            )
            Divider1()
            ToggleRow(AppIcons.Image, "Load images", "Show figures inline in articles", app.loadImages) { app.toggleImages() }
        }

        // ---- Downloads & audio ----
        GroupLabel("Downloads & audio")
        SettingsCard {
            Text("Download content", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp))
            Text("Audio options apply only when a narration exists", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp))
            val prefs = listOf(
                DownloadPref.TextOnly to "Text only",
                DownloadPref.TextAudio to "Text + audio",
                DownloadPref.AudioOnly to "Audio",
                DownloadPref.Ask to "Always ask",
            )
            FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prefs.forEach { (p, label) ->
                    val active = app.downloadPref == p
                    Box(
                        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) c.secondaryContainer else Color.Transparent)
                            .then(if (active) Modifier else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp)))
                            .clickable { app.selectDownloadPref(p) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (active) c.onSecondaryContainer else c.onSurfaceVariant)
                    }
                }
            }
            Divider1()
            ToggleRow(AppIcons.Bookmark, "Auto-download bookmarks", "Save bookmarked articles offline automatically", app.autoDownloadBookmarks) { app.toggleAutoDownloadBookmarks() }
            Divider1()
            ToggleRow(AppIcons.Wifi, "Download over Wi-Fi only", null, app.wifiOnly) { app.toggleWifi() }
        }

        // ---- SponsorBlock ----
        GroupLabel("SponsorBlock")
        SettingsCard {
            Text("Auto-skip segments", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp))
            Text("Skip these parts of YouTube narrations (crowd-sourced)", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp))
            com.foundation.scpreader.playback.SponsorCategory.ALL.forEach { cat ->
                Divider1()
                ToggleRow(AppIcons.FastForward, com.foundation.scpreader.playback.SponsorCategory.label(cat), null, app.sponsorCategories.contains(cat)) {
                    app.toggleSponsorCategory(cat)
                }
            }
        }

        // ---- Updates ----
        GroupLabel("Updates")
        SettingsCard { UpdatesSection(app) }

        // ---- About ----
        val ctx = androidx.compose.ui.platform.LocalContext.current
        GroupLabel("About")
        SettingsCard {
            LinkRow(AppIcons.Public, "SCP Wiki", "scp-wiki.wikidot.com") {
                openUrl(ctx, "https://scp-wiki.wikidot.com")
            }
            Divider1()
            LinkRow(AppIcons.SmartDisplay, "YouTube source", "SCP Archives · @SCParchives") {
                openUrl(ctx, "https://www.youtube.com/@SCParchives")
            }
            Divider1()
            LinkRow(AppIcons.Headphones, "Podcast source", "SCP Archives · open in Apple Podcasts") {
                openUrl(ctx, "https://podcasts.apple.com/podcast/id1453436915")
            }
            Divider1()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(AppIcons.Info, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Content license", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                    Text("Licensed CC BY-SA 3.0 · scp-wiki.wikidot.com", fontSize = 13.sp, color = c.onSurfaceVariant, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            Text(subtitle, fontSize = 13.sp, color = c.onSurfaceVariant)
        }
        Icon(AppIcons.NorthEast, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
    }
}

@Composable
private fun GroupLabel(text: String) {
    val c = LocalScpScheme.current
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c.primary,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalScpScheme.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, on: Boolean, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.onSurfaceVariant)
        }
        PillSwitch(on)
    }
}

/**
 * GitHub PAT entry (encrypted storage; the field never shows a previously-saved token back) plus
 * update-check/download/install controls for ThePixelPro366/scp-reader's private-repo releases.
 */
@Composable
private fun UpdatesSection(app: AppState) {
    val c = LocalScpScheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val errorColor = Color(0xFFB3261E)
    var draftToken by remember { mutableStateOf("") }

    Text("GitHub access token", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp))
    Text(
        "Needed to check the private ThePixelPro366/scp-reader repo for new releases. Stored encrypted on-device.",
        fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
    )
    Row(
        Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(c.surfaceCHigh).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.VpnKey, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(start = 12.dp)) {
            if (draftToken.isEmpty()) {
                Text(if (app.hasGithubToken) "Token saved — paste a new one to replace it" else "Paste your token", fontSize = 14.sp, color = c.onSurfaceVariant)
            }
            BasicTextField(
                value = draftToken,
                onValueChange = { draftToken = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(fontSize = 14.sp, color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton("Save", filled = true, enabled = draftToken.isNotBlank()) {
            app.saveGithubToken(draftToken)
            draftToken = ""
        }
        if (app.hasGithubToken) ActionButton("Clear", filled = false) { app.clearGithubToken(); draftToken = "" }
    }

    Divider1()

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(AppIcons.SystemUpdate, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text("App version", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = c.onSurfaceVariant)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = app.updateStatus != UpdateCheckResult.Checking) { app.checkForUpdates() },
            contentAlignment = Alignment.Center,
        ) {
            if (app.updateStatus == UpdateCheckResult.Checking) ScpSpinner(size = 22)
            else Icon(AppIcons.Refresh, "Check for updates", Modifier.size(20.dp), tint = c.primary)
        }
    }

    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
        when (val status = app.updateStatus) {
            UpdateCheckResult.Idle, UpdateCheckResult.Checking -> {}
            UpdateCheckResult.NoToken -> Text("Add a token above, then check for updates", fontSize = 13.sp, color = c.onSurfaceVariant)
            UpdateCheckResult.InvalidToken -> StatusLine(AppIcons.ErrorOutline, "Token invalid or expired — update it above", errorColor)
            UpdateCheckResult.NoReleases -> Text("No releases published yet", fontSize = 13.sp, color = c.onSurfaceVariant)
            UpdateCheckResult.UpToDate -> StatusLine(AppIcons.Check, "You're up to date", c.onSurfaceVariant)
            is UpdateCheckResult.Error -> StatusLine(AppIcons.ErrorOutline, "Couldn't check for updates: ${status.message}", errorColor)
            is UpdateCheckResult.Available -> Column {
                Text("${status.release.tagName} is available", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.primary, modifier = Modifier.padding(bottom = 10.dp))
                when (val dl = app.updateDownload) {
                    UpdateDownloadState.Idle -> ActionButton("Download & install", filled = true) { app.downloadUpdate(ctx) }
                    is UpdateDownloadState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScpSpinner(size = 20)
                        Text("Downloading… ${dl.percent}%", fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                    is UpdateDownloadState.ReadyToInstall -> ActionButton("Install update", filled = true) { installApk(ctx, dl.file) }
                    is UpdateDownloadState.Failed -> Column {
                        StatusLine(AppIcons.ErrorOutline, "Download failed: ${dl.message}", errorColor)
                        Box(Modifier.padding(top = 8.dp)) { ActionButton("Retry", filled = false) { app.downloadUpdate(ctx) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(text, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    val bg = if (filled) c.primary else Color.Transparent
    val fg = if (filled) c.onPrimary else c.primary
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) bg else c.surfaceCHighest)
            .then(if (filled) Modifier else Modifier.border(1.dp, if (enabled) c.primary else c.outlineVariant, RoundedCornerShape(12.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (enabled) fg else c.onSurfaceVariant)
    }
}
