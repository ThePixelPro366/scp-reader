package com.foundation.scpreader.ui.components

import androidx.compose.ui.graphics.Color
import com.foundation.scpreader.ui.theme.ScpScheme

data class ChipStyle(val bg: Color, val fg: Color, val border: Color)

/** Standard type/tag chip appearance (secondaryContainer when active, outlined when not). */
fun ScpScheme.chipStyle(active: Boolean): ChipStyle =
    if (active) ChipStyle(secondaryContainer, onSecondaryContainer, Color.Transparent)
    else ChipStyle(Color.Transparent, onSurfaceVariant, outlineVariant)
