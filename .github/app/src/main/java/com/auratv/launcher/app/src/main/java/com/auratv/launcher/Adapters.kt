package com.auratv.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MediaCardAdapter(private var items: List<MediaItem>, private val onClick: (MediaItem) -> Unit) :
    RecyclerView.Adapter<MediaCardAdapter.H>() {
    fun submitList(list: List<MediaItem>) { items = list; notifyDataSetChanged() }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = H(LayoutInflater.from(p.context).inflate(R.layout.item_media_card, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val m = items[pos]
        h.title.text = m.title
        h.meta.text = "${m.sourceName}  •  ★ ${m.rating}"
        ImageLoader.load(h.img, m.imageUrl, m.title, m.sourceName, m.accentHex, false)
        h.itemView.setOnClickListener { onClick(m) }
        h.itemView.setOnFocusChangeListener { v, f ->
            h.border.visibility = if (f) View.VISIBLE else View.GONE
            v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
        }
    }
    class H(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgPoster)
        val title: TextView = v.findViewById(R.id.txtCardTitle)
        val meta: TextView = v.findViewById(R.id.txtCardMeta)
        val border: View = v.findViewById(R.id.focusBorder)
    }
}

class AppCircleAdapter(private var items: List<AppEntry>, private val onClick: (AppEntry) -> Unit, private val onLong: (AppEntry) -> Unit) :
    RecyclerView.Adapter<AppCircleAdapter.H>() {
    fun submitList(list: List<AppEntry>) { items = list; notifyDataSetChanged() }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = H(LayoutInflater.from(p.context).inflate(R.layout.item_app_circle, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val a = items[pos]
        h.label.text = a.label
        h.circle.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(a.accentColor) }
        if (a.icon != null) {
            h.img.visibility = View.VISIBLE; h.fb.visibility = View.GONE; h.img.setImageDrawable(a.icon)
        } else {
            val luma = (Color.red(a.accentColor) * 299 + Color.green(a.accentColor) * 587 + Color.blue(a.accentColor) * 114) / 1000
            h.img.visibility = View.GONE; h.fb.visibility = View.VISIBLE; h.fb.text = a.label.take(6)
            h.fb.setTextColor(if (luma > 160) 0xFFE50914.toInt() else Color.WHITE)
        }
        h.itemView.setOnClickListener { onClick(a) }
        h.itemView.setOnLongClickListener { onLong(a); true }
        h.itemView.setOnFocusChangeListener { v, f -> v.animate().scaleX(if (f) 1.12f else 1f).scaleY(if (f) 1.12f else 1f).setDuration(130).start() }
    }
    class H(v: View) : RecyclerView.ViewHolder(v) {
        val circle: View = v.findViewById(R.id.appCircleContainer)
        val img: ImageView = v.findViewById(R.id.imgAppIcon)
        val fb: TextView = v.findViewById(R.id.txtAppFallback)
        val label: TextView = v.findViewById(R.id.txtAppLabel)
    }
}

class AppGridAdapter(private var items: List<AppEntry>, private val onClick: (AppEntry) -> Unit, private val onLong: (AppEntry) -> Unit) :
    RecyclerView.Adapter<AppGridAdapter.H>() {
    fun submitList(list: List<AppEntry>) { items = list; notifyDataSetChanged() }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = H(LayoutInflater.from(p.context).inflate(R.layout.item_app_grid, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, pos: Int) {
        val a = items[pos]
        h.name.text = a.label
        h.pin.visibility = if (a.isPinned) View.VISIBLE else View.GONE
        h.img.setImageDrawable(a.icon)
        h.itemView.setOnClickListener { onClick(a) }
        h.itemView.setOnLongClickListener { onLong(a); true }
    }
    class H(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgGridAppIcon)
        val name: TextView = v.findViewById(R.id.txtGridAppName)
        val pin: TextView = v.findViewById(R.id.txtPinBadge)
    }
    }
