package com.focustube.app

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Pure URL decisions shared by browser handling and local unit tests. */
internal object YouTubeUrlDecisions {
    /** Converts a YouTube Shorts URL to the standard watch URL; unrelated or malformed URLs return null. */
    fun shortsToWatchUrl(rawUrl: String): String? {
        val uri = parse(rawUrl) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!isYouTubeHost(host)) return null
        val parts = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        if (parts.size != 2 || !parts[0].equals("shorts", ignoreCase = true)) return null
        val videoId = parts[1]
        if (!videoId.matches(Regex("[A-Za-z0-9_-]+"))) return null
        return "https://m.youtube.com/watch?v=$videoId"
    }

    /** True for an explicit YouTube root path; origin-only address text stays ambiguous. */
    fun isHomeUrl(rawUrl: String): Boolean {
        // A browser often exposes only the origin while a page loads. It does not prove
        // that the user is on the home feed, so require an explicit URL path delimiter.
        val uri = parse(rawUrl) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return isYouTubeHost(host) && uri.rawPath == "/"
    }

    fun isYouTubeUrl(rawUrl: String): Boolean {
        val uri = parse(rawUrl) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return isYouTubeHost(host) || host == "youtu.be" || host.endsWith(".youtu.be")
    }

    fun shortsVideoId(rawUrl: String): String? {
        val uri = parse(rawUrl) ?: return null
        if (!isYouTubeHost(uri.host?.lowercase(Locale.ROOT).orEmpty())) return null
        val parts = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        return parts.getOrNull(1)?.takeIf {
            parts.size == 2 && parts[0].equals("shorts", ignoreCase = true) && it.matches(Regex("[A-Za-z0-9_-]+"))
        }
    }

    fun hasKnownNonHomePath(rawUrl: String): Boolean {
        val uri = parse(rawUrl) ?: return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        return path.startsWith("/watch") || path.startsWith("/shorts") ||
            path.startsWith("/results") || path.startsWith("/feed/")
    }

    fun shouldRedirectHomeToSubscriptions(rawUrl: String, homeTabSelected: Boolean): Boolean =
        homeTabSelected && !hasKnownNonHomePath(rawUrl) && isHomeUrl(rawUrl)

    private fun parse(rawUrl: String): URI? {
        val value = rawUrl.trim()
        if (value.isEmpty()) return null
        return try {
            URI(if (SCHEME.matchesAt(value, 0)) value else "https://$value")
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun isYouTubeHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com")

    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
}
