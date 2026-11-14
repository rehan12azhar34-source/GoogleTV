package com.auratv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object TvCards {
    fun makeMediaCard(c: Context, m: Media, onClick: (Media) -> Unit): FrameLayout {
        val dp = { v: Int -> TvApps.dp(c, v) }
        val card = FrameLayout(c).apply {
            layoutParams = ViewGroup.LayoutParams(dp(178), dp(100))
            isFocusable = true; isClickable = true
        }
        val iv = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF1A1D24.toInt()) }
        TvData.loadImg(iv, m.img, m.title, m.src, m.hex, false)
        card.addView(iv, FrameLayout.LayoutParams(-1, -1))
        card.addView(View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE0000000.toInt(), 0x00000000))
        }, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))
        val txt = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(6)) }
        txt.addView(TextView(c).apply { text = m.title; setTextColor(Color.WHITE); textSize = 12f; setTypeface(null, Typeface.BOLD); maxLines = 1 })
        txt.addView(TextView(c).apply { text = "${m.src} • ★ ${m.rating}"; setTextColor(0xFFCCCCCC.toInt()); textSize = 10f; maxLines = 1 })
        card.addView(txt, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val border = View(c).apply {
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setStroke(dp(3), Color.WHITE) }
            visibility = View.GONE
        }
        card.addView(border, FrameLayout.LayoutParams(-1, -1))
        card.setOnClickListener { onClick(m) }
        card.setOnFocusChangeListener { v, f ->
            border.visibility = if (f) View.VISIBLE else View.GONE
            v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
        }
        return card
    }

    fun makeAppCircle(c: Context, app: AppEntry, onClick: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit): LinearLayout {
        val dp = { v: Int -> TvApps.dp(c, v) }
        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val ring = FrameLayout(c).apply {
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(3), Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) })
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val circle = FrameLayout(c).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(app.col) }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        if (app.icon != null) {
            circle.addView(ImageView(c).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER })
        } else {
            val luma = (Color.red(app.col) * 299 + Color.green(app.col) * 587 + Color.blue(app.col) * 114) / 1000
            circle.addView(TextView(c).apply {
                text = app.name.take(6); gravity = Gravity.CENTER; textSize = 10f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (luma > 160) 0xFFE50914.toInt() else Color.WHITE)
            })
        }
        ring.addView(circle, FrameLayout.LayoutParams(dp(58), dp(58)))
        wrap.addView(ring)
        wrap.addView(TextView(c).apply {
            text = app.name; setTextColor(Color.WHITE); textSize = 11f; maxLines = 1
            ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(68), -2).apply { topMargin = dp(4) })
        wrap.setOnClickListener { onClick(app) }
        wrap.setOnLongClickListener { onLong(app); true }
        wrap.setOnFocusChangeListener { v, f ->
            v.animate().scaleX(if (f) 1.12f else 1f).scaleY(if (f) 1.12f else 1f).setDuration(130).start()
        }
        return wrap
    }

    fun makeGridAppCell(c: Context, app: AppEntry, onClick: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit): LinearLayout {
        val dp = { v: Int -> TvApps.dp(c, v) }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(12), dp(10), dp(12))
            background = TvApps.pillBg(c, 0x22FFFFFF); isFocusable = true; isClickable = true
            setOnClickListener { onClick(app) }
            setOnLongClickListener { onLong(app); true }
            addView(ImageView(c).apply { app.icon?.let { setImageDrawable(it) } }, LinearLayout.LayoutParams(dp(46), dp(46)))
            addView(TextView(c).apply {
                text = (if (app.pin) "★ " else "") + app.name; setTextColor(Color.WHITE)
                textSize = 12f; gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }
}
