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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundation.scpreader.AppState
import com.foundation.scpreader.RandomMode
import com.foundation.scpreader.RandomType
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.theme.LocalScpScheme
import kotlin.math.roundToInt

private val rTypeDefs = listOf(RandomType.Scp to "SCPs", RandomType.Tale to "Tales", RandomType.Goi to "GoI docs")
private val rModeDefs = listOf(RandomMode.Pool to "Random", RandomMode.TopRated to "Top rated", RandomMode.Entire to "Entire", RandomMode.Series to "Series")
private val popularPool = listOf("humanoid", "keter", "safe", "euclid", "building", "reptilian", "location", "alive", "mind-affecting", "autonomous")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RandomSheet(app: AppState) {
    val c = LocalScpScheme.current
    val rTypeName = when (app.randomType) {
        RandomType.Scp -> "SCP articles"; RandomType.Tale -> "tales"; RandomType.Goi -> "GoI documents"
    }
    val unit = when (app.randomType) {
        RandomType.Scp -> if (app.randomCount == 1) "SCP" else "SCPs"
        RandomType.Tale -> if (app.randomCount == 1) "tale" else "tales"
        RandomType.Goi -> "docs"
    }
    val tagInputRaw = app.randomTagInput.trim().lowercase()
    val sourcePool = if (tagInputRaw.isNotEmpty()) app.tagVocab.filter { it.contains(tagInputRaw) } else popularPool
    val suggestions = sourcePool.filter { !app.randomTags.contains(it) }.take(6)
    val tagPart = if (app.randomTags.isNotEmpty()) " tagged " + app.randomTags.joinToString(", ") else " (any tag)"
    val estMb = app.randomCount * (if (app.randomType == RandomType.Goi) 0.06 else 0.05)
    val summary = when (app.randomMode) {
        com.foundation.scpreader.RandomMode.Pool ->
            "Queue ${app.randomCount} random $rTypeName$tagPart for offline reading. Est. ${(estMb * 10).roundToInt() / 10.0} MB."
        com.foundation.scpreader.RandomMode.TopRated ->
            "Download the ${app.randomCount} highest-rated $rTypeName${if (app.randomTags.isNotEmpty()) " tagged " + app.randomTags.first() else ""} for offline reading."
        com.foundation.scpreader.RandomMode.Entire ->
            "Download the entire $rTypeName archive${if (app.randomTags.isNotEmpty()) " tagged " + app.randomTags.first() else ""} for offline reading. This can take a while and use significant storage."
        com.foundation.scpreader.RandomMode.Series -> {
            val lo = if (app.randomSeries == 1) 1 else (app.randomSeries - 1) * 1000
            "Download every SCP in Series ${app.randomSeries} (SCP-${"%03d".format(lo)}–${app.randomSeries * 1000 - 1}) for offline reading."
        }
    }
    val ctaLabel = when (app.randomMode) {
        com.foundation.scpreader.RandomMode.Pool -> "Download ${app.randomCount} $unit"
        com.foundation.scpreader.RandomMode.TopRated -> "Download top ${app.randomCount}"
        com.foundation.scpreader.RandomMode.Entire -> "Download entire archive"
        com.foundation.scpreader.RandomMode.Series -> "Download Series ${app.randomSeries}"
    }

    // scrim
    Box(Modifier.fillMaxSize().background(c.scrim).clickable { app.randomOpen = false }, contentAlignment = Alignment.BottomCenter) {
        // sheet — consume clicks so taps inside don't dismiss
        Column(
            Modifier.fillMaxWidth().clickable(enabled = false) {}.clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(c.surfaceContainer).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
        ) {
            Box(Modifier.padding(bottom = 16.dp).size(width = 34.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline.copy(alpha = 0.5f)).align(Alignment.CenterHorizontally))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AppIcons.Shuffle, null, Modifier.size(26.dp), tint = c.primary)
                Text("Bulk download", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceCHigh).clickable { app.randomOpen = false }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, null, Modifier.size(22.dp), tint = c.onSurface)
                }
            }

            SheetLabel("Mode")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rModeDefs.forEach { (m, label) ->
                    val active = app.randomMode == m
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.primary else Color.Transparent)
                            .then(if (active) Modifier else Modifier.border(1.dp, c.outline, RoundedCornerShape(12.dp)))
                            .clickable { app.selectRandomMode(m) }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (active) c.onPrimary else c.onSurfaceVariant)
                    }
                }
            }

            if (app.randomMode == RandomMode.Series) {
                SheetLabel("Which series")
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..10).forEach { n ->
                        val active = app.randomSeries == n
                        Box(
                            Modifier.height(44.dp).width(64.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (active) c.primary else Color.Transparent)
                                .then(if (active) Modifier else Modifier.border(1.dp, c.outline, RoundedCornerShape(12.dp)))
                                .clickable { app.selectRandomSeries(n) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$n", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (active) c.onPrimary else c.onSurfaceVariant)
                        }
                    }
                }
            }

            if (app.randomMode != RandomMode.Series) {
            SheetLabel("Type")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rTypeDefs.forEach { (t, label) ->
                    val active = app.randomType == t
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.primary else Color.Transparent)
                            .then(if (active) Modifier else Modifier.border(1.dp, c.outline, RoundedCornerShape(12.dp)))
                            .clickable { app.selectRandomType(t) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (active) c.onPrimary else c.onSurfaceVariant)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("FILTER BY TAG", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = c.onSurfaceVariant)
                Text("optional", fontSize = 12.sp, color = c.onSurfaceVariant)
            }
            // tag input
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceCLow)
                    .border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Sell, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (app.randomTagInput.isEmpty()) Text("Type any tag, e.g. humanoid", fontSize = 15.sp, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = app.randomTagInput,
                        onValueChange = { app.randomTagInput = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = c.onSurface),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { app.addRandomTag(app.randomTagInput) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { app.addRandomTag(app.randomTagInput) }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Add, null, Modifier.size(22.dp), tint = c.primary)
                }
            }

            if (app.randomTags.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.randomTags.forEach { tok ->
                        Row(
                            Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(c.secondaryContainer).padding(start = 14.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tok, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.onSecondaryContainer)
                            Box(Modifier.size(26.dp).clip(CircleShape).clickable { app.removeRandomTag(tok) }, contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Close, null, Modifier.size(17.dp), tint = c.onSecondaryContainer)
                            }
                        }
                    }
                }
            } else {
                Text("No tag filter — you'll get any $rTypeName.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }

            Text(if (tagInputRaw.isNotEmpty()) "Matching tags" else "Popular tags", fontSize = 12.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { label ->
                    Row(
                        Modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(9.dp))
                            .clickable { app.addRandomTag(label) }.padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(AppIcons.Add, null, Modifier.size(15.dp), tint = c.onSurfaceVariant)
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.onSurfaceVariant)
                    }
                }
            }

            if (app.randomMode != RandomMode.Entire) {
                SheetLabel("How many")
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Text("${app.randomCount}", fontSize = 64.sp, fontWeight = FontWeight.Light, color = c.primary)
                    Text(unit, fontSize = 16.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                }
                Slider(
                    value = app.randomCount.toFloat(),
                    onValueChange = { app.randomCount = it.roundToInt() },
                    valueRange = 1f..500f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", fontSize = 12.sp, color = c.onSurfaceVariant)
                    Text("500", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
            }
            } // end (mode != Series)

            Text(summary, fontSize = 14.sp, lineHeight = 21.sp, color = c.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceCLow).padding(horizontal = 16.dp, vertical = 14.dp))

            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.primary)
                    .clickable { app.startRandomDownload() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(AppIcons.Download, null, Modifier.size(22.dp), tint = c.onPrimary)
                Text(ctaLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    val c = LocalScpScheme.current
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = c.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}
