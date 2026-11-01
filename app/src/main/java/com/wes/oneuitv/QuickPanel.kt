package com.wes.oneuitv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView

/** The vertical quick panel: slides in from the right with quick tiles and the settings list. */
class QuickPanel(private val a: MainActivity, root: FrameLayout) {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val sheet = FrameLayout(a)
    private val content = LinearLayout(a)
    private val items = ArrayList<View>()
    private val width = Ui.px(460)
    private var page = "main"

    var isOpen = false
        private set

    init {
        sheet.visibility = View.GONE
        val bg = GradientDrawable()
        bg.setColor(Color.argb(238, 18, 20, 30))
        val r = Ui.pxf(56f)
        bg.cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        sheet.background = bg
        sheet.isClickable = true
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(Ui.px(32), Ui.px(34), Ui.px(32), Ui.px(34))
        content.clipChildren = false
        content.clipToPadding = false
        val scroll = ScrollView(a)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.addView(content)
        sheet.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(sheet, FrameLayout.LayoutParams(width, MATCH, Gravity.END))
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        a.dimHome(true)
        sheet.visibility = View.VISIBLE
        sheet.translationX = width.toFloat()
        sheet.animate().translationX(0f).setDuration(600).setInterpolator(OvershootInterpolator(0.7f)).start()
        showPage("main", 0)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        a.dimHome(false)
        a.focusChip()
        sheet.animate().translationX(width.toFloat()).setDuration(380).setInterpolator(AccelerateInterpolator())
            .withEndAction { sheet.visibility = View.GONE }.start()
    }

    /** returns true when it handled Back */
    fun back(): Boolean {
        if (!isOpen) return false
        if (page != "main") showPage("main", 0) else close()
        return true
    }

    private fun showPage(p: String, keep: Int) {
        page = p
        items.clear()
        content.removeAllViews()
        when (p) {
            "sources" -> sourcesPage()
            "wall" -> wallPage()
            else -> mainPage()
        }
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(220).start()
        sheet.post { items.getOrNull(keep)?.requestFocus() }
    }

    // ---------- pages ----------

    private fun mainPage() {
        header("Quick panel")
        pair(
            tile(R.drawable.ic_wifi, "Wi-Fi", a.wifiLabel()) { a.openWifi() },
            tile(R.drawable.ic_bt, "Bluetooth", "Open") { a.openBluetooth() }
        )
        pair(
            tile(R.drawable.ic_cast, "Screen mirror", "Open") { a.openCast() },
            tile(R.drawable.ic_keystone, "Auto keystone", "Picture settings") { a.openSys("PictureSettingsActivity") }
        )
        label("Settings")
        row("Sources") { showPage("sources", 0) }
        row("Wallpaper") { showPage("wall", 1) }
        row("Picture & Sound") { a.openSys("PictureSettingsActivity") }
        row("Focus") { a.openSys("FocusSettingsActivity") }
        row("All system settings") { a.openSys("MainSettings") }
    }

    private fun sourcesPage() {
        header("Sources")
        row("‹ Back") { showPage("main", 0) }
        val ins = a.inputs()
        if (ins.isEmpty()) label("No inputs found")
        for (i in ins) {
            val id = i.second
            row(i.first) { a.openInput(id) }
        }
    }

    private fun wallPage() {
        header("Wallpaper")
        val sel = a.prefs.getInt("wall", -1)
        row("‹ Back") { showPage("main", 0) }
        row("Auto-change (every 30 s)", sel == -1) {
            a.prefs.edit().putInt("wall", -1).apply()
            showPage("wall", 1)
        }
        for (i in a.palettes.indices) {
            row(a.palettes[i].name, sel == i) {
                a.prefs.edit().putInt("wall", i).apply()
                a.applyWall(i)
                showPage("wall", i + 2)
            }
        }
    }

    // ---------- pieces ----------

    private fun header(title: String) {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(a)
        t.text = title
        t.sizePx(34f)
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Color.WHITE)
        row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        val c = TextClock(a)
        c.format12Hour = "h:mm"
        c.format24Hour = "HH:mm"
        c.sizePx(26f)
        c.setTextColor(Color.argb(200, 255, 255, 255))
        row.addView(c)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = Ui.px(20)
        content.addView(row, lp)
    }

    private fun label(text: String) {
        val t = TextView(a)
        t.text = text
        t.sizePx(18f)
        t.setTextColor(Color.argb(180, 255, 255, 255))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = Ui.px(20)
        lp.leftMargin = Ui.px(4)
        content.addView(t, lp)
    }

    private fun tile(icon: Int, name: String, sub: String, onClick: () -> Unit): View {
        val t = LinearLayout(a)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(Ui.px(20), 0, Ui.px(20), 0)
        t.background = round(Color.argb(30, 255, 255, 255), 30f)
        val iv = ImageView(a)
        iv.setImageResource(icon)
        val p = Ui.px(11)
        iv.setPadding(p, p, p, p)
        iv.background = round(Color.argb(40, 255, 255, 255), 40f)
        t.addView(iv, LinearLayout.LayoutParams(Ui.px(46), Ui.px(46)))
        val n = TextView(a)
        n.text = name
        n.sizePx(21f)
        n.setTypeface(null, Typeface.BOLD)
        n.setTextColor(Color.WHITE)
        n.maxLines = 1
        val nlp = LinearLayout.LayoutParams(WRAP, WRAP)
        nlp.topMargin = Ui.px(8)
        t.addView(n, nlp)
        val s = TextView(a)
        s.text = sub
        s.sizePx(15f)
        s.setTextColor(Color.argb(190, 255, 255, 255))
        s.maxLines = 1
        t.addView(s, LinearLayout.LayoutParams(WRAP, WRAP))
        t.pressable(onClick)
        t.oneUiFocus(30f)
        items.add(t)
        return t
    }

    private fun pair(x: View, y: View) {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.clipChildren = false
        val l1 = LinearLayout.LayoutParams(0, Ui.px(112), 1f)
        l1.rightMargin = Ui.px(7)
        val l2 = LinearLayout.LayoutParams(0, Ui.px(112), 1f)
        l2.leftMargin = Ui.px(7)
        r.addView(x, l1)
        r.addView(y, l2)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = Ui.px(14)
        content.addView(r, lp)
    }

    private fun row(text: String, check: Boolean = false, onClick: () -> Unit): View {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(Ui.px(22), 0, Ui.px(22), 0)
        r.background = round(Color.argb(30, 255, 255, 255), 24f)
        val t = TextView(a)
        t.text = text
        t.sizePx(21f)
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Color.WHITE)
        t.maxLines = 1
        r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        val c = TextView(a)
        c.text = if (check) "✓" else "›"
        c.sizePx(26f)
        c.setTextColor(Color.argb(190, 255, 255, 255))
        r.addView(c)
        r.pressable(onClick)
        r.oneUiFocus(24f)
        items.add(r)
        val lp = LinearLayout.LayoutParams(MATCH, Ui.px(56))
        lp.topMargin = Ui.px(8)
        content.addView(r, lp)
        return r
    }
}
