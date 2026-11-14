package com.auratv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import java.util.concurrent.Executors

class AppRepository(private val context: Context) {
    private val appCtx = context.applicationContext
    private val prefs = appCtx.getSharedPreferences("aura", Context.MODE_PRIVATE)
    private val exec = Executors.newSingleThreadExecutor()
    private val mainH = Handler(Looper.getMainLooper())
    @Volatile private var cached: List<AppEntry> = emptyList()

    fun showDemoShortcuts() = prefs.getBoolean("demo_apps", true)
    fun setShowDemoShortcuts(v: Boolean) = prefs.edit().putBoolean("demo_apps", v).apply()
    fun getAllInstalled() = cached

    fun prefetch(onReady: () -> Unit) = exec.execute {
        val pm = appCtx.packageManager
        val pins = prefs.getStringSet("pins", emptySet()) ?: emptySet()
        val seen = HashSet<String>()
        val out = mutableListOf<AppEntry>()
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { cat ->
            val list = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
            list.forEach { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@forEach
                if (pkg != appCtx.packageName && seen.add(pkg)) {
                    val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                    val safeIcon = safeDrawable(runCatching { ri.loadIcon(pm) }.getOrNull())
                    out.add(AppEntry(label, pkg, safeIcon, pins.contains(pkg), 0xFF23262F.toInt()))
                }
            }
        }
        cached = out.sortedWith(compareByDescending<AppEntry> { it.isPinned }.thenBy { it.label.lowercase() })
        mainH.post { onReady() }
    }

    private fun safeDrawable(d: Drawable?): Drawable? = if (d == null) null else runCatching {
        val sz = (48 * appCtx.resources.displayMetrics.density).toInt().coerceIn(48, 128)
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, sz, sz)
        d.draw(Canvas(bmp))
        BitmapDrawable(appCtx.resources, bmp)
    }.getOrNull()

    fun getHomeRow(): List<AppEntry> {
        val inst = cached
        val byPkg = inst.associateBy { it.packageName }
        val out = inst.filter { it.isPinned }.toMutableList()
        val added = out.map { it.packageName }.toHashSet()
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
            if (m != null && added.add(m.packageName)) out.add(m.copy(accentColor = col))
            else if (showDemoShortcuts() && added.add(pkg)) out.add(AppEntry(name, pkg, null, false, col))
        }
        for (a in inst) if (out.size < 14 && added.add(a.packageName)) out.add(a)
        return out
    }

    fun togglePin(pkg: String): Boolean {
        val s = (prefs.getStringSet("pins", emptySet()) ?: emptySet()).toMutableSet()
        val added = if (s.contains(pkg)) { s.remove(pkg); false } else { s.add(pkg); true }
        prefs.edit().putStringSet("pins", s).apply()
        return added
    }

    fun launch(app: AppEntry) {
        val pm = context.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(app.packageName) ?: pm.getLaunchIntentForPackage(app.packageName)
        if (i != null) context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else {
            Toast.makeText(context, "${app.label} not installed — opening Play Store", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openAppInfo(pkg: String) = runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
