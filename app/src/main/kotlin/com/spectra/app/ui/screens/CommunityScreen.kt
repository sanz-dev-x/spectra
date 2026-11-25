/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri as AndroidUri
import android.webkit.ValueCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.spectra.app.BuildConfig
import com.spectra.app.LocalPlayerAwareWindowInsets

private const val CommunityUrl = "https://sxcore.my.id/community/"
private const val Tag = "CommunityWeb"

// Log diagnostik hanya di build debug, tanpa query string, fragment, atau isi yang mirip token.
private fun safeUrl(url: android.net.Uri?): String = url?.let { "${it.host.orEmpty()}${it.path.orEmpty()}" } ?: "?"

private fun redact(text: String?): String =
    text.orEmpty().take(300).replace(Regex("[A-Za-z0-9_\\-]{24,}(\\.[A-Za-z0-9_\\-]{10,})*"), "<redacted>")

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
                    Lifecycle.Event.ON_PAUSE -> {
                        runCatching { CookieManager.getInstance().flush() }
                        runCatching { view?.onPause() }
                    }
                    Lifecycle.Event.ON_RESUME -> runCatching { view?.onResume() }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pemilih file untuk lampiran (foto, video, dokumen) di komunitas.
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<AndroidUri>>?>(null) }
    val fileChooser =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            filePathCallback = null
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
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        setBackgroundColor(AndroidColor.WHITE)
                        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            settings.forceDark = WebSettings.FORCE_DARK_OFF
                        }
                        // Token ini dikenali website komunitas: mode aplikasi (kaca tanpa blur, sesi login awet).
                        settings.userAgentString = settings.userAgentString + " SpectraApp/" + BuildConfig.BASE_VERSION_NAME.filter { it.isLetterOrDigit() || it == '.' }
                        settings.javaScriptEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(true)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val scheme = request.url.scheme?.lowercase()
                                    if (scheme == "http" || scheme == "https") return false
                                    return try {
                                        val intent =
                                            if (scheme == "intent") Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME)
                                            else Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                        }
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    // Simpan sesi login ke disk setelah redirect agar tidak hilang bila proses mati.
                                    runCatching { CookieManager.getInstance().flush() }
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: android.webkit.WebResourceError,
                                ) {
                                    if (BuildConfig.DEBUG) {
                                        Log.w(Tag, "error ${error.errorCode} ${error.description} main=${request.isForMainFrame} ${safeUrl(request.url)}")
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: android.webkit.WebResourceResponse,
                                ) {
                                    if (BuildConfig.DEBUG) {
                                        Log.w(Tag, "http ${errorResponse.statusCode} main=${request.isForMainFrame} ${safeUrl(request.url)}")
                                    }
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
                                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                                    if (BuildConfig.DEBUG && message.messageLevel() != android.webkit.ConsoleMessage.MessageLevel.LOG) {
                                        Log.w(Tag, "js ${message.messageLevel()} ${redact(message.message())} @${message.sourceId().substringBefore('?').takeLast(60)}:${message.lineNumber()}")
                                    }
                                    return false
                                }

                                override fun onShowFileChooser(
                                    webView: WebView,
                                    callback: ValueCallback<Array<AndroidUri>>,
                                    params: FileChooserParams,
                                ): Boolean {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = callback
                                    return try {
                                        fileChooser.launch(params.createIntent())
                                        true
                                    } catch (_: Exception) {
                                        filePathCallback = null
                                        callback.onReceiveValue(null)
                                        false
                                    }
                                }

                                override fun onProgressChanged(
                                    view: WebView,
                                    newProgress: Int,
                                ) {
                                    progress = newProgress
                                }

                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message,
                                ): Boolean {
                                    val popup = WebView(view.context)
                                    popup.settings.javaScriptEnabled = true
                                    popup.settings.domStorageEnabled = true
                                    popup.webViewClient =
                                        object : WebViewClient() {
                                            private var forwarded = false

                                            private fun forward(url: String?) {
                                                if (forwarded || url.isNullOrBlank()) return
                                                val uri = Uri.parse(url)
                                                if (uri.scheme != "http" && uri.scheme != "https") return
                                                forwarded = true
                                                view.post {
                                                    view.loadUrl(url)
                                                    popup.stopLoading()
                                                    popup.destroy()
                                                }
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                popupView: WebView,
                                                request: WebResourceRequest,
                                            ): Boolean {
                                                forward(request.url.toString())
                                                return true
                                            }

                                            override fun onPageStarted(
                                                popupView: WebView,
                                                url: String?,
                                                favicon: android.graphics.Bitmap?,
                                            ) {
                                                forward(url)
                                            }
                                        }
                                    val transport = resultMsg.obj as WebView.WebViewTransport
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
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
