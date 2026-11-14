package com.auratv.launcher

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MediaRepository(context: Context) {
    private val prefs = context.getSharedPreferences("aura", Context.MODE_PRIVATE)
    private val exec = Executors.newSingleThreadExecutor()
    private val liveItems = mutableListOf<MediaItem>()

    private val curated = listOf(
        MediaItem("aashram", "Aashram", "Trending  |  A corrupt spiritual guru's world of crime & exploitation", "Baba Nirala runs a sprawling ashram hiding crime and politics.", "MXPLAYER TV", "Crime • Drama", "7.4", MediaCategory.SHOW, null, "#9B621B", true, "com.mxtech.videoplayer.ad"),
        MediaItem("rocket", "Rocket Boys", "Critically Acclaimed  |  Pioneers of India's space & nuclear journey", "Story of Dr. Homi Bhabha and Dr. Vikram Sarabhai.", "SONYLIV", "Bio • Drama", "8.9", MediaCategory.SHOW, null, "#245C73", false, "com.sonyliv"),
        MediaItem("farzi", "Farzi", "Top Pick  |  An artist drawn into high-stakes counterfeiting", "A small-time artist designs the ultimate counterfeit currency note.", "PRIME VIDEO", "Crime • Thriller", "8.4", MediaCategory.SHOW, null, "#3A4E5C", true, "com.amazon.avod.thirdpartyclient"),
        MediaItem("scam", "Scam 2003", "Popular  |  The Telgi stamp paper empire", "Follows Abdul Karim Telgi behind a massive financial scam.", "SONYLIV", "Crime • Bio", "8.0", MediaCategory.SHOW, null, "#2D5B4C", false, "com.sonyliv"),
        MediaItem("samantar", "Samantar", "Mystery  |  What if someone already lived your life?", "Kumar Mahajan discovers a man whose past mirrors his future.", "MXPLAYER TV", "Mystery", "7.9", MediaCategory.SHOW, null, "#333A4F", false, "com.mxtech.videoplayer.ad"),
        MediaItem("queen", "Queen", "Drama  |  A journey of resilience and ambition", "Rise of a young school topper reshaping politics.", "MXPLAYER TV", "Drama", "7.8", MediaCategory.SHOW, null, "#7A2E3A", false, "com.mxtech.videoplayer.ad"),
        MediaItem("inter", "Interstellar", "Sci-Fi Classic  |  Mankind was born on Earth", "Explorers travel through a wormhole to save humanity.", "PRIME VIDEO", "Sci-Fi", "8.7", MediaCategory.MOVIE, null, "#1E3A5F", false, "com.amazon.avod.thirdpartyclient"),
        MediaItem("rrr", "RRR", "Blockbuster  |  Fire and Water unite", "Two revolutionaries fight colonial rule in 1920s India.", "NETFLIX", "Action", "7.8", MediaCategory.MOVIE, null, "#8C321A", false, "com.netflix.mediaclient"),
        MediaItem("spider", "Across the Spider-Verse", "Animation  |  One hero faces the multiverse", "Miles Morales meets Spider-People across dimensions.", "NETFLIX", "Animation", "8.6", MediaCategory.MOVIE, null, "#7A1C4B", false, "com.netflix.mediaclient"),
        MediaItem("fail", "12th Fail", "Top Rated  |  An inspiring true story of grit", "Based on IPS officer Manoj Kumar Sharma's journey.", "HOTSTAR", "Bio • Drama", "8.8", MediaCategory.MOVIE, null, "#3E5B3C", false, "in.startv.hotstar")
    )

    fun isKidsMode() = prefs.getBoolean("kids", false)
    fun setKidsMode(v: Boolean) = prefs.edit().putBoolean("kids", v).apply()
    fun getProfileName() = prefs.getString("prof", "Viewer") ?: "Viewer"
    fun setProfileName(s: String) = prefs.edit().putString("prof", s.ifBlank { "Viewer" }).apply()

    fun isInLibrary(id: String) = (prefs.getStringSet("lib", emptySet()) ?: emptySet()).contains(id)
    fun toggleLibrary(id: String): Boolean {
        val s = (prefs.getStringSet("lib", emptySet()) ?: emptySet()).toMutableSet()
        val add = if (s.contains(id)) { s.remove(id); false } else { s.add(id); true }
        prefs.edit().putStringSet("lib", s).apply()
        return add
    }

    fun catalog(): List<MediaItem> {
        val live = synchronized(liveItems) { liveItems.toList() }
        val all = (curated + live).distinctBy { it.id }
        return if (isKidsMode()) all.filter { !it.isMature } else all
    }

    fun heroItems(tab: TabType): List<MediaItem> = when (tab) {
        TabType.MOVIES -> catalog().filter { it.category == MediaCategory.MOVIE }.take(5)
        TabType.SHOWS -> catalog().filter { it.category == MediaCategory.SHOW }.take(5)
        else -> catalog().take(5)
    }.ifEmpty { curated.take(3) }

    fun row1(tab: TabType): Pair<String, List<MediaItem>> = when (tab) {
        TabType.MOVIES -> "Recommended Movies" to catalog().filter { it.category == MediaCategory.MOVIE }
        TabType.SHOWS -> "Top Web Series & Shows" to catalog().filter { it.category == MediaCategory.SHOW }
        TabType.LIBRARY -> "Your Library & Watchlist" to catalog().filter { isInLibrary(it.id) }
        else -> "Top picks for you" to catalog().drop(1).take(12).ifEmpty { catalog() }
    }

    fun row2(tab: TabType): Pair<String, List<MediaItem>> {
        val live = synchronized(liveItems) { liveItems.toList() }.let { if (isKidsMode()) it.filter { m -> !m.isMature } else it }
        return if (tab == TabType.MOVIES) "Action & Sci-Fi" to catalog().filter { it.category == MediaCategory.MOVIE }.reversed()
        else "Live Trending Shows" to (live.ifEmpty { catalog().reversed() }).take(14)
    }

    fun search(q: String): List<MediaItem> {
        val s = q.trim().lowercase()
        return catalog().filter { s.isEmpty() || it.title.lowercase().contains(s) || it.genre.lowercase().contains(s) }.take(12)
    }

    fun fetchLive(onDone: () -> Unit) = exec.execute {
        runCatching {
            val txt = (URL("https://api.tvmaze.com/shows?page=0").openConnection() as HttpURLConnection).run {
                connectTimeout = 5000; readTimeout = 7000; inputStream.bufferedReader().readText()
            }
            val arr = JSONArray(txt)
            val cols = listOf("#1D4B5F", "#5F2D3A", "#38522E", "#4A3A69", "#78491C")
            val tmp = mutableListOf<MediaItem>()
            for (i in 0 until minOf(arr.length(), 18)) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name"); if (name.isBlank()) continue
                val gArr = o.optJSONArray("genres")
                val gs = (0 until (gArr?.length() ?: 0)).map { gArr!!.optString(it) }
                val gStr = if (gs.isEmpty()) "Series" else gs.take(2).joinToString(" • ")
                val r = o.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
                val img = o.optJSONObject("image")?.optString("medium")?.replace("http://", "https://")
                val net = o.optJSONObject("network")?.optString("name") ?: "LIVE TV"
                val desc = o.optString("summary", "").replace(Regex("<[^>]*>"), "").ifBlank { "Recommended show." }
                tmp.add(MediaItem("tv_${o.optInt("id")}", name, "Recommended  |  $gStr • $net", desc, net.uppercase(), gStr, if (r > 0) String.format("%.1f", r) else "7.8", MediaCategory.SHOW, img, cols[i % cols.size], gs.any { it == "Crime" || it == "Horror" }))
            }
            if (tmp.isNotEmpty()) {
                synchronized(liveItems) { liveItems.clear(); liveItems.addAll(tmp) }
                onDone()
            }
        }
    }
}
