package com.foundation.scpreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.fillMaxHeight
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
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        handleOpenPlayer(intent)
        handleOpenFriends(intent)
        // Android 13+ hides the narration player's notification unless this is granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    // A wiki link tapped elsewhere re-delivers here (singleTask); open it in the reader.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleOpenPlayer(intent)
        handleOpenFriends(intent)
    }

    // Recommendation-notification tap: jump straight to the Friends tab.
    private fun handleOpenFriends(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_FRIENDS) app.go(Screen.Friends)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.toString()?.let { app.openFromWikiUrl(it) }
    }

    // Notification / now-playing-island tap: surface the full-screen player (no-op if nothing is loaded).
    private fun handleOpenPlayer(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) app.openPlayer()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.foundation.scpreader.OPEN_PLAYER"
        const val ACTION_OPEN_FRIENDS = "com.foundation.scpreader.OPEN_FRIENDS"
    }
}

private data class NavDef(val screen: Screen, val iconActive: ImageVector, val iconInactive: ImageVector, val label: String)

// Settings is intentionally absent — it lives as an action in the Home top bar, not the bottom nav.
private val navDefs = listOf(
    NavDef(Screen.Home, AppIcons.Home, AppIcons.HomeOutlined, "Home"),
    NavDef(Screen.Search, AppIcons.Search, AppIcons.SearchOutlined, "Search"),
    NavDef(Screen.Library, AppIcons.LibraryBooks, AppIcons.LibraryBooksOutlined, "Library"),
    NavDef(Screen.Downloads, AppIcons.DownloadForOffline, AppIcons.DownloadForOfflineOutlined, "Downloads"),
    NavDef(Screen.Friends, AppIcons.Group, AppIcons.GroupOutlined, "Friends"),
)

@Composable
private fun AppRoot(app: AppState) {
    val c = LocalScpScheme.current

    // Hardware back navigates within the app instead of exiting from a sub-screen.
    BackHandler(enabled = app.playerFullScreen || app.randomOpen || app.readerItem != null || app.screen != Screen.Home) {
        when {
            app.playerFullScreen -> app.closePlayer()
            app.randomOpen -> app.randomOpen = false
            app.readerItem != null -> app.closeReader()
            else -> app.go(Screen.Home)
        }
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            // real status-bar inset instead of the mockup's fake status bar
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars))
            val available = app.updateStatus as? com.foundation.scpreader.update.UpdateCheckResult.Available
            if (available != null && !app.updateBannerDismissed) UpdateBanner(app, available.release.tagName)
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
                        Screen.Friends -> com.foundation.scpreader.ui.screens.FriendsScreen(app)
                        Screen.Settings -> SettingsScreen(app)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            MiniPlayer(app)
            // Offline mode collapses to Home ↔ Library ↔ Settings; the bottom nav (Search/Downloads)
            // isn't useful with no connection, so hide it and navigate via on-screen buttons.
            if (!app.offlineMode) BottomNav(app)
        }

        app.readerItem?.let { item -> ReaderScreen(app, item) }
        if (app.randomOpen) RandomSheet(app)
        if (app.recommendSheetOpen) com.foundation.scpreader.ui.screens.RecommendSheet(app)
        if (app.playerFullScreen) com.foundation.scpreader.ui.screens.PlayerScreen(app)

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

/** Dismissible nudge shown app-wide once a newer GitHub release is found; full flow lives in Settings. */
@Composable
private fun UpdateBanner(app: AppState, tag: String) {
    val c = LocalScpScheme.current
    Row(
        Modifier.fillMaxWidth().background(c.secondaryContainer).clickable { app.scrollSettingsToUpdates = true; app.go(Screen.Settings) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.SystemUpdate, null, Modifier.size(20.dp), tint = c.onSecondaryContainer)
        Text("$tag is available — tap to update", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.onSecondaryContainer, modifier = Modifier.weight(1f))
        Icon(
            AppIcons.Close, "Dismiss", Modifier.size(18.dp)
                .clickable { app.updateBannerDismissed = true },
            tint = c.onSecondaryContainer,
        )
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
    // -1 when the current screen isn't a bottom-nav destination (e.g. Settings) → no tab highlighted.
    val rawIndex = navDefs.indexOfFirst { it.screen == app.screen }
    val pillVisible = rawIndex >= 0
    val selectedIndex = rawIndex.coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().background(c.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
        // Material 3 NavigationBar dimensions: 80dp container height, a 64×32dp active indicator,
        // 24dp icons, and each item filling the full bar height so its tappable area clears the
        // 48×48dp minimum touch target.
        BoxWithConstraints(Modifier.fillMaxWidth().height(80.dp)) {
            val slotWidth = maxWidth / navDefs.size
            val pillWidth = 64.dp
            val pillOffset by animateDpAsState(
                targetValue = slotWidth * selectedIndex + (slotWidth - pillWidth) / 2,
                animationSpec = tween(320),
                label = "pill",
            )
            // Sliding pill highlight behind the active tab; hidden when no nav tab is selected.
            if (pillVisible) {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .offset(x = pillOffset)
                        .size(width = pillWidth, height = 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.secondaryContainer),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                navDefs.forEachIndexed { i, def ->
                    val active = pillVisible && i == selectedIndex
                    val iconColor by animateColorAsState(if (active) c.onSecondaryContainer else c.onSurfaceVariant, label = "icon")
                    val labelColor by animateColorAsState(if (active) c.onSurface else c.onSurfaceVariant, label = "label")
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { app.go(def.screen) }
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                            // Filled icon for the active tab, outlined for the rest.
                            Icon(if (active) def.iconActive else def.iconInactive, null, Modifier.size(24.dp), tint = iconColor)
                        }
                        // Selected tab's label is bold; the others sit at medium weight.
                        Text(def.label, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = labelColor)
                    }
                }
            }
        }
        // real navigation-bar inset instead of the fake home indicator
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}
