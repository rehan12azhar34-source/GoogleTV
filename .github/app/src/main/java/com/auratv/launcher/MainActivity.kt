package com.auratv.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var mediaRepo: MediaRepository
    private lateinit var appRepo: AppRepository
    private val prefs by lazy { getSharedPreferences("aura", MODE_PRIVATE) }
    private val h = Handler(Looper.getMainLooper())

    private var currentTab = TabType.FOR_YOU
    private var heroList = emptyList<MediaItem>()
    private var heroIdx = 0
    private var lastBack = 0L

    private lateinit var heroBg: ImageView
    private lateinit var heroContainer: View
    private lateinit var heroSrc: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var btnBookmark: TextView
    private lateinit var dotsLayout: LinearLayout
    private lateinit var homeSections: View
    private lateinit var gridContainer: View
    private lateinit var txtRow1: TextView
    private lateinit var txtRow2: TextView
    private lateinit var txtGridTitle: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var rvGrid: RecyclerView
    private lateinit var searchOverlay: View
    private lateinit var remoteOverlay: View
    private lateinit var tabs: List<Pair<TabType, TextView>>

    private lateinit var row1Ad: MediaCardAdapter
    private lateinit var row2Ad: MediaCardAdapter
    private lateinit var appsAd: AppCircleAdapter
    private lateinit var gridAppsAd: AppGridAdapter
    private lateinit var gridMediaAd: MediaCardAdapter
    private lateinit var sMediaAd: MediaCardAdapter
    private lateinit var sAppsAd: AppCircleAdapter

    private val heroTick = object : Runnable {
        override fun run() {
            if (prefs.getBoolean("rot", true) && heroList.size > 1 && currentTab in listOf(TabType.FOR_YOU, TabType.MOVIES, TabType.SHOWS)) {
                heroIdx = (heroIdx + 1) % heroList.size
                bindHero(heroList[heroIdx])
            }
            h.postDelayed(this, 7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            prefs.edit().putString("last_crash", e.stackTraceToString().take(3000)).commit()
            prev?.uncaughtException(t, e)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs.getString("last_crash", null)?.let { err ->
            prefs.edit().remove("last_crash").apply()
            AlertDialog.Builder(this).setTitle("Previous Crash Log").setMessage(err).setPositiveButton("OK", null).show()
        }

        mediaRepo = MediaRepository(this)
        appRepo = AppRepository(this)

        heroBg = findViewById(R.id.heroBackdrop)
        heroContainer = findViewById(R.id.heroContainer)
        heroSrc = findViewById(R.id.heroSourceBadge)
        heroTitle = findViewById(R.id.heroTitle)
        heroSub = findViewById(R.id.heroSubtitle)
        btnBookmark = findViewById(R.id.btnHeroBookmark)
        dotsLayout = findViewById(R.id.heroDotsLayout)
        homeSections = findViewById(R.id.homeSectionsContainer)
        gridContainer = findViewById(R.id.appsGridContainer)
        txtRow1 = findViewById(R.id.txtRow1Title)
        txtRow2 = findViewById(R.id.txtRow2Title)
        txtGridTitle = findViewById(R.id.txtGridTitle)
        txtEmpty = findViewById(R.id.txtEmptyState)
        rvGrid = findViewById(R.id.rvGrid)
        searchOverlay = findViewById(R.id.searchOverlay)
        remoteOverlay = findViewById(R.id.remoteOverlay)
        remoteOverlay.visibility = if (prefs.getBoolean("remote", false)) View.VISIBLE else View.GONE

        tabs = listOf(
            TabType.FOR_YOU to findViewById(R.id.tabForYou),
            TabType.MOVIES to findViewById(R.id.tabMovies),
            TabType.SHOWS to findViewById(R.id.tabShows),
            TabType.APPS to findViewById(R.id.tabApps),
            TabType.LIBRARY to findViewById(R.id.tabLibrary)
        )
        tabs.forEach { (type, v) -> v.setOnClickListener { selectTab(type) } }

        row1Ad = MediaCardAdapter(emptyList()) { LauncherDialogs.showMediaDetail(this, it, mediaRepo) { refreshUi() } }
        row2Ad = MediaCardAdapter(emptyList()) { LauncherDialogs.showMediaDetail(this, it, mediaRepo) { refreshUi() } }
        appsAd = AppCircleAdapter(emptyList(), { appRepo.launch(it) }, { LauncherDialogs.showAppMenu(this, it, appRepo) { refreshUi() } })
        gridAppsAd = AppGridAdapter(emptyList(), { appRepo.launch(it) }, { LauncherDialogs.showAppMenu(this, it, appRepo) { refreshUi() } })
        gridMediaAd = MediaCardAdapter(emptyList()) { LauncherDialogs.showMediaDetail(this, it, mediaRepo) { refreshUi() } }
        sMediaAd = MediaCardAdapter(emptyList()) { LauncherDialogs.showMediaDetail(this, it, mediaRepo) { refreshUi() } }
        sAppsAd = AppCircleAdapter(emptyList(), { appRepo.launch(it) }, { LauncherDialogs.showAppMenu(this, it, appRepo) { refreshUi() } })

        findViewById<RecyclerView>(R.id.rvRow1).apply { layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false); adapter = row1Ad }
        findViewById<RecyclerView>(R.id.rvAppsRow).apply { layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false); adapter = appsAd }
        findViewById<RecyclerView>(R.id.rvRow2).apply { layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false); adapter = row2Ad }
        findViewById<RecyclerView>(R.id.rvSearchMedia).apply { layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false); adapter = sMediaAd }
        findViewById<RecyclerView>(R.id.rvSearchApps).apply { layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false); adapter = sAppsAd }

        findViewById<View>(R.id.btnProfile).setOnClickListener { LauncherDialogs.showProfile(this, mediaRepo) { refreshUi() } }
        findViewById<View>(R.id.txtBrand).setOnClickListener { LauncherDialogs.showAbout(this) }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            LauncherDialogs.showSettings(this, appRepo, remoteOverlay.visibility == View.VISIBLE, prefs.getBoolean("rot", true)) { rem, rot ->
                remoteOverlay.visibility = if (rem) View.VISIBLE else View.GONE
                prefs.edit().putBoolean("remote", rem).putBoolean("rot", rot).apply()
                mediaRepo.fetchLive { runOnUiThread { if (!isFinishing) refreshUi() } }
                refreshUi()
            }
        }
        findViewById<View>(R.id.btnHeroWatch).setOnClickListener { heroList.getOrNull(heroIdx)?.let { LauncherDialogs.showMediaDetail(this, it, mediaRepo) { refreshUi() } } }
        btnBookmark.setOnClickListener {
            heroList.getOrNull(heroIdx)?.let {
                val add = mediaRepo.toggleLibrary(it.id)
                btnBookmark.text = if (add) "✓ Library" else "+ Library"
                Toast.makeText(this, if (add) "Saved to Library" else "Removed from Library", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.btnHeroPrev).setOnClickListener { if (heroList.isNotEmpty()) { heroIdx = (heroIdx - 1 + heroList.size) % heroList.size; bindHero(heroList[heroIdx]) } }
        findViewById<View>(R.id.btnHeroNext).setOnClickListener { if (heroList.isNotEmpty()) { heroIdx = (heroIdx + 1) % heroList.size; bindHero(heroList[heroIdx]) } }

        setupSearchAndRemote()
        selectTab(TabType.FOR_YOU)
        tabs.first().second.requestFocus()

        appRepo.prefetch { if (!isFinishing) refreshUi() }
        mediaRepo.fetchLive { runOnUiThread { if (!isFinishing) refreshUi() } }
    }

    private fun setupSearchAndRemote() {
        val edt = findViewById<EditText>(R.id.edtSearch)
        fun runS(q: String) {
            sMediaAd.submitList(mediaRepo.search(q))
            val s = q.trim().lowercase()
            sAppsAd.submitList(appRepo.getAllInstalled().filter { s.isEmpty() || it.label.lowercase().contains(s) }.take(10))
        }
        findViewById<View>(R.id.btnSearch).setOnClickListener { searchOverlay.visibility = View.VISIBLE; runS(edt.text.toString()); edt.requestFocus() }
        findViewById<View>(R.id.btnCloseSearch).setOnClickListener { searchOverlay.visibility = View.GONE }
        edt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { runS(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })
        fun send(k: Int) { if (currentFocus == null) tabs.first().second.requestFocus(); dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k)) }
        findViewById<View>(R.id.remUp).setOnClickListener { send(KeyEvent.KEYCODE_DPAD_UP) }
        findViewById<View>(R.id.remDown).setOnClickListener { send(KeyEvent.KEYCODE_DPAD_DOWN) }
        findViewById<View>(R.id.remLeft).setOnClickListener { send(KeyEvent.KEYCODE_DPAD_LEFT) }
        findViewById<View>(R.id.remRight).setOnClickListener { send(KeyEvent.KEYCODE_DPAD_RIGHT) }
        findViewById<View>(R.id.remOk).setOnClickListener { send(KeyEvent.KEYCODE_DPAD_CENTER) }
        findViewById<View>(R.id.remBack).setOnClickListener { send(KeyEvent.KEYCODE_BACK) }
        findViewById<View>(R.id.remClose).setOnClickListener { remoteOverlay.visibility = View.GONE; prefs.edit().putBoolean("remote", false).apply() }
    }

    override fun onResume() { super.onResume(); h.removeCallbacks(heroTick); h.postDelayed(heroTick, 7000); appRepo.prefetch { if (!isFinishing) refreshUi() } }
    override fun onPause() { super.onPause(); h.removeCallbacks(heroTick) }

    private fun selectTab(tab: TabType) {
        currentTab = tab; heroIdx = 0
        tabs.forEach { (t, v) -> v.isSelected = t == tab; v.setTextColor(if (t == tab) 0xFF121212.toInt() else 0xFFE8EAED.toInt()) }
        refreshUi()
    }

    private fun bindHero(m: MediaItem) {
        heroSrc.text = "▶  ${m.sourceName}"
        heroTitle.text = m.title
        heroSub.text = m.subtitle
        btnBookmark.text = if (mediaRepo.isInLibrary(m.id)) "✓ Library" else "+ Library"
        ImageLoader.load(heroBg, m.imageUrl, m.title, m.sourceName, m.accentHex, true)
        dotsLayout.removeAllViews()
        for (i in heroList.indices) {
            val sz = if (i == heroIdx) 14 else 10
            dotsLayout.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == heroIdx) Color.WHITE else 0x66FFFFFF) }
            }, LinearLayout.LayoutParams(sz, sz).apply { marginEnd = 8 })
        }
    }

    private fun refreshUi() {
        when (currentTab) {
            TabType.FOR_YOU, TabType.MOVIES, TabType.SHOWS -> {
                heroContainer.visibility = View.VISIBLE; homeSections.visibility = View.VISIBLE; gridContainer.visibility = View.GONE
                heroList = mediaRepo.heroItems(currentTab)
                if (heroList.isNotEmpty()) bindHero(heroList[heroIdx.coerceIn(0, heroList.lastIndex)])
                val (t1, l1) = mediaRepo.row1(currentTab)
                val (t2, l2) = mediaRepo.row2(currentTab)
                txtRow1.text = t1; txtRow2.text = t2
                row1Ad.submitList(l1); appsAd.submitList(appRepo.getHomeRow()); row2Ad.submitList(l2)
            }
            TabType.APPS -> {
                heroContainer.visibility = View.GONE; homeSections.visibility = View.GONE; gridContainer.visibility = View.VISIBLE
                txtGridTitle.text = "Installed Apps (Long-press to Pin)"
                val list = appRepo.getAllInstalled()
                rvGrid.layoutManager = GridLayoutManager(this, 5); rvGrid.adapter = gridAppsAd; gridAppsAd.submitList(list)
                txtEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE; txtEmpty.text = "Loading apps..."
            }
            TabType.LIBRARY -> {
                heroContainer.visibility = View.GONE; homeSections.visibility = View.GONE; gridContainer.visibility = View.VISIBLE
                txtGridTitle.text = "Your Library & Watchlist"
                val (_, saved) = mediaRepo.row1(TabType.LIBRARY)
                rvGrid.layoutManager = GridLayoutManager(this, 4); rvGrid.adapter = gridMediaAd; gridMediaAd.submitList(saved)
                txtEmpty.visibility = if (saved.isEmpty()) View.VISIBLE else View.GONE
                txtEmpty.text = "Your Library is empty. Open any title and tap '+ Library'."
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (searchOverlay.visibility == View.VISIBLE) { searchOverlay.visibility = View.GONE; return true }
            if (currentTab != TabType.FOR_YOU) { selectTab(TabType.FOR_YOU); tabs.first().second.requestFocus(); return true }
            val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            if (home?.activityInfo?.packageName == packageName) return true
            if (System.currentTimeMillis() - lastBack < 1800) finish() else {
                lastBack = System.currentTimeMillis(); Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
