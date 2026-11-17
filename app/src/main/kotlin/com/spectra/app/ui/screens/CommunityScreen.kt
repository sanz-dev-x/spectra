/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spectra.app.LocalPlayerAwareWindowInsets

private const val CommunityUrl = "https://sxcore.my.id/community/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CommunityScreen() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    // Naik setiap kali proses render WebView mati (mis. memori habis), supaya WebView dibuat ulang
    // dan aplikasi tidak ikut tertutup.
    var generation by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val view = webView
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> runCatching { view?.onPause() }
                    Lifecycle.Event.ON_RESUME -> runCatching { view?.onResume() }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = canGoBack) { runCatching { webView?.goBack() } }

    Box(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
    ) {
        key(generation) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        // GPU Mali di sebagian HP membuat WebView hitam dan berkedip; render lewat CPU lebih stabil.
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        setBackgroundColor(AndroidColor.WHITE)
                        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            settings.forceDark = WebSettings.FORCE_DARK_OFF
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val scheme = request.url.scheme
                                    return !(scheme == "http" || scheme == "https")
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView,
                                    url: String?,
                                    isReload: Boolean,
                                ) {
                                    canGoBack = view.canGoBack()
                                }

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail,
                                ): Boolean {
                                    // Tanpa ini, matinya proses render ikut mematikan seluruh aplikasi.
                                    progress = 0
                                    canGoBack = false
                                    webView = null
                                    // Batasi pembuatan ulang agar tidak berulang tanpa henti (layar berkedip).
                                    if (generation < 2) generation++
                                    return true
                                }
                            }
                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onProgressChanged(
                                    view: WebView,
                                    newProgress: Int,
                                ) {
                                    progress = newProgress
                                }
                            }
                        loadUrl(CommunityUrl)
                        webView = this
                    }
                },
                onRelease = { view ->
                    if (webView === view) webView = null
                    runCatching {
                        view.webViewClient = WebViewClient()
                        view.webChromeClient = null
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.onPause()
                        view.removeAllViews()
                        view.destroy()
                    }
                },
            )
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
        }
    }
}
