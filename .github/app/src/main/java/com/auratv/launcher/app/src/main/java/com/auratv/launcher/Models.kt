package com.auratv.launcher

import android.graphics.drawable.Drawable

enum class TabType { FOR_YOU, MOVIES, SHOWS, APPS, LIBRARY }
enum class MediaCategory { MOVIE, SHOW }

data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val sourceName: String,
    val genre: String,
    val rating: String,
    val category: MediaCategory,
    val imageUrl: String?,
    val accentHex: String,
    val isMature: Boolean = false,
    val targetPackage: String? = null
)

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isPinned: Boolean,
    val accentColor: Int
)
