package com.fbdownloader.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Android replacement for gallery-dl's Facebook image mode (gallery-dl can't run on Android).
 *
 * Best effort: loads the post/photo page with the imported cookies and pulls the full-size photo
 * URLs out of the HTML (the "view full size" link of the mbasic page, the photo_image/viewer_image
 * entries of the web page, and og:image as a last resort). Facebook photos almost always need a
 * logged-in session, so import your cookies.txt first.
 */
class FbImages {

    data class ImageItem(val url: String, val uploader: String, val id: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0"

    private class Page(val finalUrl: String, val html: String)

    private fun get(url: String, cookieHeader: String?): Page {
        val b = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
        if (!cookieHeader.isNullOrEmpty()) b.header("Cookie", cookieHeader)
        client.newCall(b.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            return Page(resp.request.url.toString(), body)
        }
    }

    private fun unescape(s: String): String =
        s.replace("\\/", "/")
            .replace("\\u0025", "%")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\u003d", "=")
            .let { Regex("""\\u([0-9a-fA-F]{4})""").replace(it) { m -> m.groupValues[1].toInt(16).toChar().toString() } }

    private fun withHost(url: String, host: String): String =
        try {
            val u = URI(url)
            URI(u.scheme ?: "https", host, u.path, u.query, u.fragment).toString()
        } catch (_: Exception) { url }

    private fun idFrom(url: String): String {
        Regex("""[?&]fbid=(\d+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""/(?:photos?|posts|videos|permalink)/(?:[^/?]+/)*?(\w{6,})/?(?:\?|$)""").find(url)?.let { return it.groupValues[1] }
        Regex("""(\d{8,})""").findAll(url).lastOrNull()?.let { return it.groupValues[1] }
        return "photo_${(url.hashCode().toLong() and 0xffffffffL).toString(16)}"
    }

    private fun sameImage(url: String) = url.substringBefore('?')

    suspend fun fetch(url: String, cookieHeader: String?): List<ImageItem> = withContext(Dispatchers.IO) {
        val id = idFrom(url)
        val errors = mutableListOf<String>()
        val found = LinkedHashMap<String, String>() // dedupe key -> url
        var uploader = "facebook"
        var loginWall = false

        // 1) mbasic: the lightweight page links straight to the full-size image
        // 2) the page as given
        val candidates = listOf(withHost(url, "mbasic.facebook.com"), url)
        for (pageUrl in candidates) {
            val page = try { get(pageUrl, cookieHeader) } catch (e: Exception) {
                errors += "${e.message}"; continue
            }
            val html = page.html
            if (page.finalUrl.contains("/login") || html.contains("id=\"login_form\"")) loginWall = true

            Regex("""["']owner["']\s*:\s*\{[^{}]*?["']name["']\s*:\s*["']([^"']+)["']""").find(html)
                ?.let { uploader = unescape(it.groupValues[1]) }

            // full-size link on mbasic pages
            Regex("""href=["']([^"']*view_full_size[^"']*)["']""").findAll(html).forEach { m ->
                var link = unescape(m.groupValues[1])
                if (link.startsWith("/")) link = "https://mbasic.facebook.com$link"
                found.putIfAbsent("full:$link", link)
            }
            // web page JSON
            Regex("""["'](?:photo_image|viewer_image)["']\s*:\s*\{[^{}]*?["']uri["']\s*:\s*["'](https:[^"']+)["']""")
                .findAll(html).forEach { m ->
                    val u = unescape(m.groupValues[1])
                    found.putIfAbsent(sameImage(u), u)
                }
            if (found.isNotEmpty()) break
        }

        // last resort: og:image
        if (found.isEmpty()) {
            try {
                val page = get(url, cookieHeader)
                val og = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(page.html)
                    ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
                        .find(page.html)
                og?.let {
                    val u = unescape(it.groupValues[1])
                    // skip Facebook's generic logo / placeholder images
                    if (u.contains("fbcdn") || u.contains("scontent")) found[sameImage(u)] = u
                }
            } catch (e: Exception) { errors += "og:image: ${e.message}" }
        }

        if (found.isEmpty()) {
            val hint = if (loginWall || cookieHeader.isNullOrEmpty())
                "\nFacebook photos usually require being logged in - import your cookies.txt and turn on 'Use cookies.txt'."
            else ""
            error("ERROR: could not find any photo on that page.$hint" +
                if (errors.isNotEmpty()) "\n" + errors.joinToString("\n") else "")
        }
        found.values.map { ImageItem(it, uploader, id) }
    }

    suspend fun download(imageUrl: String, target: File, cookieHeader: String?) = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(imageUrl).header("User-Agent", userAgent)
        if (!cookieHeader.isNullOrEmpty() && imageUrl.contains("facebook.com")) b.header("Cookie", cookieHeader)
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Image download failed (HTTP ${resp.code}).")
            val type = resp.body?.contentType()?.toString().orEmpty()
            if (type.isNotEmpty() && !type.startsWith("image/")) error("That link did not return an image ($type).")
            resp.body!!.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
}
