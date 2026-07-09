package com.foundation.scpreader.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundation.scpreader.R
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.classColors

/**
 * Maps an object-class name to its SCP gear-badge drawable (black disc + white symbol). Matching is
 * case-insensitive and tolerant of whitespace/hyphens/suffixes (e.g. "esoteric-class", "Keter-class"),
 * so classes parsed from tags, the ACS bar or article text all resolve. Any class we don't have art
 * for — including "Unknown" and the long tail of one-off esoteric classes — falls back to the "?" badge.
 */
@DrawableRes
fun classBadgeRes(objectClass: String): Int {
    val key = objectClass.trim().lowercase().substringBefore(' ').removeSuffix("-class").trim('-', ' ')
    return when (key) {
        "safe" -> R.drawable.class_safe
        "euclid" -> R.drawable.class_euclid
        "keter" -> R.drawable.class_keter
        "thaumiel" -> R.drawable.class_thaumiel
        "archon" -> R.drawable.class_archon
        "neutralized", "decommissioned-neutralized" -> R.drawable.class_neutralized
        "apollyon" -> R.drawable.class_apollyon
        "explained" -> R.drawable.class_explained
        "pending" -> R.drawable.class_pending
        "cernunnos" -> R.drawable.class_cernunnos
        "hiemal" -> R.drawable.class_hiemal
        "tiamat" -> R.drawable.class_tiamat
        "ticonderoga" -> R.drawable.class_ticonderoga
        "uncontained" -> R.drawable.class_uncontained
        "decommissioned" -> R.drawable.class_decommissioned
        "anomalous" -> R.drawable.class_anomalous
        "maksur" -> R.drawable.class_maksur
        "zeno" -> R.drawable.class_zeno
        else -> R.drawable.class_unknown
    }
}

/** The class gear-badge as an icon (rendered untinted so its black/white art shows true). */
@Composable
fun ClassBadgeIcon(objectClass: String, size: Int = 22) {
    Image(painterResource(classBadgeRes(objectClass)), contentDescription = null, modifier = Modifier.size(size.dp))
}

/** Object-class badge pill (e.g. "Euclid", "Keter"). */
@Composable
fun ClassBadge(objectClass: String, isDark: Boolean, fontSize: Int = 11) {
    val (bg, txt) = classColors(objectClass, isDark)
    Text(
        objectClass.uppercase(),
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = txt,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Selectable pill chip used for type / tag / library filters. */
@Composable
fun FilterChip(
    label: String,
    active: Boolean,
    bg: Color,
    fg: Color,
    border: Color,
    showCheck: Boolean = true,
    height: Int = 36,
    corner: Int = 10,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(height.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(bg)
            .then(if (border == Color.Transparent) Modifier else Modifier.border(1.dp, border, RoundedCornerShape(corner.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (active && showCheck) Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = fg)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/** Material-style pill toggle switch matching the mockup dimensions. */
@Composable
fun PillSwitch(on: Boolean) {
    val c = LocalScpScheme.current
    val trackBg by animateColorAsState(if (on) c.primary else c.surfaceCHighest, label = "track")
    val trackBorder = if (on) c.primary else c.outline
    val thumbBg = if (on) c.onPrimary else c.outline
    val thumbSize by animateDpAsState(if (on) 22.dp else 14.dp, label = "thumbSize")
    val thumbLeft by animateDpAsState(if (on) 26.dp else 5.dp, label = "thumbLeft")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackBg)
            .border(2.dp, trackBorder, RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbLeft)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbBg),
        )
    }
}

val Mono = FontFamily.Monospace

@Composable
fun Dot(color: Color, size: Int = 9) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

@Composable
fun Divider1() {
    val c = LocalScpScheme.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
}
