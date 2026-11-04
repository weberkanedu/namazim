package com.namazim

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import org.json.JSONObject

private const val HOST = "gul.local"

private fun petalsJson(petals: List<Petal>) = JSONArray().apply {
    petals.forEach { put(JSONObject().put("name", it.name).put("progress", it.progress.toDouble()).put("label", it.label).put("counts", it.counts)) }
}.toString()

/**
 * The Özet rose: the three.js page in assets/gul, served from a fake https host so its ES modules load
 * (WebView blocks module scripts from file://). Nothing goes to the network.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun GulCard(petals: List<Petal>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val json = petalsJson(petals)
    val latest = remember { arrayOf(json) }
    latest[0] = json
    // bumped when Android kills the WebView renderer: a fresh WebView replaces the dead one
    var gen by remember { mutableIntStateOf(0) }
    val web = remember(gen) {
        WebView(ctx).apply {
            setBackgroundColor(0xFF121D33.toInt())
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // gestures are handled by the Compose overlay, so vertical swipes still scroll the page
            setOnTouchListener { _, _ -> true }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse {
                    val url = req.url
                    if (url.host != HOST) return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
                    val path = url.path.orEmpty().trimStart('/')
                    val mime = when (path.substringAfterLast('.')) {
                        "html" -> "text/html"; "js" -> "text/javascript"; else -> "application/octet-stream"
                    }
                    return try {
                        WebResourceResponse(mime, "utf-8", context.assets.open("gul/$path"))
                    } catch (e: java.io.IOException) {
                        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
                    }
                }
                // a fresh page starts as a bud: give it today's values right away
                override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript("gul.set(${latest[0]})", null) }
                // without this, a killed renderer (low memory, heavy WebGL) takes the whole app down with it
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { gen++; return true }
            }
            loadUrl("https://$HOST/gul.html")
        }
    }
    LaunchedEffect(json, web) { web.evaluateJavascript("gul.set($json)", null) }

    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner, web) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) web.onPause()
            if (e == Lifecycle.Event.ON_RESUME) web.onResume()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); web.destroy() }
    }

    Box(modifier.fillMaxWidth().height(300.dp)) {
        key(web) { AndroidView(factory = { web }, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().pointerInput(web) {
            // the page works in CSS px
            detectHorizontalDragGestures { _, dx -> web.evaluateJavascript("gul.spin(${dx / density})", null) }
        })
    }
}
