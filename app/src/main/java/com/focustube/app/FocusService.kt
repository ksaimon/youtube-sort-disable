package com.focustube.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar

class FocusService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val overlays = linkedMapOf<String, Overlay>()
    private var scanPending = false
    private var scanRunnable: Runnable? = null
    private var lastActionAt = 0L
    private var receiverRegistered = false
    private var lastRedirectedVideoId: String? = null
    private var lastRedirectedVideoAt = 0L

    private data class NodeRecord(
        val node: AccessibilityNodeInfo,
        val id: String,
        val description: String,
        val text: String,
        val hint: String,
        val className: String,
        val bounds: Rect,
        val visible: Boolean,
        val selected: Boolean,
        val childCount: Int,
        val parentIndex: Int,
    )

    private data class Overlay(
        val view: View,
        val params: WindowManager.LayoutParams,
        val bounds: Rect,
    )

    private data class TreeScan(
        val records: List<NodeRecord>,
        val hitMaxNodeCount: Boolean,
        val hitTimeLimit: Boolean,
    )

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) clearOverlays()
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!Prefs.isActive(this)) clearOverlays()
        scheduleNextBoundary()
        requestScan(0L, expedite = true)
    }

    private val boundaryRunnable = Runnable {
        if (Prefs.isActive(this)) requestScan(0L, expedite = true) else clearOverlays()
        scheduleNextBoundary()
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenOffReceiver, filter)
        }
        receiverRegistered = true
        getSharedPreferences("focustube", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scheduleNextBoundary()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!Prefs.isActive(this)) {
            clearOverlays()
            scheduleNextBoundary()
            return
        }

        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        requestScan(if (isScroll) SCROLL_COALESCE_MS else CONTENT_COALESCE_MS, expedite = isScroll)
    }

    override fun onInterrupt() {
        clearOverlays()
    }

    override fun onDestroy() {
        clearOverlays()
        handler.removeCallbacksAndMessages(null)
        getSharedPreferences("focustube", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    /** Coalesce bursty content events while allowing scroll updates at a steady short interval. */
    private fun requestScan(delayMs: Long, expedite: Boolean) {
        if (scanPending) {
            if (!expedite) return
            scanRunnable?.let(handler::removeCallbacks)
        }
        scanPending = true
        val runnable = Runnable {
            scanPending = false
            scanRunnable = null
            scanActiveWindow()
        }
        scanRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun scanActiveWindow() {
        if (!Prefs.isActive(this)) {
            clearOverlays()
            return
        }
        val root = rootInActiveWindow ?: run {
            clearOverlays()
            return
        }
        val packageName = try {
            root.packageName?.toString()
        } catch (_: RuntimeException) {
            recycleAll(listOf(root))
            clearOverlays()
            return
        }
        when {
            packageName == Selectors.YOUTUBE_PACKAGE -> scanYoutube(root)
            packageName in Selectors.browserPackages -> {
                scanBrowser(root, packageName!!)
            }
            else -> {
                clearOverlays()
                recycleAll(listOf(root))
            }
        }
    }

    private fun scanYoutube(root: AccessibilityNodeInfo) {
        val owned = mutableListOf<AccessibilityNodeInfo>()
        try {
            val nodes = collectTree(root, owned).records
            if (nodes.isEmpty()) {
                clearOverlays()
                return
            }
            val screen = screenBounds()
            val rootBounds = nodes.first().bounds
            // PIP windows are too small for overlays and can obscure system controls.
            val rootArea = rootBounds.width().toLong() * rootBounds.height().toLong()
            val screenArea = screen.width().toLong() * screen.height().toLong()
            if (screenArea <= 0 || rootArea < screenArea / PIP_MIN_SCREEN_FRACTION) {
                clearOverlays()
                return
            }

            var homeSelected = false
            var feedNode: NodeRecord? = null
            var shortsPlayer = false
            var shelfOrdinal = 0
            val targets = linkedMapOf<String, Rect>()

            nodes.forEachIndexed { index, item ->
                if (!item.visible) return@forEachIndexed
                val label = item.description.ifBlank { item.text }
                val labelIsShorts = Selectors.isShortsTabLabel(label)
                val shortsTabSignal = labelIsShorts || Selectors.isShortsTabId(item.id)
                val bottomTab = shortsTabSignal && isNavigationPosition(item.bounds, screen)
                if (shortsTabSignal && bottomTab) {
                    targets["shorts-tab:$index"] = item.bounds
                }
                if ((item.description.let(Selectors::isHomeTabLabel) || item.text.let(Selectors::isHomeTabLabel)) && item.selected) {
                    homeSelected = true
                }
                if (Selectors.isHomeFeedId(item.id) && (feedNode == null || item.bounds.height() > feedNode.bounds.height())) {
                    feedNode = item
                }

                val itemArea = item.bounds.width().toLong() * item.bounds.height().toLong()
                val fullPlayerArea = itemArea >= screenArea / 2
                if (fullPlayerArea && (Selectors.isShortsPlayerId(item.id) ||
                        item.className.contains("Shorts", ignoreCase = true) ||
                        item.description.contains("shorts player", ignoreCase = true) ||
                        item.text.contains("shorts player", ignoreCase = true))) {
                    shortsPlayer = true
                }

                if (Selectors.isShortsShelfId(item.id) && item.bounds.width() > screen.width() / 3) {
                    targets["shorts-shelf:${shelfOrdinal++}:${item.id}"] = item.bounds
                }
            }

            if (!Prefs.get(this, Prefs.HIDE_SHORTS_UI)) {
                targets.keys.removeAll { it.startsWith("shorts-tab:") || it.startsWith("shorts-shelf:") }
            }

            if (Prefs.get(this, Prefs.HIDE_SHORTS_OTHER_PAGES)) {
                val semanticOrdinals = mutableMapOf<String, Int>()
                nodes.forEachIndexed { index, item ->
                    if (!item.visible) return@forEachIndexed
                    val label = item.description.ifBlank { item.text }
                    if (isNavigationPosition(item.bounds, screen)) return@forEachIndexed
                    if (!Selectors.isShortsShelfLabel(label) && !Selectors.isShortsTabLabel(label)) return@forEachIndexed
                    val target = nearestShelfContainer(nodes, index, screen) ?: item.bounds
                    val normalizedLabel = Selectors.label(label)
                    val ordinal = semanticOrdinals.getOrDefault(normalizedLabel, 0)
                    semanticOrdinals[normalizedLabel] = ordinal + 1
                    targets["semantic-shorts:$normalizedLabel:$ordinal"] = target
                }
            }

            if (Prefs.get(this, Prefs.BLOCK_SHORTS_PLAYER) && shortsPlayer && cooldown()) {
                clearOverlays()
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }

            if (Prefs.get(this, Prefs.HIDE_FEED) && homeSelected) {
                val feed = feedNode ?: findLikelyFeed(nodes, screen)
                val mode = Prefs.getString(this, Prefs.HOME_FEED_MODE, Prefs.HOME_FEED_COVER)
                if (mode == Prefs.HOME_FEED_SUBSCRIPTIONS) {
                    clickSubscriptionsTab(nodes)
                    targets.remove("home-feed")
                } else {
                    feed?.let { targets["home-feed"] = it.bounds }
                }
            } else {
                targets.remove("home-feed")
            }

            updateOverlays(targets.mapValues { (_, rect) -> Rect(rect) }, screen)
        } catch (_: RuntimeException) {
            clearOverlays()
        } finally {
            recycleAll(owned)
        }
    }

    private fun scanBrowser(root: AccessibilityNodeInfo, packageName: String) {
        val owned = mutableListOf<AccessibilityNodeInfo>()
        try {
            val screen = screenBounds()
            var bar = findBrowserBarById(root, packageName, owned)
            if (bar == null) {
                val fallback = collectTree(root, owned, MAX_BROWSER_BAR_NODES, MAX_BROWSER_BAR_SCAN_MS)
                bar = fallback.records.asSequence()
                    .filter { it.visible && it.bounds.top <= screen.top + screen.height() / 3 }
                    .map { it to browserBarScore(it, packageName, screen) }
                    .filter { it.second > 0 }
                    .maxByOrNull { it.second }?.first
            }
            if (BuildConfig.DEBUG) {
                if (bar == null) {
                    Log.d("FocusTube", "URL bar found=false")
                } else {
                    Log.d("FocusTube", "URL bar found=true id=${bar.id} text=${bar.text}")
                }
            }
            val raw = bar?.text?.ifBlank { bar.hint }?.trim().orEmpty()
            val pageScan = collectTree(
                root, owned, MAX_BROWSER_PAGE_NODES, MAX_BROWSER_PAGE_SCAN_MS,
                skipSubtree = { item -> Selectors.isBrowserToolbarNode(item.id, item.className) },
            )
            val nodes = pageScan.records
            if (BuildConfig.DEBUG) logBrowserScan(packageName, pageScan)
            val youtubePage = if (bar != null && raw.isNotBlank()) YouTubeUrlDecisions.isYouTubeUrl(raw)
                else hasYoutubePageSignals(nodes)
            if (!youtubePage) {
                clearOverlays()
                return
            }

            if (bar?.node?.isFocused == true) return
            val shortId = YouTubeUrlDecisions.shortsVideoId(raw)
            if (Prefs.get(this, Prefs.BLOCK_SHORTS_PLAYER)) {
                if (shortId != null) {
                    if (mayRedirectVideo(shortId) && cooldown()) {
                        rememberRedirectedVideo(shortId)
                        open(packageName, "https://m.youtube.com/watch?v=$shortId")
                        return
                    }
                } else if (isWebShortsPlayer(nodes, screen) && cooldown()) {
                    clearOverlays()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }

            val targets = browserShortsTargets(nodes, screen)
            updateOverlays(targets, screen)

            val mode = Prefs.getString(this, Prefs.HOME_FEED_MODE, Prefs.HOME_FEED_COVER)
            val homeSelected = nodes.any { it.visible && it.selected &&
                (Selectors.Web.homeLabels.contains(Selectors.label(it.text)) || Selectors.Web.homeLabels.contains(Selectors.label(it.description))) }
            if (Prefs.get(this, Prefs.HIDE_FEED) && mode == Prefs.HOME_FEED_SUBSCRIPTIONS &&
                YouTubeUrlDecisions.shouldRedirectHomeToSubscriptions(raw, homeSelected) && cooldown()) {
                open(packageName, "https://m.youtube.com/feed/subscriptions")
            }
        } catch (_: RuntimeException) {
            clearOverlays()
        } finally {
            recycleAll(owned)
        }
    }

    private fun collectTree(
        root: AccessibilityNodeInfo,
        owned: MutableList<AccessibilityNodeInfo>,
        maxNodes: Int = MAX_NODE_COUNT,
        maxScanMs: Long = MAX_TREE_SCAN_MS,
        skipSubtree: (NodeRecord) -> Boolean = { false },
    ): TreeScan {
        val records = ArrayList<NodeRecord>(maxNodes)
        val deadline = SystemClock.uptimeMillis() + maxScanMs
        var hitMaxNodeCount = false
        var hitTimeLimit = false

        fun visit(node: AccessibilityNodeInfo, depth: Int, parentIndex: Int) {
            owned += node
            if (depth > MAX_TREE_DEPTH) return
            if (records.size >= maxNodes) {
                hitMaxNodeCount = true
                return
            }
            if (SystemClock.uptimeMillis() >= deadline) {
                hitTimeLimit = true
                return
            }
            val index = records.size
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val record = NodeRecord(
                node = node,
                id = node.viewIdResourceName.orEmpty(),
                description = node.contentDescription?.toString().orEmpty(),
                text = node.text?.toString().orEmpty(),
                hint = node.hintText?.toString().orEmpty(),
                className = node.className?.toString().orEmpty(),
                bounds = rect,
                visible = node.isVisibleToUser,
                selected = node.isSelected,
                childCount = node.childCount,
                parentIndex = parentIndex,
            )
            records += record
            if (skipSubtree(record)) return
            if (depth >= MAX_TREE_DEPTH) return
            for (childIndex in 0 until record.childCount) {
                if (records.size >= maxNodes) {
                    hitMaxNodeCount = true
                    break
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    hitTimeLimit = true
                    break
                }
                val child = try {
                    node.getChild(childIndex)
                } catch (_: RuntimeException) {
                    null
                } ?: continue
                visit(child, depth + 1, index)
            }
        }

        visit(root, 0, -1)
        return TreeScan(records, hitMaxNodeCount, hitTimeLimit)
    }

    private fun findBrowserBarById(
        root: AccessibilityNodeInfo,
        packageName: String,
        owned: MutableList<AccessibilityNodeInfo>,
    ): NodeRecord? {
        for (resourceId in Selectors.knownBrowserBarResourceIds(packageName)) {
            val found = try { root.findAccessibilityNodeInfosByViewId(resourceId) } catch (_: RuntimeException) { null }
                ?: continue
            owned.addAll(found)
            for (node in found) {
                val record = nodeRecord(node, -1)
                if (record.visible && record.bounds.height() > 0) return record
            }
        }
        return null
    }

    private fun nodeRecord(node: AccessibilityNodeInfo, parentIndex: Int) = NodeRecord(
        node = node, id = node.viewIdResourceName.orEmpty(),
        description = node.contentDescription?.toString().orEmpty(), text = node.text?.toString().orEmpty(),
        hint = node.hintText?.toString().orEmpty(), className = node.className?.toString().orEmpty(),
        bounds = Rect().also { node.getBoundsInScreen(it) }, visible = node.isVisibleToUser,
        selected = node.isSelected, childCount = node.childCount, parentIndex = parentIndex,
    )

    private fun hasYoutubePageSignals(nodes: List<NodeRecord>): Boolean = nodes.any { item ->
        item.visible && (Selectors.Web.youtubePageTextParts.any { item.text.contains(it, true) } ||
            Selectors.Web.youtubePageDescriptionParts.any { item.description.contains(it, true) } ||
            Selectors.Web.youtubePageIdParts.any { item.id.contains(it, true) } ||
            Selectors.Web.youtubePageClassParts.any { item.className.contains(it, true) })
    }

    private fun isWebShortsPlayer(nodes: List<NodeRecord>, screen: Rect): Boolean {
        val visible = nodes.filter { it.visible }
        val hasShortsLabel = visible.any { item ->
            Selectors.Web.shortsLabels.contains(Selectors.label(item.text)) ||
                Selectors.Web.shortsLabels.contains(Selectors.label(item.description))
        }
        val hasPlayerControls = visible.any { item ->
            Selectors.Web.playerControlClassParts.any { item.className.contains(it, true) } ||
                Selectors.Web.playerControlText.contains(Selectors.label(item.text)) ||
                Selectors.Web.playerControlDescription.contains(Selectors.label(item.description))
        }
        val hasRemixSound = visible.any { item ->
            Selectors.Web.remixSoundLabels.contains(Selectors.label(item.text)) ||
                Selectors.Web.remixSoundLabels.contains(Selectors.label(item.description))
        }
        val hasVerticalVideo = visible.any { item ->
            Selectors.Web.videoClassParts.any { item.className.contains(it, true) } &&
                item.bounds.height() >= screen.height() * 0.8 && item.bounds.height() > item.bounds.width()
        }
        return hasShortsLabel && (hasPlayerControls || hasRemixSound || hasVerticalVideo)
    }

    private fun browserShortsTargets(nodes: List<NodeRecord>, screen: Rect): Map<String, Rect> {
        if (!Prefs.get(this, Prefs.HIDE_SHORTS_UI) && !Prefs.get(this, Prefs.HIDE_SHORTS_OTHER_PAGES)) return emptyMap()
        val targets = linkedMapOf<String, Rect>()
        val ordinals = mutableMapOf<String, Int>()
        nodes.forEachIndexed { index, item ->
            if (!item.visible) return@forEachIndexed
            val label = Selectors.label(item.text.ifBlank { item.description })
            val isShorts = label in Selectors.Web.shortsLabels || label in Selectors.Web.shortsChipLabels
            if (!isShorts) return@forEachIndexed
            val navigation = isNavigationPosition(item.bounds, screen)
            if (navigation && Prefs.get(this, Prefs.HIDE_SHORTS_UI)) {
                targets["browser-shorts-tab:$index"] = item.bounds
            } else if (!navigation && (Prefs.get(this, Prefs.HIDE_SHORTS_UI) || Prefs.get(this, Prefs.HIDE_SHORTS_OTHER_PAGES))) {
                val rect = nearestShelfContainer(nodes, index, screen) ?: item.bounds
                val ordinal = ordinals.getOrDefault(label, 0)
                ordinals[label] = ordinal + 1
                targets["browser-shorts-shelf:$label:$ordinal"] = rect
            }
        }
        return targets
    }

    private fun mayRedirectVideo(videoId: String): Boolean =
        videoId != lastRedirectedVideoId || SystemClock.uptimeMillis() - lastRedirectedVideoAt >= VIDEO_REDIRECT_LOOP_GUARD_MS

    private fun rememberRedirectedVideo(videoId: String) {
        lastRedirectedVideoId = videoId
        lastRedirectedVideoAt = SystemClock.uptimeMillis()
    }

    private fun logBrowserScan(packageName: String, scan: TreeScan) {
        val nodes = scan.records
        Log.d("FocusTube", "Browser scan package=$packageName nodes=${nodes.size} hitMaxNodeCount=${scan.hitMaxNodeCount} hitTimeLimit=${scan.hitTimeLimit}")
        val shortsNodes = nodes.filter {
            it.text.equals("Shorts", ignoreCase = true) || it.description.equals("Shorts", ignoreCase = true)
        }
        Log.d("FocusTube", "Shorts nodes=${shortsNodes.size} bounds=${shortsNodes.joinToString { it.bounds.toShortString() }}")
        nodes.asSequence().filter { it.visible }.take(40).forEach {
            Log.d("FocusTube", "Visible node id=${it.id} class=${it.className} text=${it.text} contentDescription=${it.description}")
        }
    }

    private fun browserBarScore(item: NodeRecord, packageName: String, screen: Rect): Int {
        if (Selectors.isKnownBrowserBarId(packageName, item.id)) return 100
        if (Selectors.isLikelyBrowserBarId(item.id)) return 80
        val topZone = item.bounds.top <= screen.top + screen.height() / 5
        if (!topZone || item.bounds.width() < screen.width() / 3) return 0
        val editField = item.className.contains("EditText", ignoreCase = true) ||
            item.className.contains("AutoCompleteTextView", ignoreCase = true)
        val urlLike = item.text.contains("youtube.com", ignoreCase = true) ||
            item.text.startsWith("http", ignoreCase = true)
        val addressHint = item.hint.contains("search", ignoreCase = true) ||
            item.hint.contains("address", ignoreCase = true) || item.hint.contains("url", ignoreCase = true)
        return when {
            editField && (urlLike || addressHint) -> 60
            urlLike -> 45
            else -> 0
        }
    }

    private fun clickSubscriptionsTab(nodes: List<NodeRecord>) {
        val tab = nodes.firstOrNull { item ->
            !item.selected && (Selectors.isSubscriptionsTabId(item.id) ||
                Selectors.isSubscriptionsTabLabel(item.description) || Selectors.isSubscriptionsTabLabel(item.text))
        } ?: return
        if (!cooldown()) return
        var candidate: NodeRecord? = tab
        repeat(4) {
            val current = candidate ?: return
            if (current.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            candidate = nodes.getOrNull(current.parentIndex)
        }
    }

    private fun findLikelyFeed(nodes: List<NodeRecord>, screen: Rect): NodeRecord? = nodes.asSequence()
        .filter { item ->
            item.visible && item.className.contains("RecyclerView", ignoreCase = true) &&
                item.bounds.width() >= screen.width() * 0.72 && item.bounds.height() >= screen.height() * 0.28 &&
                item.bounds.top >= screen.top + screen.height() / 12
        }
        .maxByOrNull { it.bounds.width().toLong() * it.bounds.height().toLong() }

    private fun nearestShelfContainer(nodes: List<NodeRecord>, index: Int, screen: Rect): Rect? {
        var parent = nodes[index].parentIndex
        while (parent >= 0) {
            val candidate = nodes.getOrNull(parent) ?: break
            val height = candidate.bounds.height()
            if (candidate.visible && candidate.bounds.width() >= screen.width() / 2 &&
                height in (screen.height() / 10)..(screen.height() * 3 / 5) && candidate.childCount >= 2) {
                return candidate.bounds
            }
            parent = candidate.parentIndex
        }
        return null
    }

    private fun isNavigationPosition(rect: Rect, screen: Rect): Boolean {
        val bottomNavigation = rect.centerY() >= screen.top + screen.height() * 0.78
        val sideNavigation = rect.centerX() <= screen.left + screen.width() * 0.14 ||
            rect.centerX() >= screen.right - screen.width() * 0.14
        return bottomNavigation || sideNavigation
    }

    private fun open(packageName: String, url: String) {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("com.android.browser.application_id", packageName),
        )
    }

    private fun cooldown(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return false
        lastActionAt = now
        return true
    }

    private fun screenBounds(): Rect {
        if (Build.VERSION.SDK_INT >= 30) return Rect(windowManager.maximumWindowMetrics.bounds)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    /** Update only moved targets and remove obsolete ones, avoiding overlay churn while scrolling. */
    private fun updateOverlays(targets: Map<String, Rect>, screen: Rect) {
        val clippedTargets = targets.mapNotNull { (key, original) ->
            val clipped = Rect(original)
            if (!clipped.intersect(screen) || clipped.width() <= 0 || clipped.height() <= 0) null
            else key to clipped
        }.toMap()

        (overlays.keys - clippedTargets.keys).forEach(::removeOverlay)
        clippedTargets.forEach { (key, rect) ->
            val existing = overlays[key]
            if (existing != null && existing.bounds == rect) return@forEach
            if (existing != null) {
                existing.params.width = rect.width()
                existing.params.height = rect.height()
                existing.params.x = rect.left
                existing.params.y = rect.top
                try {
                    windowManager.updateViewLayout(existing.view, existing.params)
                    overlays[key] = existing.copy(bounds = Rect(rect))
                } catch (_: Exception) {
                    removeOverlay(key)
                }
            }
            if (key !in overlays) addOverlay(key, rect)
        }
    }

    private fun addOverlay(key: String, rect: Rect) {
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 17, 22))
            addView(TextView(context).apply {
                text = getString(R.string.overlay_label)
                setTextColor(Color.LTGRAY)
                textSize = 12f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val params = WindowManager.LayoutParams(
            rect.width(), rect.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }
        try {
            windowManager.addView(view, params)
            overlays[key] = Overlay(view, params, Rect(rect))
        } catch (_: Exception) {
            // The active window can disappear between inspection and overlay attachment.
        }
    }

    private fun removeOverlay(key: String) {
        val overlay = overlays.remove(key) ?: return
        try {
            windowManager.removeView(overlay.view)
        } catch (_: Exception) {
            // The system may already have detached it during a window transition.
        }
    }

    private fun clearOverlays() {
        overlays.keys.toList().forEach(::removeOverlay)
    }

    @Suppress("DEPRECATION")
    private fun recycleAll(nodes: List<AccessibilityNodeInfo>) {
        if (Build.VERSION.SDK_INT < 33) nodes.forEach { it.recycle() }
    }

    private fun scheduleNextBoundary() {
        handler.removeCallbacks(boundaryRunnable)
        val now = System.currentTimeMillis()
        val boundaries = mutableListOf<Long>()
        Prefs.getLong(this, Prefs.PAUSE_UNTIL).takeIf { it > now }?.let(boundaries::add)
        if (Prefs.get(this, Prefs.SCHEDULE_ENABLED, false)) {
            val current = Calendar.getInstance()
            listOf(Prefs.SCHEDULE_START_MINUTE, Prefs.SCHEDULE_END_MINUTE).forEach { key ->
                val minute = Prefs.getInt(this, key, if (key == Prefs.SCHEDULE_START_MINUTE) 9 * 60 else 17 * 60)
                    .coerceIn(0, 1439)
                for (dayOffset in 0..1) {
                    val boundary = current.clone() as Calendar
                    boundary.add(Calendar.DAY_OF_YEAR, dayOffset)
                    boundary.set(Calendar.HOUR_OF_DAY, minute / 60)
                    boundary.set(Calendar.MINUTE, minute % 60)
                    boundary.set(Calendar.SECOND, 0)
                    boundary.set(Calendar.MILLISECOND, 0)
                    if (boundary.timeInMillis > now) boundaries += boundary.timeInMillis
                }
            }
        }
        val next = boundaries.minOrNull() ?: return
        handler.postDelayed(boundaryRunnable, (next - now).coerceAtLeast(250L))
    }

    companion object {
        private const val MAX_NODE_COUNT = 1_200
        private const val MAX_TREE_DEPTH = 24
        private const val MAX_TREE_SCAN_MS = 65L
        private const val MAX_BROWSER_BAR_NODES = 2_500
        private const val MAX_BROWSER_BAR_SCAN_MS = 120L
        private const val MAX_BROWSER_PAGE_NODES = 2_500
        private const val MAX_BROWSER_PAGE_SCAN_MS = 120L
        private const val VIDEO_REDIRECT_LOOP_GUARD_MS = 5_000L
        private const val PIP_MIN_SCREEN_FRACTION = 5L
        private const val SCROLL_COALESCE_MS = 50L
        private const val CONTENT_COALESCE_MS = 100L
        private const val ACTION_COOLDOWN_MS = 1_500L
    }
}
