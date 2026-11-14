package com.auratv.launcher

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {
    private val mainH = Handler(Looper.getMainLooper())
    private val exec = Executors.newFixedThreadPool(4)
    private val cache = object : LruCache<String, Bitmap>(8192) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount / 1024
    }

    fun load(iv: ImageView, url: String?, title: String, badge: String, accentHex: String, isHero: Boolean) {
        val fbKey = "fb_${title}_$isHero"
        val key = url ?: fbKey
        iv.tag = key
        cache.get(key)?.let { iv.setImageBitmap(it); return }
        val fb = cache.get(fbKey) ?: makeFallback(title, badge, accentHex, isHero).also { cache.put(fbKey, it) }
        iv.setImageBitmap(fb)
        if (url.isNullOrBlank()) return

        exec.execute {
            val bmp = downloadScaled(url, if (isHero) 800 else 380)
            if (bmp != null) {
                cache.put(key, bmp)
                mainH.post { if (iv.tag == key) iv.setImageBitmap(bmp) }
            }
        }
    }

    private fun downloadScaled(urlStr: String, maxDim: Int): Bitmap? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 7000; instanceFollowRedirects = true
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun makeFallback(title: String, badge: String, hex: String, isHero: Boolean): Bitmap {
        val w = if (isHero) 640 else 340
        val h = if (isHero) 360 else 190
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val col = runCatching { Color.parseColor(hex) }.getOrDefault(0xFF5A3921.toInt())
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF14161D.toInt(), col, 0xFF0B0C10.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawCircle(w * 0.72f, h * 0.35f, h * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFD180 })
        if (!isHero) {
            c.drawText(badge.uppercase(), 18f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 16f; isFakeBoldText = true })
            c.drawText(title.take(18), 18f, h - 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f; isFakeBoldText = true })
        }
        return bmp
    }
}
