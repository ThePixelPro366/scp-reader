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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.foundation.scpreader.AppState
import com.foundation.scpreader.DownloadPref
import com.foundation.scpreader.HeroMode
import com.foundation.scpreader.ThemeMode
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Divider1
import com.foundation.scpreader.ui.components.PillSwitch
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.SeedKey
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 108.dp)) {
        Text("Settings", fontSize = 28.sp, color = c.onSurface, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 8.dp))

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
            Divider1()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(
                Modifier.fillMaxWidth().clickable { com.foundation.scpreader.ui.screens.openUrl(ctx, "https://podcasts.apple.com/podcast/id1453436915") }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(AppIcons.Headphones, null, Modifier.size(24.dp), tint = c.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Podcast source", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                    Text("SCP Archives · open in Apple Podcasts", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                Icon(AppIcons.NorthEast, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
            }
        }

        Text(
            "Content licensed CC BY-SA 3.0 · scp-wiki.wikidot.com",
            fontSize = 12.sp, lineHeight = 19.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 10.dp),
        )
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
