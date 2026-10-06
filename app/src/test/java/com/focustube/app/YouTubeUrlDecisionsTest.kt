package com.focustube.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlDecisionsTest {
    @Test fun shortsUrlBecomesWatchUrlAndDropsTrackingQuery() {
        assertEquals(
            "https://m.youtube.com/watch?v=AbC_12-x",
            YouTubeUrlDecisions.shortsToWatchUrl("https://www.youtube.com/shorts/AbC_12-x?feature=share"),
        )
    }

    @Test fun acceptsMobileAndMusicYoutubeHosts() {
        assertEquals("https://m.youtube.com/watch?v=xyz", YouTubeUrlDecisions.shortsToWatchUrl("m.youtube.com/shorts/xyz"))
        assertEquals("https://m.youtube.com/watch?v=xyz", YouTubeUrlDecisions.shortsToWatchUrl("https://music.youtube.com/shorts/xyz?si=abc"))
    }

    @Test fun shortLinksAndNonYoutubeHostsAreNotTreatedAsShorts() {
        assertNull(YouTubeUrlDecisions.shortsToWatchUrl("https://youtu.be/AbC_12-x"))
        assertNull(YouTubeUrlDecisions.shortsToWatchUrl("https://example.com/shorts/AbC_12-x"))
    }

    @Test fun rejectsMissingOrMalformedVideoIds() {
        assertNull(YouTubeUrlDecisions.shortsToWatchUrl("youtube.com/shorts/"))
        assertNull(YouTubeUrlDecisions.shortsToWatchUrl("youtube.com/shorts/id%2Fother"))
        assertNull(YouTubeUrlDecisions.shortsToWatchUrl("not a url"))
    }

    @Test fun detectsYoutubeHomeWithQueryButNotOtherPathsOrShortDomains() {
        assertTrue(YouTubeUrlDecisions.isHomeUrl("https://www.youtube.com/?app=desktop"))
        assertFalse(YouTubeUrlDecisions.isHomeUrl("m.youtube.com"))
        assertFalse(YouTubeUrlDecisions.isHomeUrl("https://m.youtube.com"))
        assertTrue(YouTubeUrlDecisions.isHomeUrl("https://m.youtube.com/"))
        assertTrue(YouTubeUrlDecisions.isHomeUrl("https://music.youtube.com/"))
        assertFalse(YouTubeUrlDecisions.isHomeUrl("https://youtube.com/feed/subscriptions"))
        assertFalse(YouTubeUrlDecisions.isHomeUrl("https://youtu.be/"))
        assertFalse(YouTubeUrlDecisions.isHomeUrl("https://youtube.com.attacker.example/"))
    }

    @Test fun identifiesYoutubeDomainsAndRejectsDomainOnlyHomeAssumptions() {
        assertTrue(YouTubeUrlDecisions.isYouTubeUrl("m.youtube.com"))
        assertTrue(YouTubeUrlDecisions.isYouTubeUrl("https://youtu.be/abc"))
        assertFalse(YouTubeUrlDecisions.isYouTubeUrl("youtube.com.attacker.example"))
        assertEquals("abc_1", YouTubeUrlDecisions.shortsVideoId("https://m.youtube.com/shorts/abc_1?feature=share"))
        assertTrue(YouTubeUrlDecisions.hasKnownNonHomePath("https://youtube.com/watch?v=abc"))
        assertFalse(YouTubeUrlDecisions.hasKnownNonHomePath("https://youtube.com/"))
        assertTrue(YouTubeUrlDecisions.shouldRedirectHomeToSubscriptions("https://youtube.com/", true))
        assertFalse(YouTubeUrlDecisions.shouldRedirectHomeToSubscriptions("https://youtube.com/", false))
        assertFalse(YouTubeUrlDecisions.shouldRedirectHomeToSubscriptions("https://youtube.com/watch?v=abc", true))
        assertFalse(YouTubeUrlDecisions.shouldRedirectHomeToSubscriptions("https://youtube.com", true))
    }
}
