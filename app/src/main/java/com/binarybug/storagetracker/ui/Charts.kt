package com.binarybug.storagetracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import java.text.DateFormat
import java.util.Date

/** Thick rounded bar showing how full the storage is. */
class UsageBarView(ctx: Context, private val p: Palette) : View(ctx) {
    private var frac = 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.track }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.used }

    fun set(used: Long, total: Long) {
        frac = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
        contentDescription = "Storage ${(frac * 100).toInt()} percent full"
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        c.drawRoundRect(0f, 0f, w, h, r, r, track)
        if (frac > 0f) {
            c.save()
            c.clipRect(0f, 0f, w * frac, h)
            c.drawRoundRect(0f, 0f, w, h, r, r, fill)
            c.restore()
        }
    }
}

/** One horizontal bar split into coloured segments ("where your storage goes"); the unfilled rest is free space. */
class StackedBarView(ctx: Context, private val p: Palette) : View(ctx) {
    private var segs: List<Pair<Int, Long>> = emptyList()
    private var total = 1L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.track }
    private val gap = Paint().apply { color = p.surface; strokeWidth = context.dpf(2f) }

    fun set(segments: List<Pair<Int, Long>>, totalBytes: Long, description: String) {
        segs = segments.filter { it.second > 0 }
        total = totalBytes.coerceAtLeast(1L)
        contentDescription = description
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        c.drawRoundRect(0f, 0f, w, h, r, r, track)
        c.save()
        c.clipPath(Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) })
        var x = 0f
        for ((color, bytes) in segs) {
            val sw = w * bytes / total
            paint.color = color
            c.drawRect(x, 0f, x + sw, h, paint)
            x += sw
            c.drawLine(x, 0f, x, h, gap)
        }
        c.restore()
    }
}

/** One line over time in GB, with a highlighted last point; used for the "unattributed space" trend. */
class SingleLineChartView(ctx: Context, private val p: Palette, private val color: Int) : View(ctx) {
    class Pt(val t: Long, val gb: Double)

    private var pts: List<Pt> = emptyList()
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    private val grid = Paint().apply { this.color = p.divider; strokeWidth = context.dpf(1f) }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = p.muted; textSize = sp(11f) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@SingleLineChartView.color; style = Paint.Style.STROKE; strokeWidth = context.dpf(2.5f); strokeJoin = Paint.Join.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@SingleLineChartView.color }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = p.text; textSize = sp(12f); isFakeBoldText = true }

    fun set(list: List<Pt>, description: String) {
        pts = list
        contentDescription = description
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (pts.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val ml = context.dpf(44f); val mr = context.dpf(62f); val mt = context.dpf(10f); val mb = context.dpf(26f)
        var lo = pts.minOf { it.gb }; var hi = pts.maxOf { it.gb }
        if (hi - lo < 1.0) { lo -= 0.5; hi += 0.5 }
        val pad = (hi - lo) * 0.1; lo = maxOf(0.0, lo - pad); hi += pad
        val t0 = pts.first().t; val t1 = pts.last().t
        fun x(t: Long) = if (t1 == t0) ml + (w - ml - mr) / 2f else ml + (t - t0).toFloat() / (t1 - t0) * (w - ml - mr)
        fun y(v: Double) = (mt + (1 - (v - lo) / (hi - lo)) * (h - mt - mb)).toFloat()
        // a narrow range needs a decimal, or neighbouring labels all round to the same whole number
        val axisFmt = if (hi - lo < 6.0) "%.1f" else "%.0f"
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = y(v)
            c.drawLine(ml, yy, w - mr, yy, grid)
            c.drawText(axisFmt.format(v), context.dpf(4f), yy + sp(4f), axisText)
        }
        val fmt = if (t1 - t0 < 86_400_000L) DateFormat.getTimeInstance(DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT)
        c.drawText(fmt.format(Date(t0)), ml, h - context.dpf(6f), axisText)
        if (t1 != t0) {
            val s = fmt.format(Date(t1))
            c.drawText(s, w - mr - axisText.measureText(s), h - context.dpf(6f), axisText)
        }
        if (pts.size > 1) {
            val path = Path()
            pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(x(q.t), y(q.gb)) else path.lineTo(x(q.t), y(q.gb)) }
            c.drawPath(path, line)
        }
        for (q in pts) c.drawCircle(x(q.t), y(q.gb), context.dpf(3.5f), dot)
        val last = pts.last()
        c.drawText("%.1f".format(last.gb), x(last.t) + context.dpf(8f), y(last.gb) + sp(4f), valueText)
    }
}

/** Used/free line chart over time, the native counterpart of the SVG chart in the Windows dashboard. */
class TrendChartView(ctx: Context, private val p: Palette) : View(ctx) {
    class Pt(val t: Long, val usedGb: Double, val freeGb: Double)

    private var pts: List<Pt> = emptyList()
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    private val grid = Paint().apply { color = p.divider; strokeWidth = context.dpf(1f) }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.muted; textSize = sp(11f) }
    private val usedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.used; style = Paint.Style.STROKE; strokeWidth = context.dpf(2.5f); strokeJoin = Paint.Join.ROUND }
    private val freeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.free; style = Paint.Style.STROKE; strokeWidth = context.dpf(2.5f); strokeJoin = Paint.Join.ROUND }
    private val usedDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.used }
    private val freeDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.free }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; textSize = sp(12f); isFakeBoldText = true }

    fun set(list: List<Pt>) {
        pts = list
        val last = list.lastOrNull()
        contentDescription = if (last == null) "No data yet" else
            "Used and free space over ${list.size} snapshots. Latest: used %.1f GB, free %.1f GB.".format(last.usedGb, last.freeGb)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (pts.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val ml = context.dpf(44f); val mr = context.dpf(62f); val mt = context.dpf(10f); val mb = context.dpf(26f)
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (q in pts) { lo = minOf(lo, q.usedGb, q.freeGb); hi = maxOf(hi, q.usedGb, q.freeGb) }
        if (hi - lo < 1.0) { lo -= 0.5; hi += 0.5 }
        val pad = (hi - lo) * 0.1; lo = maxOf(0.0, lo - pad); hi += pad
        val t0 = pts.first().t; val t1 = pts.last().t
        fun x(t: Long) = if (t1 == t0) ml + (w - ml - mr) / 2f else ml + (t - t0).toFloat() / (t1 - t0) * (w - ml - mr)
        fun y(v: Double) = (mt + (1 - (v - lo) / (hi - lo)) * (h - mt - mb)).toFloat()

        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = y(v)
            c.drawLine(ml, yy, w - mr, yy, grid)
            c.drawText("%.0f".format(v), context.dpf(4f), yy + sp(4f), axisText)
        }
        val fmt = if (t1 - t0 < 86_400_000L) DateFormat.getTimeInstance(DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT)
        c.drawText(fmt.format(Date(t0)), ml, h - context.dpf(6f), axisText)
        if (t1 != t0) {
            val s = fmt.format(Date(t1))
            c.drawText(s, w - mr - axisText.measureText(s), h - context.dpf(6f), axisText)
        }
        fun line(paint: Paint, dot: Paint, sel: (Pt) -> Double) {
            if (pts.size > 1) {
                val path = Path()
                pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(x(q.t), y(sel(q))) else path.lineTo(x(q.t), y(sel(q))) }
                c.drawPath(path, paint)
            }
            for (q in pts) c.drawCircle(x(q.t), y(sel(q)), context.dpf(3.5f), dot)
        }
        line(usedLine, usedDot) { it.usedGb }
        line(freeLine, freeDot) { it.freeGb }
        val last = pts.last()
        var yu = y(last.usedGb); var yf = y(last.freeGb)
        if (Math.abs(yu - yf) < sp(14f)) { if (yu < yf) { yu -= sp(7f); yf += sp(7f) } else { yu += sp(7f); yf -= sp(7f) } }
        c.drawText("Used %.1f".format(last.usedGb), x(last.t) + context.dpf(8f), yu + sp(4f), valueText)
        c.drawText("Free %.1f".format(last.freeGb), x(last.t) + context.dpf(8f), yf + sp(4f), valueText)
    }
}
