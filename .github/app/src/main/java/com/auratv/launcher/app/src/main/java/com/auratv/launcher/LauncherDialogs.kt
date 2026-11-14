package com.auratv.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object LauncherDialogs {
    fun showMediaDetail(c: Context, m: MediaItem, mediaRepo: MediaRepository, onRefresh: () -> Unit) {
        val inLib = mediaRepo.isInLibrary(m.id)
        AlertDialog.Builder(c)
            .setTitle(m.title)
            .setMessage("${m.sourceName}  •  ★ ${m.rating}  •  ${m.genre}\n\n${m.description}")
            .setPositiveButton("▶ Watch / Trailer") { _, _ ->
                val i = m.targetPackage?.let { c.packageManager.getLeanbackLaunchIntentForPackage(it) ?: c.packageManager.getLaunchIntentForPackage(it) }
                if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(m.title + " trailer")}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .setNeutralButton(if (inLib) "Remove from Library" else "+ Add to Library") { _, _ ->
                val added = mediaRepo.toggleLibrary(m.id)
                Toast.makeText(c, if (added) "Added to Library" else "Removed from Library", Toast.LENGTH_SHORT).show()
                onRefresh()
            }
            .setNegativeButton("Close", null).show()
    }

    fun showAppMenu(c: Context, app: AppEntry, appRepo: AppRepository, onRefresh: () -> Unit) {
        AlertDialog.Builder(c).setTitle(app.label)
            .setItems(arrayOf("Open", if (app.isPinned) "Unpin from Your Apps" else "Pin to Your Apps", "App Info")) { _, w ->
                when (w) {
                    0 -> appRepo.launch(app)
                    1 -> { appRepo.togglePin(app.packageName); appRepo.prefetch(onRefresh) }
                    2 -> appRepo.openAppInfo(app.packageName)
                }
            }.show()
    }

    fun showProfile(c: Context, mediaRepo: MediaRepository, onRefresh: () -> Unit) {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8) }
        val edt = EditText(c).apply { setText(mediaRepo.getProfileName()); hint = "Profile Name" }
        val kids = CheckBox(c).apply { text = "Kids Mode (hide mature titles)"; isChecked = mediaRepo.isKidsMode() }
        box.addView(edt); box.addView(kids)
        AlertDialog.Builder(c).setTitle("Profile: ${mediaRepo.getProfileName()}").setView(box)
            .setPositiveButton("Save") { _, _ ->
                mediaRepo.setProfileName(edt.text.toString()); mediaRepo.setKidsMode(kids.isChecked); onRefresh()
            }.setNegativeButton("Cancel", null).show()
    }

    fun showSettings(c: Context, appRepo: AppRepository, remoteOn: Boolean, autoRot: Boolean, onApply: (Boolean, Boolean) -> Unit) {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8) }
        val cRem = CheckBox(c).apply { text = "On-Screen TV Remote (Mobile test)"; isChecked = remoteOn }
        val cRot = CheckBox(c).apply { text = "Auto-rotate Hero Banner"; isChecked = autoRot }
        val cDemo = CheckBox(c).apply { text = "Show Streaming Shortcuts"; isChecked = appRepo.showDemoShortcuts() }
        box.addView(cRem); box.addView(cRot); box.addView(cDemo)
        AlertDialog.Builder(c).setTitle("Settings").setView(box)
            .setPositiveButton("Apply") { _, _ ->
                appRepo.setShowDemoShortcuts(cDemo.isChecked)
                onApply(cRem.isChecked, cRot.isChecked)
            }
            .setNeutralButton("Set Default TV Home") { _, _ ->
                runCatching { c.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .setNegativeButton("Android Settings") { _, _ ->
                c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.show()
    }

    fun showAbout(c: Context) {
        AlertDialog.Builder(c).setTitle("Aura TV Launcher")
            .setMessage("• D-pad remote ready for Android TV.\n• Enable 'On-Screen TV Remote' in Settings to test remote buttons on Mobile.\n• Live shows fetched from TVMaze.")
            .setPositiveButton("OK", null).show()
    }
}
