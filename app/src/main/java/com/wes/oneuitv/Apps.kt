package com.wes.oneuitv

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

class AppItem(val label: String, val icon: Drawable, val intent: Intent, val pkg: String)

object Apps {
    val catNames = listOf("Home", "Movies", "Music", "Browsers", "Apps")
    val catIcons = intArrayOf(R.drawable.ic_home, R.drawable.ic_movies, R.drawable.ic_music, R.drawable.ic_globe, R.drawable.ic_grid)

    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                val i = Intent(Intent.ACTION_MAIN)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                out.add(AppItem(ri.loadLabel(pm).toString(), ri.loadIcon(pm), i, pkg))
            }
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    /** 1 = Movies, 2 = Music, 3 = Browsers, 0 = no special category */
    fun catOf(a: AppItem): Int {
        val s = (a.pkg + " " + a.label).lowercase()
        fun has(vararg k: String): Boolean = k.any { s.contains(it) }
        return when {
            has("spotify", "music", "deezer", "tidal", "audio", "podcast") -> 2
            has("chrome", "brave", "firefox", "opera", "browser", "puffin", "tvbro", "bromite") -> 3
            has("netflix", "youtube", "video", "hbo", "disney", "hulu", "plex", "kodi", "vlc",
                "showmax", "movie", "twitch", "iptv", "stremio", "lftv") -> 1
            else -> 0
        }
    }
}
