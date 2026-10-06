package com.focustube.app

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("focustube", Context.MODE_PRIVATE)
    fun get(c: Context, key: String, def: Boolean = true) = sp(c).getBoolean(key, def)
    fun set(c: Context, key: String, v: Boolean) = sp(c).edit().putBoolean(key, v).apply()
    fun getInt(c: Context, key: String, def: Int) = sp(c).getInt(key, def)
    fun setInt(c: Context, key: String, value: Int) = sp(c).edit().putInt(key, value).apply()
    fun getLong(c: Context, key: String, def: Long = 0L) = sp(c).getLong(key, def)
    fun setLong(c: Context, key: String, value: Long) = sp(c).edit().putLong(key, value).apply()
    fun getString(c: Context, key: String, def: String) = sp(c).getString(key, def) ?: def
    fun setString(c: Context, key: String, value: String) = sp(c).edit().putString(key, value).apply()
    fun isDisclosureAccepted(c: Context) = get(c, DISCLOSURE_ACCEPTED, false)
    fun acceptDisclosure(c: Context) = set(c, DISCLOSURE_ACCEPTED, true)

    fun isPaused(c: Context, now: Long = System.currentTimeMillis()) = getLong(c, PAUSE_UNTIL) > now

    fun isFocusTime(c: Context, now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!get(c, SCHEDULE_ENABLED, false)) return true
        val minuteNow = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val start = getInt(c, SCHEDULE_START_MINUTE, 9 * 60).coerceIn(0, 1439)
        val end = getInt(c, SCHEDULE_END_MINUTE, 17 * 60).coerceIn(0, 1439)
        if (start == end) return true
        return if (start < end) minuteNow in start until end else minuteNow >= start || minuteNow < end
    }

    fun pauseForTenMinutes(c: Context, now: Long = System.currentTimeMillis()) =
        setLong(c, PAUSE_UNTIL, now + PAUSE_DURATION_MS)

    fun isEnabled(c: Context) = get(c, MASTER_ENABLED, true)
    fun isActive(c: Context) = isEnabled(c) && !isPaused(c) && isFocusTime(c)

    const val BLOCK_SHORTS_PLAYER = "block_shorts_player"   // close Shorts player / redirect Shorts URLs
    const val HIDE_SHORTS_UI = "hide_shorts_ui"             // cover Shorts tab + Shorts shelves in app
    const val HIDE_FEED = "hide_feed"                       // cover home feed (app) / redirect home (browser)
    const val HIDE_SHORTS_OTHER_PAGES = "hide_shorts_other_pages"
    const val HOME_FEED_MODE = "home_feed_mode"
    const val HOME_FEED_COVER = "cover"
    const val HOME_FEED_SUBSCRIPTIONS = "subscriptions"
    const val MASTER_ENABLED = "master_enabled"
    const val SCHEDULE_ENABLED = "schedule_enabled"
    const val SCHEDULE_START_MINUTE = "schedule_start_minute"
    const val SCHEDULE_END_MINUTE = "schedule_end_minute"
    const val PAUSE_UNTIL = "pause_until"
    const val PAUSE_DURATION_MS = 10 * 60 * 1000L
    const val DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
}
