package com.fbdownloader.app

import java.util.UUID

object Utils {
    /** Same pattern as FBDownloader.py: watch, reels, videos, photos, posts, share links, stories, fb.watch. */
    val FB_URL = Regex(
        """https?://(?:(?:www|m|web|mbasic|mobile)\.)?(?:facebook\.com|fb\.com)/""" +
            """(?:watch|reel|reels|video\.php|photo(?:\.php)?|permalink\.php|story\.php|share/[vrp]|stories|videos""" +
            """|[^/\s?]+/(?:videos|posts|photos|reel)|groups/[^/\s]+/(?:posts|permalink))""" +
            """|https?://fb\.watch/\S+""",
        RegexOption.IGNORE_CASE
    )

    private val BAD_CHARS = Regex("""[\\/*?:"<>|]""")
    private val SPACES = Regex("""\s+""")

    /**
     * Returns the full Facebook URL found in [text] (the pattern only matches the start of a link,
     * so the rest of the link up to the next whitespace is included).
     */
    fun findFbUrl(text: String?): String? {
        if (text == null) return null
        val m = FB_URL.find(text) ?: return null
        val rest = text.substring(m.range.first)
        return rest.takeWhile { !it.isWhitespace() }
    }

    fun sanitizeFilename(name: String?, maxLength: Int = 80): String {
        if (name.isNullOrEmpty()) return "facebook_${UUID.randomUUID().toString().take(8)}"
        var s = BAD_CHARS.replace(name, "")
        s = SPACES.replace(s, "_").trim('_')
        if (s.length > maxLength) s = s.substring(0, maxLength)
        s = s.trim('_', '.', ' ')
        return s.ifEmpty { "facebook_${UUID.randomUUID().toString().take(8)}" }
    }
}
