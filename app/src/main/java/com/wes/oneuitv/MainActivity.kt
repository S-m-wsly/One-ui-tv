package com.wes.oneuitv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.max

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    lateinit var prefs: SharedPreferences
    private val h = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var aurora: AuroraView
    private lateinit var home: FrameLayout
    private lateinit var panel: QuickPanel
    private lateinit var appRow: LinearLayout
    private lateinit var catLabel: TextView
    private lateinit var chip: View
    private lateinit var chipWx: TextView
    private lateinit var resumeTitle: TextView
    private lateinit var resumeCard: View
    private lateinit var wxIcon: TextView
    private lateinit var wxTemp: TextView
    private lateinit var srcTitle: TextView
    private val navItems = ArrayList<NavItem>()
    private val intro = ArrayList<View>()
    private var allApps: List<AppItem> = emptyList()
    private var resumeApp: AppItem? = null
    private var cat = 0
    private var wallIndex = 0
    private var launchOverlay: View? = null
    private val userName = "Wes"

    val palettes: List<Palette> = listOf(
        Palette("Midnight", Color.rgb(15, 32, 39), Color.rgb(32, 58, 67), Color.rgb(44, 83, 100)),
        Palette("Sunset", Color.rgb(60, 20, 60), Color.rgb(190, 70, 80), Color.rgb(245, 160, 90)),
        Palette("Forest", Color.rgb(10, 40, 30), Color.rgb(25, 90, 70), Color.rgb(60, 140, 110)),
        Palette("Aurora", Color.rgb(20, 20, 60), Color.rgb(60, 50, 140), Color.rgb(40, 150, 160)),
        Palette("Ember", Color.rgb(40, 10, 20), Color.rgb(120, 30, 50), Color.rgb(200, 90, 60)),
        Palette("Ocean", Color.rgb(5, 30, 70), Color.rgb(10, 90, 150), Color.rgb(80, 170, 200))
    )

    private val autoWall: Runnable = object : Runnable {
        override fun run() {
            if (prefs.getInt("wall", -1) == -1) {
                wallIndex = (wallIndex + 1) % palettes.size
                applyWall(wallIndex)
            }
            h.postDelayed(this, 30000)
        }
    }

    // ---------- lifecycle ----------

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Ui.init(this)
        prefs = getSharedPreferences("oneuitv", Context.MODE_PRIVATE)
        root = FrameLayout(this)
        aurora = AuroraView(this)
        root.addView(aurora, FrameLayout.LayoutParams(MATCH, MATCH))
        buildHome()
        panel = QuickPanel(this, root)
        setContentView(root)
        val saved = prefs.getInt("wall", -1)
        wallIndex = if (saved >= 0) saved else 3
        applyWall(wallIndex)
        allApps = Apps.load(this)
        renderApps(false)
        updateResume()
        loadWeather()
        playIntro()
        resumeCard.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        h.removeCallbacks(autoWall)
        h.postDelayed(autoWall, 30000)
        val ov = launchOverlay
        if (ov != null) {
            ov.animate().alpha(0f).setDuration(250).withEndAction { root.removeView(ov) }.start()
            launchOverlay = null
        }
        dimHome(panel.isOpen)
        allApps = Apps.load(this)
        renderApps(false)
        updateResume()
        home.post { if (!panel.isOpen && currentFocus == null) resumeCard.requestFocus() }
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(autoWall)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (panel.isOpen) panel.close()
        resumeCard.requestFocus()
    }

    override fun onBackPressed() {
        if (panel.back()) return
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (panel.isOpen) panel.close() else panel.open()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ---------- small helpers ----------

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun fl(w: Int, ht: Int, g: Int, l: Int, t: Int, r: Int, b: Int): FrameLayout.LayoutParams {
        val p = FrameLayout.LayoutParams(w, ht, g)
        p.setMargins(Ui.px(l), Ui.px(t), Ui.px(r), Ui.px(b))
        return p
    }

    private fun text(s: String, size: Float, bold: Boolean = false, alpha: Float = 1f): TextView {
        val t = TextView(this)
        t.text = s
        t.sizePx(size)
        t.setTextColor(Color.WHITE)
        t.alpha = alpha
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    fun applyWall(i: Int) {
        aurora.setPalette(palettes[i])
    }

    fun dimHome(on: Boolean) {
        home.descendantFocusability = if (on) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_BEFORE_DESCENDANTS
        val s = if (on) 0.93f else 1f
        home.animate().scaleX(s).scaleY(s)
            .translationX(if (on) -Ui.pxf(36f) else 0f)
            .alpha(if (on) 0.6f else 1f)
            .setDuration(if (on) 600L else 400L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun focusChip() {
        chip.requestFocus()
    }

    private fun greeting(): String {
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = if (hr < 12) "morning" else if (hr < 18) "afternoon" else "evening"
        return "Good " + part + ", " + userName
    }

    // ---------- home screen ----------

    private fun buildHome() {
        home = FrameLayout(this)
        home.clipChildren = false
        home.clipToPadding = false
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        // greeting + date
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        val hi = text(greeting(), 56f, true)
        hi.includeFontPadding = false
        val dt = TextClock(this)
        dt.format12Hour = "EEEE, d MMMM"
        dt.format24Hour = "EEEE, d MMMM"
        dt.sizePx(22f)
        dt.setTextColor(Color.WHITE)
        dt.alpha = 0.8f
        head.addView(hi, LinearLayout.LayoutParams(WRAP, WRAP))
        val dlp = LinearLayout.LayoutParams(WRAP, WRAP)
        dlp.topMargin = Ui.px(10)
        head.addView(dt, dlp)
        home.addView(head, fl(WRAP, WRAP, Gravity.TOP or Gravity.START, 56, 40, 0, 0))
        intro.add(head)

        // status chip: wifi, weather, clock. Opens the quick panel.
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(Ui.px(26), Ui.px(14), Ui.px(26), Ui.px(14))
        c.background = round(Color.argb(56, 255, 255, 255), 999f)
        val wf = ImageView(this)
        wf.setImageResource(R.drawable.ic_wifi)
        c.addView(wf, LinearLayout.LayoutParams(Ui.px(24), Ui.px(24)))
        chipWx = text("--", 22f, true)
        val l1 = LinearLayout.LayoutParams(WRAP, WRAP)
        l1.leftMargin = Ui.px(16)
        c.addView(chipWx, l1)
        val clk = TextClock(this)
        clk.format12Hour = "h:mm"
        clk.format24Hour = "HH:mm"
        clk.sizePx(22f)
        clk.setTypeface(null, Typeface.BOLD)
        clk.setTextColor(Color.WHITE)
        val l2 = LinearLayout.LayoutParams(WRAP, WRAP)
        l2.leftMargin = Ui.px(16)
        c.addView(clk, l2)
        c.pressable { panel.open() }
        c.oneUiFocus(30f)
        chip = c
        home.addView(c, fl(WRAP, WRAP, Gravity.TOP or Gravity.END, 0, 46, 56, 0))
        intro.add(c)

        // widgets
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.addView(buildResume(), widgetLp(520, 0))
        row.addView(buildWeather(), widgetLp(280, 20))
        row.addView(buildSources(), widgetLp(320, 20))
        home.addView(row, fl(WRAP, WRAP, Gravity.TOP or Gravity.START, 56, 176, 0, 0))

        // category label + app row
        catLabel = text("Suggested", 22f, true)
        home.addView(catLabel, fl(WRAP, WRAP, Gravity.TOP or Gravity.START, 60, 392, 0, 0))
        intro.add(catLabel)
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12))
        appRow = LinearLayout(this)
        appRow.orientation = LinearLayout.HORIZONTAL
        appRow.clipChildren = false
        scroll.addView(appRow)
        home.addView(scroll, fl(MATCH, WRAP, Gravity.TOP or Gravity.START, 28, 416, 24, 0))

        // floating bottom nav
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.clipChildren = false
        nav.setPadding(Ui.px(8), Ui.px(8), Ui.px(8), Ui.px(8))
        nav.background = round(Color.argb(170, 20, 22, 32), 999f)
        for (i in Apps.catNames.indices) {
            val n = NavItem(this, Apps.catIcons[i], Apps.catNames[i])
            n.pressable { focusFirstApp() }
            n.oneUiFocus(30f) { has -> if (has) setCategory(i) }
            val lp = LinearLayout.LayoutParams(WRAP, Ui.px(60))
            if (i > 0) lp.leftMargin = Ui.px(6)
            nav.addView(n, lp)
            navItems.add(n)
        }
        navItems[0].select(true, false)
        home.addView(nav, fl(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 34))
        intro.add(nav)
    }

    private fun widgetLp(w: Int, left: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(Ui.px(w), Ui.px(196))
        lp.leftMargin = Ui.px(left)
        return lp
    }

    private fun widgetCol(card: FrameLayout): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(Ui.px(28), Ui.px(26), Ui.px(28), Ui.px(26))
        card.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        return col
    }

    private fun spacer(): View = View(this)

    private fun buildResume(): View {
        val card = FrameLayout(this)
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(90, 70, 200), Color.rgb(220, 70, 130)))
        g.cornerRadius = Ui.pxf(40f)
        card.background = g
        val col = widgetCol(card)
        col.addView(text("Resume", 18f, false, 0.8f))
        resumeTitle = text("Nothing opened yet", 30f, true)
        col.addView(resumeTitle)
        col.addView(spacer(), LinearLayout.LayoutParams(WRAP, 0, 1f))
        col.addView(text("Press OK to open", 18f, false, 0.8f))
        val pc = ImageView(this)
        pc.setImageResource(R.drawable.ic_play)
        pc.setColorFilter(Color.rgb(26, 21, 48), PorterDuff.Mode.SRC_IN)
        val p = Ui.px(18)
        pc.setPadding(p, p, p, p)
        pc.background = round(Color.WHITE, 40f)
        card.addView(pc, fl(Ui.px(62), Ui.px(62), Gravity.BOTTOM or Gravity.END, 0, 0, 26, 26))
        card.pressable {
            val a = resumeApp
            if (a != null) launch(a, card) else toast("Open an app first")
        }
        card.oneUiFocus(40f)
        resumeCard = card
        intro.add(card)
        return card
    }

    private fun buildWeather(): View {
        val card = FrameLayout(this)
        card.background = round(Color.argb(56, 255, 255, 255), 40f)
        val col = widgetCol(card)
        col.addView(spacer(), LinearLayout.LayoutParams(WRAP, 0, 1f))
        wxTemp = text("--", 64f)
        wxTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
        wxTemp.includeFontPadding = false
        col.addView(wxTemp)
        col.addView(text("Windhoek", 18f, false, 0.8f))
        wxIcon = text("", 56f)
        card.addView(wxIcon, fl(WRAP, WRAP, Gravity.TOP or Gravity.END, 0, 18, 22, 0))
        card.pressable { loadWeather() }
        card.oneUiFocus(40f)
        intro.add(card)
        return card
    }

    private fun buildSources(): View {
        val card = FrameLayout(this)
        card.background = round(Color.argb(56, 255, 255, 255), 40f)
        val col = widgetCol(card)
        col.addView(text("Sources", 18f, false, 0.8f))
        srcTitle = text(inputs().firstOrNull()?.first ?: "Inputs", 30f, true)
        col.addView(srcTitle)
        col.addView(spacer(), LinearLayout.LayoutParams(WRAP, 0, 1f))
        col.addView(text("Press OK to switch", 18f, false, 0.8f))
        val ic = ImageView(this)
        ic.setImageResource(R.drawable.ic_input)
        card.addView(ic, fl(Ui.px(40), Ui.px(40), Gravity.TOP or Gravity.END, 0, 24, 26, 0))
        card.pressable {
            val first = inputs().firstOrNull()
            if (first != null) openInput(first.second) else toast("No inputs found")
        }
        card.oneUiFocus(40f)
        intro.add(card)
        return card
    }

    private fun playIntro() {
        for ((i, v) in intro.withIndex()) {
            v.alpha = 0f
            v.translationY = Ui.pxf(28f)
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(800)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun updateResume() {
        val last = prefs.getString("last", null)
        resumeApp = allApps.firstOrNull { it.pkg == last } ?: allApps.firstOrNull { Apps.catOf(it) == 1 }
        resumeTitle.text = resumeApp?.label ?: "Nothing opened yet"
        srcTitle.text = inputs().firstOrNull()?.first ?: "Inputs"
    }

    // ---------- categories + app row ----------

    private fun uses(a: AppItem): Int = prefs.getInt("use_" + a.pkg, 0)

    private fun appsFor(c: Int): List<AppItem> = when (c) {
        0 -> allApps.sortedWith(compareByDescending<AppItem> { uses(it) }.thenBy { it.label.lowercase() }).take(8)
        4 -> allApps
        else -> allApps.filter { Apps.catOf(it) == c }
    }

    private fun setCategory(i: Int) {
        if (i == cat) return
        cat = i
        for ((k, n) in navItems.withIndex()) n.select(k == i, true)
        renderApps(true)
    }

    private fun focusFirstApp() {
        appRow.getChildAt(0)?.requestFocus()
    }

    private fun renderApps(animate: Boolean) {
        appRow.removeAllViews()
        catLabel.text = if (cat == 0) "Suggested" else Apps.catNames[cat]
        val list = appsFor(cat)
        var first: View? = null
        for ((i, a) in list.withIndex()) {
            val t = appTile(a)
            if (first == null) first = t
            val lp = LinearLayout.LayoutParams(Ui.px(132), WRAP)
            lp.rightMargin = Ui.px(10)
            appRow.addView(t, lp)
            t.nextFocusDownId = navItems[cat].id
            if (animate) {
                t.scaleX = 0.55f
                t.scaleY = 0.55f
                t.alpha = 0f
                t.translationY = Ui.pxf(18f)
                t.animate().scaleX(1f).scaleY(1f).alpha(1f).translationY(0f)
                    .setStartDelay(i * 45L).setDuration(520)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }
        }
        if (list.isEmpty()) {
            val e = text("No apps here yet", 20f, false, 0.8f)
            e.setPadding(Ui.px(16), Ui.px(40), Ui.px(16), Ui.px(40))
            appRow.addView(e)
        }
        val fid = first?.id ?: View.NO_ID
        for (n in navItems) n.nextFocusUpId = fid
    }

    private fun appTile(a: AppItem): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(Ui.px(8), Ui.px(16), Ui.px(8), Ui.px(14))
        t.id = View.generateViewId()
        val iv = ImageView(this)
        iv.setImageDrawable(a.icon)
        val p = Ui.px(16)
        iv.setPadding(p, p, p, p)
        iv.background = round(Color.argb(46, 255, 255, 255), 31f)
        t.addView(iv, LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)))
        val tv = text(a.label, 18f)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(Ui.px(116), WRAP)
        lp.topMargin = Ui.px(12)
        t.addView(tv, lp)
        t.pressable { launch(a, iv) }
        t.oneUiFocus(38f)
        return t
    }

    // ---------- One UI app launch: a circle grows from the icon, then the app opens ----------

    private fun launch(a: AppItem, from: View) {
        prefs.edit().putInt("use_" + a.pkg, uses(a) + 1).putString("last", a.pkg).apply()
        val hue = ((a.pkg.hashCode() and 0x7fffffff) % 360).toFloat()
        val ov = View(this)
        ov.setBackgroundColor(Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.45f)))
        ov.visibility = View.INVISIBLE
        root.addView(ov, FrameLayout.LayoutParams(MATCH, MATCH))
        launchOverlay = ov
        val a1 = IntArray(2)
        val a2 = IntArray(2)
        from.getLocationInWindow(a1)
        root.getLocationInWindow(a2)
        val cx = a1[0] - a2[0] + from.width / 2
        val cy = a1[1] - a2[1] + from.height / 2
        val end = hypot(root.width.toFloat(), root.height.toFloat())
        home.animate().scaleX(0.9f).scaleY(0.9f).alpha(0f).setDuration(450).start()
        ov.post {
            val anim = ViewAnimationUtils.createCircularReveal(ov, cx, cy, from.width / 3f, end)
            anim.duration = 480
            anim.interpolator = DecelerateInterpolator()
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        startActivity(a.intent)
                    } catch (e: Exception) {
                        toast("Could not open " + a.label)
                        root.removeView(ov)
                        launchOverlay = null
                        dimHome(panel.isOpen)
                    }
                }
            })
            ov.visibility = View.VISIBLE
            anim.start()
        }
    }

    // ---------- weather ----------

    private fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    private fun loadWeather() {
        thread {
            var temp = "--"
            var icon = ""
            try {
                val u = URL("https://api.open-meteo.com/v1/forecast?latitude=-22.56&longitude=17.08&current=temperature_2m,weather_code")
                val c = u.openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val body = c.inputStream.bufferedReader().readText()
                c.disconnect()
                val cur = JSONObject(body).getJSONObject("current")
                temp = Math.round(cur.getDouble("temperature_2m")).toInt().toString() + "°"
                icon = weatherIcon(cur.getInt("weather_code"))
            } catch (e: Exception) {
            }
            h.post {
                chipWx.text = (icon + " " + temp).trim()
                wxTemp.text = temp
                wxIcon.text = icon
            }
        }
        h.postDelayed({ loadWeather() }, 900000)
    }

    // ---------- system actions (used by the quick panel) ----------

    fun wifiLabel(): String {
        return try {
            val w = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val s: String = w.connectionInfo?.ssid ?: ""
            val n = s.trim('"')
            if (n.isEmpty() || n.startsWith("<unknown")) "Open" else n
        } catch (e: Exception) {
            "Open"
        }
    }

    fun openWifi() {
        try {
            startActivity(Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e2: Exception) {
                toast("Could not open Wi-Fi")
            }
        }
    }

    fun openBluetooth() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: Exception) {
            openSys("MainSettings")
        }
    }

    fun openCast() {
        try {
            startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
        } catch (e: Exception) {
            toast("Screen mirroring is not available here")
        }
    }

    /** list of (label, input id): first HDMI and the AV input, like the old app */
    fun inputs(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            val tm = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            val all: List<TvInputInfo> = tm.tvInputList
            val hdmi: TvInputInfo? = all.filter { it.type == TvInputInfo.TYPE_HDMI }.minByOrNull { it.id }
            val av: TvInputInfo? = all.firstOrNull { it.type == TvInputInfo.TYPE_COMPOSITE }
            if (hdmi != null) out.add(Pair("HDMI", hdmi.id))
            if (av != null) out.add(Pair("AV", av.id))
        } catch (e: Exception) {
        }
        return out
    }

    fun openInput(id: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))
        } catch (e: Exception) {
            toast("Could not open input")
        }
    }

    fun openSys(suffix: String) {
        try {
            val pkg = "com.android.tv.settings"
            val info = packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            val a = info.activities?.firstOrNull { it.name.endsWith(suffix) }
            if (a == null) {
                toast("Screen not found")
                return
            }
            startActivity(Intent().setClassName(pkg, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Could not open screen")
        }
    }
}

/** A tab in the floating nav bar: the selected tab turns into a white pill and slides its label out. */
class NavItem(ctx: Context, iconRes: Int, name: String) : LinearLayout(ctx) {
    private val icon = ImageView(ctx)
    private val label = TextView(ctx)
    private val pill = GradientDrawable()
    private var labelW = 0
    private var progress = 0f
    private var anim: ValueAnimator? = null

    init {
        id = View.generateViewId()
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumWidth = Ui.px(60)
        setPadding(Ui.px(17), 0, Ui.px(17), 0)
        icon.setImageResource(iconRes)
        addView(icon, LayoutParams(Ui.px(26), Ui.px(26)))
        label.text = name
        label.sizePx(20f)
        label.setTypeface(null, Typeface.BOLD)
        label.maxLines = 1
        label.setPadding(Ui.px(10), 0, 0, 0)
        label.measure(0, 0)
        labelW = label.measuredWidth
        addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT))
        pill.cornerRadius = Ui.pxf(40f)
        background = pill
        apply(0f)
    }

    private fun apply(p: Float) {
        progress = p
        val c = p.coerceIn(0f, 1f)
        val lp = label.layoutParams
        lp.width = (labelW * max(0f, p)).toInt()
        label.layoutParams = lp
        label.alpha = c
        pill.setColor(Color.argb((250 * c).toInt(), 255, 255, 255))
        val ink = if (c > 0.5f) Color.rgb(20, 22, 31) else Color.WHITE
        label.setTextColor(ink)
        icon.setColorFilter(ink, PorterDuff.Mode.SRC_IN)
    }

    fun select(on: Boolean, animate: Boolean) {
        val to = if (on) 1f else 0f
        anim?.cancel()
        if (!animate) {
            apply(to)
            return
        }
        val a = ValueAnimator.ofFloat(progress, to)
        a.duration = 450
        a.interpolator = OvershootInterpolator(1.2f)
        a.addUpdateListener { apply(it.animatedValue as Float) }
        a.start()
        anim = a
    }
}
