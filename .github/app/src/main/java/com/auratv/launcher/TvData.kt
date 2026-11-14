package com.auratv.launcher

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class Media(
    val id: String, val title: String, val sub: String, val desc: String,
    val src: String, val genre: String, val rating: String, val kind: String,
    val img: String?, val hex: String, val mature: Boolean = false, val pkg: String? = null
)

data class AppEntry(val name: String, val pkg: String, val icon: Drawable?, val pin: Boolean, val col: Int)

object TvData {
    private val h = Handler(Looper.getMainLooper())
    private val exec = Executors.newFixedThreadPool(4)
    private val cache = object : LruCache<String, Bitmap>(8192) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount / 1024
    }

    val baseMedia = listOf(
        Media("aashram", "Aashram", "Trending | A corrupt spiritual guru's world of crime & exploitation", "Baba Nirala runs a sprawling ashram hiding crime and politics.", "MXPLAYER TV", "Crime • Drama", "7.4", "show", null, "#9B621B", true, "com.mxtech.videoplayer.ad"),
        Media("rocket", "Rocket Boys", "Critically Acclaimed | Pioneers of India's space & nuclear journey", "Story of Dr. Homi Bhabha and Dr. Vikram Sarabhai.", "SONYLIV", "Bio • Drama", "8.9", "show", null, "#245C73", false, "com.sonyliv"),
        Media("farzi", "Farzi", "Top Pick | An artist drawn into high-stakes counterfeiting", "A small-time artist creates the ultimate counterfeit note.", "PRIME VIDEO", "Crime • Thriller", "8.4", "show", null, "#3A4E5C", true, "com.amazon.avod.thirdpartyclient"),
        Media("scam", "Scam 2003", "Popular | The Telgi stamp paper empire", "Follows Abdul Karim Telgi behind a massive financial scam.", "SONYLIV", "Crime • Bio", "8.0", "show", null, "#2D5B4C", false, "com.sonyliv"),
        Media("samantar", "Samantar", "Mystery | What if someone already lived your life?", "Kumar Mahajan discovers a man whose past mirrors his future.", "MXPLAYER TV", "Mystery", "7.9", "show", null, "#333A4F", false, "com.mxtech.videoplayer.ad"),
        Media("queen", "Queen", "Drama | A journey of resilience and ambition", "Rise of a young school topper reshaping politics.", "MXPLAYER TV", "Drama", "7.8", "show", null, "#7A2E3A", false, "com.mxtech.videoplayer.ad"),
        Media("inter", "Interstellar", "Sci-Fi Classic | Mankind was born on Earth", "Explorers travel through a wormhole to save humanity.", "PRIME VIDEO", "Sci-Fi", "8.7", "movie", null, "#1E3A5F", false, "com.amazon.avod.thirdpartyclient"),
        Media("rrr", "RRR", "Blockbuster | Fire and Water unite", "Two revolutionaries fight British colonial rule in 1920s India.", "NETFLIX", "Action", "7.8", "movie", null, "#8C321A", false, "com.netflix.mediaclient"),
        Media("spider", "Across the Spider-Verse", "Animation | One hero faces the multiverse", "Miles Morales meets Spider-People across dimensions.", "NETFLIX", "Animation", "8.6", "movie", null, "#7A1C4B", false, "com.netflix.mediaclient"),
        Media("fail", "12th Fail", "Top Rated | An inspiring true story of grit", "Based on IPS officer Manoj Kumar Sharma's journey.", "HOTSTAR", "Bio • Drama", "8.8", "movie", null, "#3E5B3C", false, "in.startv.hotstar")
    )

    private val liveMedia = mutableListOf<Media>()

    fun liveSnapshot(): List<Media> = synchronized(liveMedia) { liveMedia.toList() }

    fun catalog(kids: Boolean): List<Media> {
        val all = (baseMedia + liveSnapshot()).distinctBy { it.id }
        return if (kids) all.filter { !it.mature } else all
    }

    fun fetchTvMaze(onReady: () -> Unit) = exec.execute {
        try {
            val txt = (URL("https://api.tvmaze.com/shows?page=0").openConnection() as HttpURLConnection).run {
                connectTimeout = 5000; readTimeout = 7000; inputStream.bufferedReader().readText()
            }
            val arr = JSONArray(txt)
            val cols = listOf("#1D4B5F", "#5F2D3A", "#38522E", "#4A3A69", "#78491C")
            val tmp = mutableListOf<Media>()
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
                tmp.add(
                    Media(
                        "tv_${o.optInt("id")}", name, "Recommended | $gStr • $net", desc,
                        net.uppercase(), gStr, if (r > 0) String.format("%.1f", r) else "7.8",
                        "show", img, cols[i % cols.size], gs.any { it == "Crime" || it == "Horror" }
                    )
                )
            }
            if (tmp.isNotEmpty()) {
                synchronized(liveMedia) { liveMedia.clear(); liveMedia.addAll(tmp) }
                h.post { onReady() }
            }
        } catch (_: Exception) {}
    }

    fun loadImg(iv: ImageView, url: String?, title: String, src: String, hex: String, hero: Boolean) {
        val key = url ?: "fb_${title}_$hero"
        iv.tag = key
        cache.get(key)?.let { iv.setImageBitmap(it); return }
        iv.setImageBitmap(makeFallback(title, src, hex, hero))
        if (url.isNullOrBlank()) return
        exec.execute {
            val bmp = runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 5000; readTimeout = 7000; inputStream.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
            if (bmp != null) {
                cache.put(key, bmp)
                h.post { if (iv.tag == key) iv.setImageBitmap(bmp) }
            }
        }
    }

    private fun makeFallback(title: String, src: String, hex: String, hero: Boolean): Bitmap {
        val w = if (hero) 720 else 360; val h = if (hero) 405 else 200
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val col = runCatching { Color.parseColor(hex) }.getOrDefault(0xFF5A3921.toInt())
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF14161D.toInt(), col, 0xFF0B0C10.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawCircle(w * 0.72f, h * 0.35f, h * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFD180 })
        if (!hero) {
            c.drawText(src, 20f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 18f; isFakeBoldText = true })
            c.drawText(title.take(18), 20f, h - 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; isFakeBoldText = true })
        }
        return b
    }
}
