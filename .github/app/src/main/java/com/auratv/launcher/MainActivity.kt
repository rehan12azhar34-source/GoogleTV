package com.auratv.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("aura", MODE_PRIVATE) }

    private var tab = "For you"
    private var heroIdx = 0
    private var lastBack = 0L

    private lateinit var heroBg: ImageView
    private lateinit var heroBox: LinearLayout
    private lateinit var heroSrc: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroLib: TextView
    private lateinit var dotsBox: LinearLayout
    private lateinit var contentBox: LinearLayout
    private lateinit var searchBox: LinearLayout
    private lateinit var remoteBox: LinearLayout
    private val tabBtns = mutableMapOf<String, TextView>()

    private val heroTick = object : Runnable {
        override fun run() {
            if (prefs.getBoolean("rot", true) && tab in listOf("For you", "Movies", "Shows")) {
                val hl = heroItems()
                if (hl.size > 1) { heroIdx = (heroIdx + 1) % hl.size; bindHero(hl[heroIdx], hl.size) }
            }
            h.postDelayed(this, 7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0B0C10")))
        try {
            initUi()
        } catch (e: Throwable) {
            val tv = TextView(this).apply {
                text = "Aura TV Error:\n${e.stackTraceToString()}"
                setTextColor(Color.WHITE); setBackgroundColor(Color.BLACK); setPadding(32, 48, 32, 32)
            }
            setContentView(ScrollView(this).apply { addView(tv) })
        }
    }

    private fun initUi() {
        val dp = { v: Int -> TvApps.dp(this, v) }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0C10")) }
        heroBg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(heroBg, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xE60B0C10.toInt(), 0x660B0C10, 0x000B0C10))
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xF20B0C10.toInt(), 0x550B0C10, 0x000B0C10))
        }, FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(24)) }
        scroll.addView(page)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(8))
        }
        top.addView(TvApps.pillBtn(this, "👤", false) { TvOverlays.showProfile(this) { renderContent() } })
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("For you", "Movies", "Shows", "Apps", "Library").forEach { name ->
            val tv = TvApps.pillBtn(this, name, false) { selectTab(name) }
            tabBtns[name] = tv
            tabRow.addView(tv, lpWrap(dp(6)))
        }
        tabScroll.addView(tabRow)
        top.addView(tabScroll, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
        top.addView(TvApps.pillBtn(this, "🔍", false) { searchBox.visibility = View.VISIBLE; (searchBox.tag as? Runnable)?.run() }, lpWrap(dp(6)))
        top.addView(TvApps.pillBtn(this, "⚙", false) {
            TvOverlays.showSettings(this, remoteBox.visibility == View.VISIBLE) { remOn ->
                remoteBox.visibility = if (remOn) View.VISIBLE else View.GONE
                TvData.fetchTvMaze { if (!isFinishing) renderContent() }
                renderContent()
            }
        }, lpWrap(dp(6)))
        top.addView(TvApps.pillBtn(this, "Aura TV", false) { TvOverlays.showAbout(this) })
        page.addView(top)

        heroBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(18), dp(24), dp(10)) }
        heroSrc = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 11f; setTypeface(null, Typeface.BOLD)
            setBackgroundColor(0x88000000.toInt()); setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        heroTitle = TextView(this).apply { setTextColor(Color.WHITE); textSize = 30f; setTypeface(null, Typeface.BOLD); maxLines = 2 }
        heroSub = TextView(this).apply {
            setTextColor(0xFFE8EAED.toInt()); textSize = 13f; maxLines = 2
            setBackgroundColor(0x66000000); setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        val heroActs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val btnWatch = TvApps.pillBtn(this, "▶  Watch", true) { heroItems().getOrNull(heroIdx)?.let { TvOverlays.showDetail(this, it) { renderContent() } } }
        heroLib = TvApps.pillBtn(this, "+ Library", false) {
            heroItems().getOrNull(heroIdx)?.let { m ->
                val add = TvOverlays.toggleLib(this, m.id)
                Toast.makeText(this, if (add) "Saved to Library" else "Removed from Library", Toast.LENGTH_SHORT).show()
                heroLib.text = if (add) "✓ Library" else "+ Library"
            }
        }
        dotsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heroActs.addView(btnWatch, lpWrap(dp(8)))
        heroActs.addView(heroLib, lpWrap(dp(8)))
        heroActs.addView(TvApps.pillBtn(this, "‹", false) { stepHero(-1) }, lpWrap(dp(6)))
        heroActs.addView(TvApps.pillBtn(this, "›", false) { stepHero(1) }, lpWrap(dp(12)))
        heroActs.addView(dotsBox)

        heroBox.addView(heroSrc, lpWrap(0, dp(6)))
        heroBox.addView(heroTitle, lpWrap(0, dp(6)))
        heroBox.addView(heroSub, lpWrap(0, dp(10)))
        heroBox.addView(heroActs)
        page.addView(heroBox)

        contentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(contentBox)

        searchBox = TvOverlays.buildSearch(this, { currentCatalog() }, { m -> TvOverlays.showDetail(this, m) { renderContent() } }, { renderContent() })
        root.addView(searchBox, FrameLayout.LayoutParams(-1, -1))

        remoteBox = TvOverlays.buildRemote(this, { tabBtns["For you"] }) {
            remoteBox.visibility = View.GONE
            prefs.edit().putBoolean("remote", false).apply()
        }
        remoteBox.visibility = if (prefs.getBoolean("remote", false)) View.VISIBLE else View.GONE
        root.addView(remoteBox, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(14), dp(14), dp(14), dp(14))
        })

        setContentView(root)
        selectTab("For you")
        tabBtns["For you"]?.requestFocus()
        TvApps.prefetchApps(this) { if (!isFinishing) renderContent() }
        TvData.fetchTvMaze { if (!isFinishing) renderContent() }
    }

    override fun onResume() {
        super.onResume()
        h.removeCallbacks(heroTick)
        h.postDelayed(heroTick, 7000)
        TvApps.prefetchApps(this) { if (!isFinishing && ::contentBox.isInitialized) renderContent() }
    }
    override fun onPause() { super.onPause(); h.removeCallbacks(heroTick) }

    private fun lpWrap(mr: Int = 0, mb: Int = 0) = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = mr; bottomMargin = mb }
    private fun currentCatalog() = TvData.catalog(prefs.getBoolean("kids", false))

    private fun selectTab(name: String) {
        tab = name; heroIdx = 0
        tabBtns.forEach { (k, tv) ->
            val sel = k == name
            tv.setTextColor(if (sel) 0xFF121212.toInt() else 0xFFE8EAED.toInt())
            tv.background = TvApps.pillBg(this, if (sel) 0xFFE8EAED.toInt() else 0x22FFFFFF, if (sel) 0xFF82B1FF.toInt() else Color.WHITE)
        }
        renderContent()
    }

    private fun heroItems(): List<Media> = when (tab) {
        "Movies" -> currentCatalog().filter { it.kind == "movie" }.take(5)
        "Shows" -> currentCatalog().filter { it.kind == "show" }.take(5)
        else -> currentCatalog().take(5)
    }.ifEmpty { TvData.baseMedia.take(3) }

    private fun stepHero(d: Int) {
        val hl = heroItems(); if (hl.isEmpty()) return
        heroIdx = (heroIdx + d + hl.size) % hl.size
        bindHero(hl[heroIdx], hl.size)
    }

    private fun bindHero(m: Media, total: Int) {
        val dp = { v: Int -> TvApps.dp(this, v) }
        heroSrc.text = "▶  ${m.src}"
        heroTitle.text = m.title
        heroSub.text = m.sub
        heroLib.text = if (TvOverlays.inLib(this, m.id)) "✓ Library" else "+ Library"
        TvData.loadImg(heroBg, m.img, m.title, m.src, m.hex, true)
        dotsBox.removeAllViews()
        for (i in 0 until total) {
            val sz = dp(if (i == heroIdx) 8 else 6)
            dotsBox.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == heroIdx) Color.WHITE else 0x66FFFFFF) }
            }, LinearLayout.LayoutParams(sz, sz).apply { marginEnd = dp(6) })
        }
    }

    private fun renderContent() {
        val dp = { v: Int -> TvApps.dp(this, v) }
        contentBox.removeAllViews()
        val all = currentCatalog()

        when (tab) {
            "For you", "Movies", "Shows" -> {
                heroBox.visibility = View.VISIBLE
                val hl = heroItems()
                if (hl.isNotEmpty()) bindHero(hl[heroIdx.coerceIn(0, hl.lastIndex)], hl.size)
                val row1 = when (tab) {
                    "Movies" -> "Recommended Movies" to all.filter { it.kind == "movie" }
                    "Shows" -> "Top Web Series & Shows" to all.filter { it.kind == "show" }
                    else -> "Top picks for you" to all.drop(1).take(12).ifEmpty { all }
                }
                addMediaRow(row1.first, row1.second)
                addAppsRow("Your apps", TvApps.loadRow(this))
                val live = TvData.liveSnapshot()
                val row2 = if (tab == "Movies") "Action & Sci-Fi" to all.filter { it.kind == "movie" }.reversed()
                else "Live Trending Shows" to (live.ifEmpty { all.reversed() }).take(14)
                addMediaRow(row2.first, row2.second)
            }
            "Apps" -> {
                heroBox.visibility = View.GONE
                val apps = TvApps.loadInstalled(this)
                addHeader("Installed Apps (Long-press to Pin)")
                val cols = 5
                val grid = GridLayout(this).apply { columnCount = cols; setPadding(dp(20), 0, dp(20), 0) }
                val cellW = (resources.displayMetrics.widthPixels - dp(40)) / cols - dp(12)
                apps.forEach { app ->
                    val cell = TvCards.makeGridAppCell(this, app, { TvApps.launch(this, it) }, { TvApps.showMenu(this, it) { renderContent() } })
                    grid.addView(cell, GridLayout.LayoutParams().apply { width = cellW; setMargins(dp(6), dp(6), dp(6), dp(6)) })
                }
                contentBox.addView(grid)
            }
            else -> {
                heroBox.visibility = View.GONE
                val saved = all.filter { TvOverlays.inLib(this, it.id) }
                addHeader("Your Library & Watchlist")
                if (saved.isEmpty()) {
                    contentBox.addView(TextView(this).apply {
                        text = "Your Library is empty.\nOpen any movie or show and tap '+ Library'."
                        setTextColor(0xFFB8BDC8.toInt()); textSize = 15f; gravity = Gravity.CENTER; setPadding(dp(24), dp(40), dp(24), dp(40))
                    })
                } else addMediaRow("Saved Titles", saved)
            }
        }
    }

    private fun addHeader(title: String) {
        val dp = { v: Int -> TvApps.dp(this, v) }
        contentBox.addView(TextView(this).apply {
            text = title; setTextColor(0xFFE8EAED.toInt()); textSize = 14f; setTypeface(null, Typeface.BOLD)
            setBackgroundColor(0x55000000); setPadding(dp(10), dp(4), dp(10), dp(4))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(24); topMargin = dp(12); bottomMargin = dp(8) })
    }

    private fun addMediaRow(title: String, items: List<Media>) {
        val dp = { v: Int -> TvApps.dp(this, v) }
        addHeader(title)
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), dp(4), dp(24), dp(6)) }
        items.forEach { m ->
            row.addView(TvCards.makeMediaCard(this, m) { TvOverlays.showDetail(this, it) { renderContent() } },
                LinearLayout.LayoutParams(dp(178), dp(100)).apply { marginEnd = dp(12) })
        }
        hs.addView(row); contentBox.addView(hs)
    }

    private fun addAppsRow(title: String, apps: List<AppEntry>) {
        val dp = { v: Int -> TvApps.dp(this, v) }
        addHeader(title)
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), dp(4), dp(24), dp(6)) }
        apps.forEach { a -> row.addView(TvCards.makeAppCircle(this, a, { TvApps.launch(this, it) }, { TvApps.showMenu(this, it) { renderContent() } }), lpWrap(dp(12))) }
        hs.addView(row); contentBox.addView(hs)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (::searchBox.isInitialized && searchBox.visibility == View.VISIBLE) { searchBox.visibility = View.GONE; return true }
            if (tab != "For you") { selectTab("For you"); tabBtns["For you"]?.requestFocus(); return true }
            val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            if (home?.activityInfo?.packageName == packageName) return true
            if (System.currentTimeMillis() - lastBack < 1800) finish() else {
                lastBack = System.currentTimeMillis()
                Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
