package com.foundation.scpreader

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.MiniPlayer
import com.foundation.scpreader.ui.screens.DownloadsScreen
import com.foundation.scpreader.ui.screens.HomeScreen
import com.foundation.scpreader.ui.screens.LibraryScreen
import com.foundation.scpreader.ui.screens.RandomSheet
import com.foundation.scpreader.ui.screens.ReaderScreen
import com.foundation.scpreader.ui.screens.SearchScreen
import com.foundation.scpreader.ui.screens.SettingsScreen
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.components.ScpSpinner
import com.foundation.scpreader.ui.theme.LocalScpScheme
import com.foundation.scpreader.ui.theme.ProvideScpScheme
import com.foundation.scpreader.ui.theme.buildScheme
import com.foundation.scpreader.ui.theme.materialColorScheme
import com.foundation.scpreader.ui.theme.schemeFromMaterial
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val app: AppState by viewModels { AppState.factory((application as ScpApp).container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) { app.systemDark = systemDark }
            val ctx = LocalContext.current
            val useWallpaper = app.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val material = if (useWallpaper) {
                if (app.isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                materialColorScheme(buildScheme(app.seed, dynamicColor = true, isDark = app.isDark), app.isDark)
            }
            val scpScheme = if (useWallpaper) schemeFromMaterial(material, app.isDark)
            else buildScheme(app.seed, dynamicColor = true, isDark = app.isDark)
            MaterialTheme(colorScheme = material) {
                ProvideScpScheme(scpScheme) { AppRoot(app) }
            }
        }
    }
}

private data class NavDef(val screen: Screen, val icon: ImageVector, val label: String)

private val navDefs = listOf(
    NavDef(Screen.Home, AppIcons.Home, "Home"),
    NavDef(Screen.Search, AppIcons.Search, "Search"),
    NavDef(Screen.Library, AppIcons.LibraryBooks, "Library"),
    NavDef(Screen.Downloads, AppIcons.Downloading, "Downloads"),
    NavDef(Screen.Settings, AppIcons.Settings, "Settings"),
)

@Composable
private fun AppRoot(app: AppState) {
    val c = LocalScpScheme.current

    // Hardware back navigates within the app instead of exiting from a sub-screen.
    BackHandler(enabled = app.randomOpen || app.readerItem != null || app.screen != Screen.Home) {
        when {
            app.randomOpen -> app.randomOpen = false
            app.readerItem != null -> app.closeReader()
            else -> app.go(Screen.Home)
        }
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            // real status-bar inset instead of the mockup's fake status bar
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = app.screen,
                    transitionSpec = {
                        (fadeIn(tween(220)) togetherWith fadeOut(tween(160)))
                    },
                    label = "screen",
                ) { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(app)
                        Screen.Search -> SearchScreen(app)
                        Screen.Library -> LibraryScreen(app)
                        Screen.Downloads -> DownloadsScreen(app)
                        Screen.Settings -> SettingsScreen(app)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            MiniPlayer(app)
            BottomNav(app)
        }

        app.readerItem?.let { item -> ReaderScreen(app, item) }
        if (app.randomOpen) RandomSheet(app)

        // Cold-start access screen: stays until the app is actually ready (not a fixed timer),
        // with a safety fallback so it never hangs.
        var showSplash by remember { mutableStateOf(true) }
        val ready = app.feed.isNotEmpty() || app.feedError != null
        LaunchedEffect(ready) { if (ready) showSplash = false }
        LaunchedEffect(Unit) { delay(3000); showSplash = false }
        AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(350))) {
            SplashOverlay()
        }
    }
}

@Composable
private fun SplashOverlay() {
    // Blank white → just the access line, like the original.
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Text(
            "LEVEL 5 ACCESS GRANTED",
            fontFamily = Mono,
            color = Color(0xFF141316),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun BottomNav(app: AppState) {
    val c = LocalScpScheme.current
    val selectedIndex = navDefs.indexOfFirst { it.screen == app.screen }.coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().background(c.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
        BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
            val slotWidth = maxWidth / navDefs.size
            val pillWidth = 56.dp
            val pillOffset by animateDpAsState(
                targetValue = slotWidth * selectedIndex + (slotWidth - pillWidth) / 2,
                animationSpec = tween(320),
                label = "pill",
            )
            // sliding pill highlight
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .offset(x = pillOffset)
                    .size(width = pillWidth, height = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.secondaryContainer),
            )
            Row(Modifier.fillMaxWidth()) {
                navDefs.forEachIndexed { i, def ->
                    val active = i == selectedIndex
                    val iconColor by animateColorAsState(if (active) c.onSecondaryContainer else c.onSurfaceVariant, label = "icon")
                    val labelColor by animateColorAsState(if (active) c.onSurface else c.onSurfaceVariant, label = "label")
                    Column(
                        Modifier.weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { app.go(def.screen) }
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                            Icon(def.icon, null, Modifier.size(24.dp), tint = iconColor)
                        }
                        Text(def.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = labelColor)
                    }
                }
            }
        }
        // real navigation-bar inset instead of the fake home indicator
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}
