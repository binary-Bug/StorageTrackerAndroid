package com.binarybug.storagetracker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.binarybug.storagetracker.formatBytes
import java.text.DateFormat
import java.util.Date

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Light/dark colours. Text colours are chosen for at least 4.5:1 contrast on their backgrounds. */
class Palette(ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private fun pick(light: Long, night: Long) = (if (dark) night else light).toInt()
    val bg = pick(0xFFF3F5F8, 0xFF121417)
    val surface = pick(0xFFFFFFFF, 0xFF1D2126)
    val text = pick(0xFF1B1F23, 0xFFE8EAED)
    val muted = pick(0xFF59616B, 0xFFA7AEB8)
    val primary = pick(0xFF0B5FC4, 0xFF7DB7FF)
    val onPrimary = pick(0xFFFFFFFF, 0xFF08223F)
    val used = pick(0xFFB54708, 0xFFFFA25C)
    val free = pick(0xFF0F7A45, 0xFF5AD18F)
    val divider = pick(0xFFDDE2E8, 0xFF2E343B)
    val track = pick(0xFFDDE2E8, 0xFF2E343B)
    val warnBg = pick(0xFFFFF1E6, 0xFF3A2A1C)

    /** Slice colours, indexed by Breakdown.C_*: apps, app data, cache, images, videos, audio, docs, archives, apks, other, system. */
    val cat: IntArray = (
        if (dark) longArrayOf(0xFF6FA8FF, 0xFFB69CFF, 0xFF4FD1C5, 0xFFFF8CC6, 0xFFFFC46B, 0xFF7BE495, 0xFF6CCBFF, 0xFFC4C4C4, 0xFFFF8A80, 0xFFBFAF9E, 0xFF94A3B8)
        else longArrayOf(0xFF1D6FE0, 0xFF7C4DDB, 0xFF0E9488, 0xFFD6336C, 0xFFC77700, 0xFF1E9E4A, 0xFF0B7FC1, 0xFF6B7280, 0xFFD32F2F, 0xFF7C6F64, 0xFF475569)
        ).map { it.toInt() }.toIntArray()
}

/** Row with a coloured dot, name + note on the left and size + percentage on the right. */
fun Context.legendRow(
    p: Palette, color: Int, title: String, note: String?, value: String, pct: String,
    chevron: Boolean = false, onClick: (() -> Unit)? = null,
): View {
    val outer = vbox()
    val row = hbox().apply { setPadding(dp(16), dp(10), dp(16), dp(10)); minimumHeight = dp(52) }
    row.addView(
        View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
        lp(dp(12), dp(12), 0f, r = dp(12)),
    )
    val left = vbox()
    left.addView(label(title, 15f, p.text))
    if (!note.isNullOrEmpty()) left.addView(label(note, 12f, p.muted))
    row.addView(left, lp(0, WRAP, 1f))
    val right = vbox().apply { gravity = Gravity.END }
    right.addView(label(value, 15f, p.text, true).apply { gravity = Gravity.END })
    if (pct.isNotEmpty()) right.addView(label(pct, 12f, p.muted).apply { gravity = Gravity.END })
    row.addView(right, lp(WRAP, WRAP, 0f, l = dp(12)))
    if (chevron) row.addView(label("›", 22f, p.muted), lp(WRAP, WRAP, 0f, l = dp(8)))
    outer.addView(row)
    outer.addView(View(this).apply { setBackgroundColor(p.divider) }, lp(MATCH, 1))
    outer.contentDescription = listOfNotNull(title, note, value, pct.ifEmpty { null }).joinToString(", ")
    outer.isFocusable = true
    if (onClick != null) {
        outer.isClickable = true
        outer.setOnClickListener { onClick() }
        val tv = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) outer.foreground = getDrawable(tv.resourceId)
    }
    return outer
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
    LinearLayout.LayoutParams(w, h, weight).apply { setMargins(l, t, r, b) }

fun Context.vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

fun Context.label(s: CharSequence, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
    text = s
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

fun Context.heading(s: CharSequence, p: Palette) = label(s, 17f, p.text, true).apply { isAccessibilityHeading = true }

fun Context.card(p: Palette) = vbox().apply {
    background = GradientDrawable().apply { setColor(p.surface); cornerRadius = dpf(16f) }
    setPadding(dp(16), dp(14), dp(16), dp(16))
}

fun LinearLayout.addCard(ctx: Context, card: View) = addView(card, lp(l = ctx.dp(12), t = ctx.dp(10), r = ctx.dp(12)))

private fun Context.ripple(p: Palette, content: GradientDrawable) =
    RippleDrawable(ColorStateList.valueOf(if (p.dark) 0x33FFFFFF else 0x22000000), content, null)

/** Real [Button] (so screen readers announce it as one) with a 48dp minimum touch target. */
fun Context.pill(s: String, p: Palette, filled: Boolean = true, onClick: () -> Unit) = Button(this).apply {
    text = s
    isAllCaps = false
    stateListAnimator = null
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    minHeight = dp(48)
    minimumHeight = dp(48)
    setPadding(dp(18), 0, dp(18), 0)
    val shape = GradientDrawable().apply {
        cornerRadius = dpf(24f)
        if (filled) setColor(p.primary) else { setColor(0); setStroke(dp(1), p.primary) }
    }
    background = ripple(p, shape)
    setTextColor(if (filled) p.onPrimary else p.primary)
    setOnClickListener { onClick() }
}

/** Compact two-or-three way toggle (e.g. Folders | Large files). */
class Segmented(ctx: Context, private val p: Palette, labels: List<String>, selected: Int, private val onPick: (Int) -> Unit) : LinearLayout(ctx) {
    private val buttons = ArrayList<Button>()
    var index = selected
        private set

    init {
        orientation = HORIZONTAL
        labels.forEachIndexed { i, s ->
            val b = Button(ctx).apply {
                text = s
                isAllCaps = false
                stateListAnimator = null
                minHeight = ctx.dp(48)
                minimumHeight = ctx.dp(48)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setOnClickListener { set(i); onPick(i) }
            }
            buttons.add(b)
            addView(b, LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = ctx.dp(8) })
        }
        set(selected)
    }

    fun set(i: Int) {
        index = i
        buttons.forEachIndexed { k, b ->
            val on = k == i
            b.background = GradientDrawable().apply {
                cornerRadius = context.dpf(24f)
                if (on) setColor(p.primary) else { setColor(0); setStroke(context.dp(1), p.divider) }
            }
            b.setTextColor(if (on) p.onPrimary else p.text)
            b.isSelected = on
            b.stateDescription = if (on) "Selected" else "Not selected"
        }
    }
}

/** Spinner that only reports real user changes, never the programmatic initial selection. */
class Dropdown(ctx: Context, private val onPick: (Int) -> Unit) : Spinner(ctx) {
    private var last = -1

    init {
        minimumHeight = ctx.dp(48)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != last) { last = position; onPick(position) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    fun load(items: List<String>, selected: Int) {
        last = selected.coerceAtLeast(0) // Spinner always shows an item; don't report it as a user choice
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        setSelection(selected.coerceAtLeast(0), false)
    }
}

/** List row: title/subtitle on the left, value/sub-value on the right, optional chevron. Read as one item by TalkBack. */
fun Context.listRow(
    p: Palette, title: String, subtitle: String?, right: String?, rightColor: Int, rightSub: String?,
    chevron: Boolean = false, onClick: (() -> Unit)? = null,
): View {
    val outer = vbox()
    val row = hbox().apply { setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(56) }
    val left = vbox()
    left.addView(label(title, 15f, p.text).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.MIDDLE })
    if (!subtitle.isNullOrEmpty()) left.addView(label(subtitle, 12f, p.muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.MIDDLE })
    row.addView(left, lp(0, WRAP, 1f))
    if (right != null || rightSub != null) {
        val r = vbox().apply { gravity = Gravity.END }
        if (right != null) r.addView(label(right, 15f, rightColor, true).apply { gravity = Gravity.END })
        if (rightSub != null) r.addView(label(rightSub, 12f, p.muted).apply { gravity = Gravity.END })
        row.addView(r, lp(WRAP, WRAP, 0f, l = dp(12)))
    }
    if (chevron) row.addView(label("›", 22f, p.muted), lp(WRAP, WRAP, 0f, l = dp(8)))
    outer.addView(row)
    outer.addView(View(this).apply { setBackgroundColor(p.divider) }, lp(MATCH, 1))
    outer.contentDescription = listOfNotNull(title, subtitle, right, rightSub).joinToString(", ")
    outer.isFocusable = true
    if (onClick != null) {
        outer.isClickable = true
        outer.setOnClickListener { onClick() }
        val tv = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) outer.foreground = getDrawable(tv.resourceId)
    }
    return outer
}

/** Adapter that simply asks a lambda for each row's view; fine for the few hundred rows we show. */
class ViewListAdapter : BaseAdapter() {
    var rows: List<() -> View> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }
    override fun getCount() = rows.size
    override fun getItem(position: Int): Any = position
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = rows[position]()
}

object Fmt {
    fun signed(b: Long) = when {
        b > 0 -> "+${formatBytes(b)}"
        b < 0 -> "−${formatBytes(-b)}"
        else -> "0 B"
    }
    fun arrow(b: Long) = when { b > 0 -> "▲"; b < 0 -> "▼"; else -> "–" }
    fun delta(b: Long) = "${arrow(b)} ${signed(b)}"

    fun dur(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }

    fun whenFull(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
    fun timeShort(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = now - ms
        return when {
            d < 60_000 -> "just now"
            d < 3_600_000 -> "${d / 60_000} min ago"
            d < 86_400_000 -> "${d / 3_600_000} h ago"
            else -> "${d / 86_400_000} d ago"
        }
    }

    fun pct(part: Long, whole: Long): String = if (whole <= 0) "" else "%.0f%%".format(part * 100.0 / whole)
}
