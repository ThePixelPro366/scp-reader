package com.foundation.scpreader.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.foundation.scpreader.AppState
import com.foundation.scpreader.data.ScpItem
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.ScpSpinner
import com.foundation.scpreader.ui.theme.LocalScpScheme

/**
 * Full-screen WebView showing the article's real, fully-styled wiki page — the faithful way to view
 * a custom theme the native block renderer can't reproduce. Opened from the reader's "View original
 * theme" banner (only for pages the scraper flags as themed). Online-only by nature.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThemeWebView(app: AppState, item: ScpItem) {
    val c = LocalScpScheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val url = item.url.replaceFirst("http://", "https://")
    // Only the FIRST load shows the spinner — later in-page navigations must not throw a full-screen
    // overlay over the page mid-scroll (that read as the scroll "randomly stopping").
    var initialLoad by remember { mutableStateOf(true) }
    val surfaceArgb = c.surface.toArgb()

    // Retained across recompositions so the page isn't reloaded; destroyed on close.
    val webView = remember {
        WebView(ctx).apply {
            // Match the app surface so there's no white/black flash before the page paints — the
            // stray dark strip at the bottom came from a reserved inset the WebView now fills itself.
            setBackgroundColor(surfaceArgb)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, u: String?) { initialLoad = false }
                // Keep wiki pages inside this view; hand anything else (external links) to a browser.
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString() ?: return false
                    val host = request.url?.host.orEmpty()
                    return if (host.contains("wikidot.com") || host.contains("scp-wiki")) {
                        false // let the WebView load it, preserving the theme
                    } else {
                        openUrl(ctx, target); true
                    }
                }
            }
            loadUrl(url)
        }
    }

    // Destroy the WebView when the screen leaves the composition, freeing its render thread.
    DisposableEffect(Unit) { onDispose { webView.destroy() } }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else app.closeArticleTheme()
    }

    Column(Modifier.fillMaxSize().background(c.surface)) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { app.closeArticleTheme() }, contentAlignment = Alignment.Center) {
                Icon(AppIcons.ArrowBack, "Back", Modifier.size(24.dp), tint = c.onSurface)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(item.number, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                Text("Original theme", fontSize = 11.sp, color = c.onSurfaceVariant)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { openUrl(ctx, url) }, contentAlignment = Alignment.Center) {
                Icon(AppIcons.NorthEast, "Open in browser", Modifier.size(22.dp), tint = c.onSurface)
            }
        }
        // The WebView fills the rest and scrolls under the gesture/navigation bar (like a browser),
        // so there's no separate dark inset strip below it.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (initialLoad) {
                Box(Modifier.fillMaxSize().background(c.surface), contentAlignment = Alignment.Center) {
                    ScpSpinner(size = 84)
                }
            }
        }
    }
}
