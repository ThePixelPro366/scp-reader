package com.foundation.scpreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Full color scheme used across the app. This mirrors the Material 3 token set from the
 * design mockup one-to-one (surface containers, primary/secondary/tertiary containers, etc.)
 * rather than relying on the stock Material3 ColorScheme, so the visuals match pixel-for-pixel.
 */
@Immutable
data class ScpScheme(
    // seed-derived
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // neutrals
    val surface: Color,
    val surfaceDim: Color,
    val surfaceCLowest: Color,
    val surfaceCLow: Color,
    val surfaceContainer: Color,
    val surfaceCHigh: Color,
    val surfaceCHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
)

private fun hex(s: String): Color {
    val v = s.removePrefix("#")
    return when (v.length) {
        6 -> Color(0xFF000000 or v.toLong(16))
        else -> Color.Magenta
    }
}

private data class Neutral(
    val surface: String, val surfaceDim: String, val surfaceCLowest: String, val surfaceCLow: String,
    val surfaceContainer: String, val surfaceCHigh: String, val surfaceCHighest: String,
    val onSurface: String, val onSurfaceVariant: String, val outline: String, val outlineVariant: String,
    val scrim: Color, val inverseSurface: String, val inverseOnSurface: String,
)

private val neutralLight = Neutral(
    surface = "#fdf8fd", surfaceDim = "#ddd8dd", surfaceCLowest = "#ffffff", surfaceCLow = "#f7f2f7",
    surfaceContainer = "#f2ecf1", surfaceCHigh = "#ece6eb", surfaceCHighest = "#e6e0e5",
    onSurface = "#1c1b1d", onSurfaceVariant = "#49454e", outline = "#7a757f", outlineVariant = "#cbc4ce",
    scrim = Color(0x66000000), inverseSurface = "#313033", inverseOnSurface = "#f4eff3",
)
private val neutralDark = Neutral(
    surface = "#141316", surfaceDim = "#141316", surfaceCLowest = "#0e0d10", surfaceCLow = "#1c1b1e",
    surfaceContainer = "#201f22", surfaceCHigh = "#2b292d", surfaceCHighest = "#363438",
    onSurface = "#e6e1e5", onSurfaceVariant = "#cbc4ce", outline = "#948f99", outlineVariant = "#49454e",
    scrim = Color(0x8C000000), inverseSurface = "#e6e1e5", inverseOnSurface = "#313033",
)

private data class Seed(
    val primary: String, val onPrimary: String, val primaryContainer: String, val onPrimaryContainer: String,
    val secondaryContainer: String, val onSecondaryContainer: String,
    val tertiaryContainer: String, val onTertiaryContainer: String,
)

enum class SeedKey(val label: String, val swatch: Color) {
    Violet("Violet", Color(0xFF6750A4)),
    Green("Green", Color(0xFF386A20)),
    Coral("Coral", Color(0xFF9A4523)),
    Blue("Blue", Color(0xFF34618E)),
}

private val seedLight = mapOf(
    SeedKey.Violet to Seed("#6750a4", "#ffffff", "#eaddff", "#21005d", "#e8def8", "#1d192b", "#ffd8e4", "#31111d"),
    SeedKey.Green to Seed("#386a20", "#ffffff", "#b7f397", "#042100", "#d9e7cb", "#131f0d", "#bcecec", "#002020"),
    SeedKey.Coral to Seed("#9a4523", "#ffffff", "#ffdbcc", "#380d00", "#fcdbc9", "#2c160a", "#eee0a8", "#211b00"),
    SeedKey.Blue to Seed("#34618e", "#ffffff", "#cfe5ff", "#001d36", "#d7e3f8", "#101c2b", "#f9d8ff", "#2c1236"),
)
private val seedDark = mapOf(
    SeedKey.Violet to Seed("#d0bcff", "#381e72", "#4f378b", "#eaddff", "#4a4458", "#e8def8", "#633b48", "#ffd8e4"),
    SeedKey.Green to Seed("#9cd67d", "#0c3900", "#205107", "#b7f397", "#3a4a31", "#d9e7cb", "#204f4f", "#bcecec"),
    SeedKey.Coral to Seed("#ffb59b", "#591d00", "#7b2e0e", "#ffdbcc", "#5d4034", "#fcdbc9", "#524600", "#eee0a8"),
    SeedKey.Blue to Seed("#9fcaff", "#003257", "#164974", "#cfe5ff", "#3b4858", "#d7e3f8", "#573f5e", "#f9d8ff"),
)

fun buildScheme(seedKey: SeedKey, dynamicColor: Boolean, isDark: Boolean): ScpScheme {
    val n = if (isDark) neutralDark else neutralLight
    val effectiveSeed = if (dynamicColor) seedKey else SeedKey.Violet
    val s = (if (isDark) seedDark else seedLight).getValue(effectiveSeed)
    return ScpScheme(
        primary = hex(s.primary), onPrimary = hex(s.onPrimary),
        primaryContainer = hex(s.primaryContainer), onPrimaryContainer = hex(s.onPrimaryContainer),
        secondaryContainer = hex(s.secondaryContainer), onSecondaryContainer = hex(s.onSecondaryContainer),
        tertiaryContainer = hex(s.tertiaryContainer), onTertiaryContainer = hex(s.onTertiaryContainer),
        surface = hex(n.surface), surfaceDim = hex(n.surfaceDim), surfaceCLowest = hex(n.surfaceCLowest),
        surfaceCLow = hex(n.surfaceCLow), surfaceContainer = hex(n.surfaceContainer), surfaceCHigh = hex(n.surfaceCHigh),
        surfaceCHighest = hex(n.surfaceCHighest), onSurface = hex(n.onSurface), onSurfaceVariant = hex(n.onSurfaceVariant),
        outline = hex(n.outline), outlineVariant = hex(n.outlineVariant), scrim = n.scrim,
        inverseSurface = hex(n.inverseSurface), inverseOnSurface = hex(n.inverseOnSurface),
    )
}

/** Build our token set from a Material3 [ColorScheme] — used for Android 12+ wallpaper (Material You) colors. */
fun schemeFromMaterial(cs: ColorScheme, isDark: Boolean): ScpScheme = ScpScheme(
    primary = cs.primary, onPrimary = cs.onPrimary,
    primaryContainer = cs.primaryContainer, onPrimaryContainer = cs.onPrimaryContainer,
    secondaryContainer = cs.secondaryContainer, onSecondaryContainer = cs.onSecondaryContainer,
    tertiaryContainer = cs.tertiaryContainer, onTertiaryContainer = cs.onTertiaryContainer,
    surface = cs.surface, surfaceDim = cs.surfaceDim, surfaceCLowest = cs.surfaceContainerLowest,
    surfaceCLow = cs.surfaceContainerLow, surfaceContainer = cs.surfaceContainer, surfaceCHigh = cs.surfaceContainerHigh,
    surfaceCHighest = cs.surfaceContainerHighest, onSurface = cs.onSurface, onSurfaceVariant = cs.onSurfaceVariant,
    outline = cs.outline, outlineVariant = cs.outlineVariant,
    scrim = Color(if (isDark) 0x8C000000 else 0x66000000),
    inverseSurface = cs.inverseSurface, inverseOnSurface = cs.inverseOnSurface,
)

/** Build a Material3 [ColorScheme] from our tokens so stock M3 components (Slider, progress, etc.) are themed. */
fun materialColorScheme(s: ScpScheme, isDark: Boolean): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = s.primary, onPrimary = s.onPrimary,
        primaryContainer = s.primaryContainer, onPrimaryContainer = s.onPrimaryContainer,
        secondary = s.primary, onSecondary = s.onPrimary,
        secondaryContainer = s.secondaryContainer, onSecondaryContainer = s.onSecondaryContainer,
        tertiaryContainer = s.tertiaryContainer, onTertiaryContainer = s.onTertiaryContainer,
        background = s.surface, onBackground = s.onSurface,
        surface = s.surface, onSurface = s.onSurface,
        surfaceVariant = s.surfaceCHigh, onSurfaceVariant = s.onSurfaceVariant,
        surfaceContainerLowest = s.surfaceCLowest, surfaceContainerLow = s.surfaceCLow,
        surfaceContainer = s.surfaceContainer, surfaceContainerHigh = s.surfaceCHigh, surfaceContainerHighest = s.surfaceCHighest,
        outline = s.outline, outlineVariant = s.outlineVariant, scrim = s.scrim,
        inverseSurface = s.inverseSurface, inverseOnSurface = s.inverseOnSurface,
    )
}

/** Object-class chip colors: [background, text]. Mirrors classColors() in the mockup. */
fun classColors(objectClass: String, isDark: Boolean): Pair<Color, Color> {
    val light = mapOf(
        "Safe" to ("#c6ead0" to "#0b3d1e"), "Euclid" to ("#fbe2b0" to "#4a3200"),
        "Keter" to ("#fbd1cc" to "#5a140c"), "Thaumiel" to ("#cfe0ff" to "#0a2a5a"),
        "Neutralized" to ("#e0dde0" to "#3a383b"), "Unknown" to ("#e0dde0" to "#3a383b"),
    )
    val dark = mapOf(
        "Safe" to ("#1e4a2e" to "#b7f0c6"), "Euclid" to ("#574400" to "#fbe2b0"),
        "Keter" to ("#5a1f17" to "#fbd1cc"), "Thaumiel" to ("#22406e" to "#cfe0ff"),
        "Neutralized" to ("#3a383b" to "#e0dde0"), "Unknown" to ("#3a383b" to "#e0dde0"),
    )
    val m = (if (isDark) dark else light)
    val (bg, txt) = m[objectClass] ?: m.getValue("Unknown")
    return hex(bg) to hex(txt)
}
