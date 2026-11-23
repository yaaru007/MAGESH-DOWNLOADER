package com.magesh.downloader.cookies

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

data class WebViewExtractedMedia(
    val title: String,
    val imageUrls: List<String>,
    val videoUrls: List<String>
)

object WebViewMediaExtractor {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(context: Context, url: String): WebViewExtractedMedia? {
        return withTimeoutOrNull(16_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val webView = WebView(context.applicationContext)
                    val handler = Handler(Looper.getMainLooper())
                    var finished = false

                    fun cleanupAndResume(result: WebViewExtractedMedia?) {
                        if (finished) return
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (_: Exception) {
                        }
                        if (cont.isActive) {
                            cont.resume(result)
                        }
                    }

                    cont.invokeOnCancellation {
                        handler.post { cleanupAndResume(null) }
                    }

                    try {
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(webView, true)

                        val jsExtract = """
                            (function() {
                              var imgs = Array.from(document.querySelectorAll('img'))
                                .filter(function(i) {
                                  var s = i.currentSrc || i.src || '';
                                  return s.indexOf('http') === 0 &&
                                    s.indexOf('t51.2885-19') === -1 &&
                                    s.indexOf('s150x150') === -1 &&
                                    s.indexOf('p150x150') === -1 &&
                                    s.indexOf('rsrc.php') === -1 &&
                                    s.indexOf('static.cdninstagram.com') === -1 &&
                                    (i.naturalWidth === 0 || i.naturalWidth >= 220);
                                })
                                .map(function(i) { return i.currentSrc || i.src; });
                              var vids = Array.from(document.querySelectorAll('video'))
                                .map(function(v) { return v.currentSrc || v.src || ''; })
                                .filter(function(s) { return s.indexOf('http') === 0; });
                              if (vids.length === 0) {
                                var html = document.documentElement ? document.documentElement.innerHTML : '';
                                var m = html.match(/"video_url"\s*:\s*"([^"]+)"/);
                                if (m && m[1]) {
                                  vids.push(m[1].replace(/\\u0026/g, '&').replace(/\\\//g, '/'));
                                }
                              }
                              return JSON.stringify({
                                title: document.title || '',
                                images: Array.from(new Set(imgs)),
                                videos: Array.from(new Set(vids))
                              });
                            })();
                        """.trimIndent()

                        var scheduled = false
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                if (scheduled || finished) return
                                scheduled = true
                                handler.postDelayed({
                                    if (finished) return@postDelayed
                                    webView.evaluateJavascript(jsExtract) { rawJson ->
                                        val parsed = parseResult(rawJson)
                                        if (parsed != null &&
                                            (parsed.imageUrls.isNotEmpty() || parsed.videoUrls.isNotEmpty())
                                        ) {
                                            cleanupAndResume(parsed)
                                        } else {
                                            // Retry once after another 1.5s for slow React hydration
                                            handler.postDelayed({
                                                if (finished) return@postDelayed
                                                webView.evaluateJavascript(jsExtract) { retryJson ->
                                                    cleanupAndResume(parseResult(retryJson))
                                                }
                                            }, 1500L)
                                        }
                                    }
                                }, 1800L)
                            }
                        }

                        webView.loadUrl(url)
                    } catch (_: Exception) {
                        cleanupAndResume(null)
                    }
                }
            }
        }
    }

    private fun parseResult(rawJson: String?): WebViewExtractedMedia? {
        if (rawJson.isNullOrBlank() || rawJson == "null") return null
        return try {
            val unquoted = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                JSONObject("{\"v\":$rawJson}").getString("v")
            } else {
                rawJson
            }
            val obj = JSONObject(unquoted)
            val title = obj.optString("title", "").trim()
            val imagesArr = obj.optJSONArray("images")
            val videosArr = obj.optJSONArray("videos")

            val images = mutableListOf<String>()
            if (imagesArr != null) {
                for (i in 0 until imagesArr.length()) {
                    val u = imagesArr.optString(i)
                    if (u.startsWith("http")) images.add(u)
                }
            }

            val videos = mutableListOf<String>()
            if (videosArr != null) {
                for (i in 0 until videosArr.length()) {
                    val u = videosArr.optString(i)
                    if (u.startsWith("http")) videos.add(u)
                }
            }

            if (images.isEmpty() && videos.isEmpty()) null
            else WebViewExtractedMedia(title = title, imageUrls = images, videoUrls = videos)
        } catch (_: Exception) {
            null
        }
    }
}
