package com.focustube.app

/**
 * Accessibility selectors for supported apps. Resource IDs are preferred; descriptions,
 * labels, class, and screen position are fallbacks because app layouts change over time.
 * YouTube selectors should be checked against fresh device dumps when those are available.
 */
internal object Selectors {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    // YouTube has used reel_* names for Shorts views. Keep the older shorts_* spellings too.
    val shortsPlayerIdParts = listOf("reel_player", "reel_recycler", "shorts_player")
    val shortsShelfIdParts = listOf("reel_shelf", "shorts_shelf", "reel_item_shelf")
    val shortsTabIdParts = listOf("shorts_tab", "tab_shorts", "reel_tab")
    val homeFeedIdParts = listOf(":id/results", ":id/related_results")
    val subscriptionsTabIdParts = listOf("subscriptions_tab", "tab_subscriptions")

    // Bottom navigation labels. Exclude the exact tab label from shelf-heading matches.
    val shortsTabLabels = setOf("shorts", "youtube shorts")
    val homeTabLabels = setOf("home")
    val subscriptionsTabLabels = setOf("subscriptions", "subscription")

    val browserPackages = setOf(
        "com.android.chrome",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.vivaldi.browser",
    )

    // Firefox's GeckoView ID is known; other browsers get ID and EditText/position fallbacks.
    val knownBrowserBarParts = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar", "address_bar"),
        "com.microsoft.emmx" to listOf("url_bar", "address_bar"),
        "com.sec.android.app.sbrowser" to listOf("url_bar", "location_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view"),
        "com.opera.browser" to listOf("url_field", "url_bar", "address_bar", "omnibox"),
        "com.vivaldi.browser" to listOf("url_bar", "address_bar", "url_field", "omnibox"),
    )

    /** Selectors used only for YouTube's web UI inside supported browsers. */
    object Web {
        val shortsLabels = setOf("shorts", "youtube shorts")
        val homeLabels = setOf("home")
        val shortsChipLabels = setOf("shorts")
        val youtubePageTextParts = listOf("youtube")
        val youtubePageDescriptionParts = listOf("youtube")
        val youtubePageIdParts = listOf("youtube", "video_renderer")
        val youtubePageClassParts = listOf("youtube")
        val remixSoundLabels = setOf("remix", "sound", "use this sound")
        val playerControlText = setOf("play", "pause", "seek", "mute", "unmute", "settings", "full screen")
        val playerControlDescription = setOf("play", "pause", "seek", "mute", "unmute", "settings", "full screen")
        val playerControlClassParts = listOf("playercontrol", "playbackcontrol", "videocontrol")
        val videoClassParts = listOf("htmlvideoelement", "videoview", "video")
        val toolbarIdParts = listOf("toolbar", "omnibox", "url_bar", "url_field", "address_bar", "location_bar", "mozac_browser_toolbar")
        val toolbarClassParts = listOf("toolbar", "omnibox", "addressbar")
    }

    fun knownBrowserBarResourceIds(packageName: String) = knownBrowserBarParts[packageName].orEmpty()
        .map { "$packageName:id/$it" }

    fun isBrowserToolbarNode(id: String, className: String) =
        Web.toolbarIdParts.any { id.contains(it, ignoreCase = true) } ||
            Web.toolbarClassParts.any { className.contains(it, ignoreCase = true) }

    fun isShortsPlayerId(id: String) = shortsPlayerIdParts.any(id::contains)
    fun isShortsShelfId(id: String) = shortsShelfIdParts.any(id::contains)
    fun isShortsTabId(id: String) = shortsTabIdParts.any(id::contains)
    fun isHomeFeedId(id: String) = homeFeedIdParts.any(id::contains)
    fun isSubscriptionsTabId(id: String) = subscriptionsTabIdParts.any(id::contains)

    fun label(value: String) = value.trim().lowercase()
    fun isShortsTabLabel(value: String) = label(value) in shortsTabLabels
    fun isShortsShelfLabel(value: String) = label(value).contains("shorts") && !isShortsTabLabel(value)
    fun isHomeTabLabel(value: String) = label(value) in homeTabLabels
    fun isSubscriptionsTabLabel(value: String) = label(value) in subscriptionsTabLabels

    fun isKnownBrowserBarId(packageName: String, id: String): Boolean =
        knownBrowserBarParts[packageName].orEmpty().any { id.contains(it, ignoreCase = true) }

    fun isLikelyBrowserBarId(id: String): Boolean {
        val value = id.lowercase()
        return "url_bar" in value || "url_view" in value || "url_field" in value ||
            "address_bar" in value || "location_bar" in value || "omnibox" in value
    }
}
