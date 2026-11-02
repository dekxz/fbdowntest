package com.fbdownloader.app

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps a private copy of the user's Firefox-exported cookies.txt (Netscape format).
 * yt-dlp reads it directly (--cookies); the image downloader builds a Cookie header from it.
 */
class CookieStore(private val context: Context) {

    val file = File(context.filesDir, "cookies.txt")

    fun hasCookies(): Boolean = file.exists() && file.length() > 0

    data class Cookie(val domain: String, val name: String, val value: String)

    private fun isFbDomain(domain: String): Boolean {
        val d = domain.trimStart('.').lowercase()
        return d == "facebook.com" || d.endsWith(".facebook.com") ||
            d == "fb.com" || d.endsWith(".fb.com") ||
            d == "fb.watch" || d.endsWith(".fb.watch")
    }

    /** Returns a short status message for the UI. Throws on unreadable input. */
    fun import(uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open the selected file.")
        val normalized = text.replace("\r\n", "\n")
        val body = if (normalized.lines().firstOrNull { it.isNotBlank() }?.contains("Netscape", true) == true) {
            normalized
        } else {
            "# Netscape HTTP Cookie File\n$normalized"
        }
        file.writeText(body)
        if (parse().none { isFbDomain(it.domain) }) {
            file.delete()
            error("No facebook.com cookies found in that file.")
        }
        return statusText()
    }

    fun clear() { file.delete() }

    fun statusText(): String {
        if (!hasCookies()) return "No cookies.txt imported"
        val fb = parse().filter { isFbDomain(it.domain) }
        val loggedIn = fb.any { it.name == "c_user" } && fb.any { it.name == "xs" }
        return "cookies.txt loaded: ${fb.size} Facebook cookies" +
            if (loggedIn) "" else " (no c_user/xs – you may not be logged in)"
    }

    /** "name=value; name=value" header for facebook.com requests (used by the image downloader). */
    fun headerForFacebook(): String? {
        val pairs = parse().filter { isFbDomain(it.domain) }.map { "${it.name}=${it.value}" }
        return pairs.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    fun parse(): List<Cookie> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { raw ->
            var line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            if (line.startsWith("#HttpOnly_")) line = line.removePrefix("#HttpOnly_")
            else if (line.startsWith("#")) return@mapNotNull null
            val f = line.split('\t')
            if (f.size < 7) return@mapNotNull null
            Cookie(f[0], f[5], f[6])
        }
    }
}
