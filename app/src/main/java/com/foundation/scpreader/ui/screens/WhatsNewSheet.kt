package com.foundation.scpreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundation.scpreader.AppState
import com.foundation.scpreader.BuildConfig
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.theme.LocalScpScheme

/**
 * Bottom sheet showing the current build's GitHub release description (the "What's new" notes).
 * The body is GitHub-flavoured Markdown; [ReleaseNotes] renders the small subset releases use
 * (headings, bold section labels, bullet lists) — enough to read cleanly without a full parser.
 */
@Composable
fun WhatsNewSheet(app: AppState) {
    val c = LocalScpScheme.current
    val onDismiss = { app.dismissWhatsNew() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(
            indication = null, interactionSource = remember { MutableInteractionSource() },
        ) { onDismiss() },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val maxHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.8f
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).windowInsetsPadding(WindowInsets.navigationBars)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(c.surfaceCHigh)
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WHAT'S NEW", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = c.primary)
                    Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, "Close", Modifier.size(18.dp), tint = c.onSurfaceVariant)
                }
            }

            Column(Modifier.padding(top = 14.dp).verticalScroll(rememberScrollState())) {
                when {
                    app.whatsNewLoading && app.whatsNewNotes == null ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = c.primary, strokeWidth = 3.dp)
                        }
                    app.whatsNewNotes.isNullOrBlank() ->
                        Text("Release notes couldn't be loaded. Check the release on GitHub for details.",
                            fontSize = 14.sp, lineHeight = 20.sp, color = c.onSurfaceVariant)
                    else -> ReleaseNotes(app.whatsNewNotes!!)
                }
            }

            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(c.primary).clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Got it", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
            }
        }
    }
}

/** Minimal renderer for the Markdown subset our release descriptions use. */
@Composable
private fun ReleaseNotes(body: String) {
    val c = LocalScpScheme.current
    body.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> Spacer(Modifier.height(8.dp))
            line.startsWith("#") -> Text(
                line.trimStart('#', ' '), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onSurface,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            line.startsWith("**") && line.endsWith("**") && line.length > 4 -> Text(
                line.removeSurrounding("**"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            line.startsWith("- ") || line.startsWith("* ") -> Row(Modifier.padding(top = 6.dp)) {
                Text("•", fontSize = 15.sp, color = c.primary, modifier = Modifier.width(18.dp))
                Text(stripInline(line.drop(2)), fontSize = 15.sp, lineHeight = 21.sp, color = c.onSurface)
            }
            else -> Text(stripInline(line), fontSize = 15.sp, lineHeight = 21.sp, color = c.onSurface, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Drop the inline emphasis markers we don't style (bold/italic/backticks) so text reads cleanly. */
private fun stripInline(s: String): String =
    s.replace("**", "").replace("`", "").replace(Regex("(?<!\\w)[*_](?=\\w)|(?<=\\w)[*_](?!\\w)"), "")
