package com.auratv.launcher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

object TvApps {
    private val exec = Executors.newSingleThreadExecutor()
    private val mainH = Handler(Looper.getMainLooper())
    @Volatile private var cachedApps: List<AppEntry>? = null

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun pillBg(c: Context, solid: Int, focusStroke: Int = Color.WHITE) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            cornerRadius = dp(c, 20).toFloat(); setColor(solid); setStroke(dp(c, 2), focusStroke)
        })
        addState(intArrayOf(), GradientDrawable().apply {
            cornerRadius = dp(c, 20).toFloat(); setColor(solid)
        })
    }

    fun pillBtn(c: Context, label: String, light: Boolean, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 13f; setTypeface(null, Typeface.BOLD)
        setTextColor(if (light) 0xFF121212.toInt() else Color.WHITE)
        background = pillBg(c, if (light) 0xFFE8EAED.toInt() else 0x2AFFFFFF, if (light) 0xFF82B1FF.toInt() else Color.WHITE)
        setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7))
        isFocusable = true; isClickable = true
        setOnClickListener { onClick() }
    }

    fun loadInstalled(c: Context): List<AppEntry> = cachedApps ?: emptyList()

    fun prefetchApps(c: Context, onReady: () -> Unit) {
        val appCtx = c.applicationContext
        exec.execute {
            val pm = appCtx.packageManager
            val prefs = appCtx.getSharedPreferences("aura", Context.MODE_PRIVATE)
            val pins = prefs.getStringSet("pins", emptySet()) ?: emptySet()
            val seen = HashSet<String>()
            val out = mutableListOf<AppEntry>()
            listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { cat ->
                val list = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
                list.forEach { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@forEach
                    if (pkg != appCtx.packageName && seen.add(pkg)) {
                        val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                        val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                        out.add(AppEntry(label, pkg, icon, pins.contains(pkg), 0xFF23262F.toInt()))
                    }
                }
            }
            cachedApps = out.sortedWith(compareByDescending<AppEntry> { it.pin }.thenBy { it.name.lowercase() })
            mainH.post { onReady() }
        }
    }

    fun loadRow(c: Context): List<AppEntry> {
        val prefs = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        val inst = loadInstalled(c)
        val byPkg = inst.associateBy { it.pkg }
        val out = inst.filter { it.pin }.toMutableList()
        val added = out.map { it.pkg }.toHashSet()
        val known = listOf(
            Triple("Netflix", "com.netflix.mediaclient", 0xFFFFFFFF.toInt()),
            Triple("YouTube", "com.google.android.youtube", 0xFFFFFFFF.toInt()),
            Triple("Prime Video", "com.amazon.avod.thirdpartyclient", 0xFF00A8E1.toInt()),
            Triple("Hotstar", "in.startv.hotstar", 0xFF0A1A4A.toInt()),
            Triple("MX Player", "com.mxtech.videoplayer.ad", 0xFF147EFB.toInt()),
            Triple("ZEE5", "com.graymatrix.did", 0xFF150E24.toInt()),
            Triple("SonyLIV", "com.sonyliv", 0xFF141414.toInt()),
            Triple("Spotify", "com.spotify.music", 0xFF1DB954.toInt())
        )
        for ((name, pkg, col) in known) {
            val m = byPkg[pkg]
            if (m != null && added.add(m.pkg)) out.add(m.copy(col = col))
            else if (prefs.getBoolean("demo_apps", true) && added.add(pkg)) out.add(AppEntry(name, pkg, null, false, col))
        }
        for (a in inst) if (out.size < 14 && added.add(a.pkg)) out.add(a)
        return out
    }

    fun launch(c: Context, app: AppEntry) {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(app.pkg) ?: pm.getLaunchIntentForPackage(app.pkg)
        if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else {
            Toast.makeText(c, "${app.name} not installed — opening Play Store", Toast.LENGTH_SHORT).show()
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun showMenu(c: Context, app: AppEntry, onRefresh: () -> Unit) {
        val prefs = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert).setTitle(app.name)
            .setItems(arrayOf("Open", if (app.pin) "Unpin" else "Pin to Your Apps", "App Info")) { _, w ->
                when (w) {
                    0 -> launch(c, app)
                    1 -> {
                        val s = (prefs.getStringSet("pins", emptySet()) ?: emptySet()).toMutableSet()
                        if (!s.remove(app.pkg)) s.add(app.pkg)
                        prefs.edit().putStringSet("pins", s).apply()
                        prefetchApps(c, onRefresh)
                    }
                    2 -> runCatching {
                        c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }.show()
    }
}
