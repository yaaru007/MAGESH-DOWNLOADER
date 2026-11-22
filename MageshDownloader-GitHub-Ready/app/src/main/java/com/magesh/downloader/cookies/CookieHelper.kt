package com.magesh.downloader.cookies

import android.content.Context
import android.webkit.CookieManager
import java.io.File

object CookieHelper {

    private const val COOKIES_FILE_NAME = "cookies.txt"

    private val TARGET_URLS = listOf(
        "https://www.youtube.com",
        "https://m.youtube.com",
        "https://accounts.google.com",
        "https://www.instagram.com",
        "https://i.instagram.com",
        "https://www.facebook.com",
        "https://m.facebook.com"
    )

    fun getCookiesFile(context: Context): File {
        return File(context.filesDir, COOKIES_FILE_NAME)
    }

    fun getCookiesFileIfValid(context: Context): File? {
        val file = getCookiesFile(context)
        return if (file.exists() && file.length() > 30L) file else null
    }

    fun exportCookiesFromWebView(context: Context): Boolean {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val lines = mutableSetOf<String>()
        val expiry = (System.currentTimeMillis() / 1000L) + 31_536_000L // +1 year

        for (url in TARGET_URLS) {
            val cookieStr = cookieManager.getCookie(url) ?: continue
            val host = android.net.Uri.parse(url).host ?: continue
            val domain = when {
                host.endsWith("instagram.com") -> ".instagram.com"
                host.endsWith("facebook.com") -> ".facebook.com"
                host.endsWith("youtube.com") -> ".youtube.com"
                host.endsWith("google.com") -> ".google.com"
                else -> ".$host"
            }
            val pairs = cookieStr.split(";")
            for (pair in pairs) {
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isEmpty()) continue
                // Netscape cookie format:
                // domain \t include_subdomains \t path \t secure \t expiration \t name \t value
                lines.add("$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
            }
        }

        if (lines.isEmpty()) return false

        val outFile = getCookiesFile(context)
        outFile.writeText(
            buildString {
                appendLine("# Netscape HTTP Cookie File")
                lines.forEach { appendLine(it) }
            }
        )
        return true
    }

    fun clearCookies(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        val file = getCookiesFile(context)
        if (file.exists()) {
            file.delete()
        }
    }

    fun getCookieHeaderForUrl(url: String): String? {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
    }
}
