package com.auratv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*

object TvOverlays {
    fun inLib(c: Context, id: String): Boolean {
        val prefs = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        return (prefs.getStringSet("lib", emptySet()) ?: emptySet()).contains(id)
    }

    fun toggleLib(c: Context, id: String): Boolean {
        val prefs = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        val s = (prefs.getStringSet("lib", emptySet()) ?: emptySet()).toMutableSet()
        val add = if (s.contains(id)) { s.remove(id); false } else { s.add(id); true }
        prefs.edit().putStringSet("lib", s).apply()
        return add
    }

    fun showDetail(c: Context, m: Media, onRefresh: () -> Unit) {
        AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(m.title)
            .setMessage("${m.src} • ★ ${m.rating} • ${m.genre}\n\n${m.desc}")
            .setPositiveButton("▶ Watch / Trailer") { _, _ ->
                val pm = c.packageManager
                val i = m.pkg?.let { pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it) }
                if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(m.title + " trailer")}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .setNeutralButton(if (inLib(c, m.id)) "Remove from Library" else "+ Add to Library") { _, _ ->
                toggleLib(c, m.id); onRefresh()
            }
            .setNegativeButton("Close", null).show()
    }

    fun buildSearch(
        act: Activity,
        getCatalog: () -> List<Media>,
        onMediaClick: (Media) -> Unit,
        onRefresh: () -> Unit
    ): LinearLayout {
        val dp = { v: Int -> TvApps.dp(act, v) }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xF20B0C10.toInt())
            setPadding(dp(24), dp(24), dp(24), dp(24)); visibility = View.GONE
        }
        val bar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val edt = EditText(act).apply {
            hint = "Search movies, shows, apps..."; setHintTextColor(0xFFAAAAAA.toInt())
            setTextColor(Color.WHITE); setBackgroundColor(0x28FFFFFF); setPadding(dp(14), dp(10), dp(14), dp(10)); isSingleLine = true
        }
        val resBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun runSearch(q: String) {
            resBox.removeAllViews()
            val s = q.trim().lowercase()
            val mHits = getCatalog().filter { s.isEmpty() || it.title.lowercase().contains(s) || it.genre.lowercase().contains(s) }.take(10)
            val aHits = TvApps.loadInstalled(act).filter { s.isEmpty() || it.name.lowercase().contains(s) }.take(10)
            val hs1 = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
            val r1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            mHits.forEach { r1.addView(TvCards.makeMediaCard(act, it, onMediaClick), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) }) }
            hs1.addView(r1); resBox.addView(hs1, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            val hs2 = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
            val r2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            aHits.forEach { r2.addView(TvCards.makeAppCircle(act, it, { a -> TvApps.launch(act, a) }, { a -> TvApps.showMenu(act, a, onRefresh) }), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) }) }
            hs2.addView(r2); resBox.addView(hs2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        edt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { runSearch(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })
        bar.addView(edt, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        bar.addView(TvApps.pillBtn(act, "Close", false) { box.visibility = View.GONE })
        box.addView(bar); box.addView(resBox)
        box.tag = Runnable { runSearch(edt.text.toString()); edt.requestFocus() }
        return box
    }

    fun buildRemote(act: Activity, getFallbackFocus: () -> View?, onClose: () -> Unit): LinearLayout {
        val dp = { v: Int -> TvApps.dp(act, v) }
        fun key(k: Int) {
            if (act.currentFocus == null) getFallbackFocus()?.requestFocus()
            act.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k))
            act.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k))
        }
        fun btn(t: String, bg: Int, fg: Int, k: () -> Unit) = TextView(act).apply {
            text = t; textSize = 12f; setTextColor(fg); setBackgroundColor(bg); gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { k() }
        }
        return LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xCC16181F.toInt()); setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(btn("▲", 0x33FFFFFF, Color.WHITE) { key(KeyEvent.KEYCODE_DPAD_UP) })
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(btn("◀", 0x33FFFFFF, Color.WHITE) { key(KeyEvent.KEYCODE_DPAD_LEFT) })
                addView(btn("OK", 0xFFE8EAED.toInt(), Color.BLACK) { key(KeyEvent.KEYCODE_DPAD_CENTER) })
                addView(btn("▶", 0x33FFFFFF, Color.WHITE) { key(KeyEvent.KEYCODE_DPAD_RIGHT) })
            })
            addView(btn("▼", 0x33FFFFFF, Color.WHITE) { key(KeyEvent.KEYCODE_DPAD_DOWN) })
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(btn("Back", 0x22FFFFFF, Color.WHITE) { key(KeyEvent.KEYCODE_BACK) })
                addView(btn("✕", 0x66FF5252, Color.WHITE) { onClose() })
            })
        }
    }

    fun showProfile(c: Context, onRefresh: () -> Unit) {
        val dp = { v: Int -> TvApps.dp(c, v) }
        val prefs = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), 0) }
        val edt = EditText(c).apply { setText(prefs.getString("prof", "Viewer")); hint = "Profile Name" }
        val kids = CheckBox(c).apply { text = "Kids Mode (hide mature)"; isChecked = prefs.getBoolean("kids", false) }
        box.addView(edt); box.addView(kids)
        AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert).setTitle("Profile").setView(box)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("prof", edt.text.toString().ifBlank { "Viewer" }).putBoolean("kids", kids.isChecked).apply()
                onRefresh()
            }.setNegativeButton("Cancel", null).show()
    }

    fun showSettings(c: Context, remoteVisible: Boolean, onApply: (Boolean) -> Unit) {
        val dp = { v: Int -> TvApps.dp(c, v) }
        val prefs = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), 0) }
        val cRem = CheckBox(c).apply { text = "On-Screen TV Remote (Mobile test)"; isChecked = remoteVisible }
        val cRot = CheckBox(c).apply { text = "Auto-rotate Hero Banner"; isChecked = prefs.getBoolean("rot", true) }
        val cDemo = CheckBox(c).apply { text = "Show Streaming Shortcuts"; isChecked = prefs.getBoolean("demo_apps", true) }
        box.addView(cRem); box.addView(cRot); box.addView(cDemo)
        AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert).setTitle("Settings").setView(box)
            .setPositiveButton("Apply") { _, _ ->
                prefs.edit().putBoolean("remote", cRem.isChecked).putBoolean("rot", cRot.isChecked).putBoolean("demo_apps", cDemo.isChecked).apply()
                onApply(cRem.isChecked)
            }
            .setNeutralButton("Set Default TV Home") { _, _ ->
                runCatching { c.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .setNegativeButton("Android Settings") { _, _ ->
                c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.show()
    }

    fun showAbout(c: Context) {
        AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert).setTitle("Aura TV Launcher")
            .setMessage("• D-pad remote ready for Android TV.\n• Enable 'On-Screen TV Remote' in Settings on Mobile.\n• Live shows fetched from TVMaze.")
            .setPositiveButton("OK", null).show()
    }
}
