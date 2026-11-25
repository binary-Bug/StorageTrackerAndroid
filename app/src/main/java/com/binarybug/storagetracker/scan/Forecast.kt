package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.formatBytes

/**
 * Straight-line estimate of when free space runs out, from the free-space readings of the recent snapshots.
 *
 * One-time jumps (installing a big app, deleting a download, copying a video) are not a pace, so they are left out of the
 * line: a change between two snapshots counts as a jump when it is both large and far bigger than the usual change between
 * snapshots. What remains is the steady drift, and [daysLeft] is based on that.
 */
class Forecast(
    val perDayBytes: Double,
    val daysLeft: Double?,
    val basedOn: Int,
    val spanDays: Double,
    val steps: List<Step> = emptyList(),
) {
    /** A one-time jump in free space between two snapshots; [bytes] is negative when free space dropped. */
    class Step(val atMs: Long, val bytes: Long)

    /** True when free space is clearly shrinking and will run out within [WARN_DAYS]. */
    val warns: Boolean get() = daysLeft != null && daysLeft <= WARN_DAYS

    /** A sentence about the jumps that were left out, or null when there were none. [ago] turns a time into text like "3 min ago". */
    fun stepNote(ago: (Long) -> String): String? {
        val last = steps.lastOrNull() ?: return null
        val change = (if (last.bytes < 0) "drop" else "rise") + " of " + formatBytes(Math.abs(last.bytes))
        return if (steps.size == 1) "Ignoring a one-time $change (${ago(last.atMs)})."
        else "Ignoring ${steps.size} one-time jumps; the latest was a $change (${ago(last.atMs)})."
    }

    companion object {
        const val WINDOW_DAYS = 14L
        const val MIN_POINTS = 3
        const val MIN_SPAN_DAYS = 0.5
        const val WARN_DAYS = 30.0
        const val MAX_DAYS = 3650.0
        private const val DAY_MS = 86_400_000.0
        /** Slower than this (about 1 MB a day) counts as stable. */
        private const val STABLE_BYTES_PER_DAY = 1_000_000.0
        /** A change smaller than this is never a jump. */
        private const val STEP_MIN_BYTES = 500_000_000L
        /** A jump must also be at least this many times the usual (median) change between snapshots. */
        private const val STEP_FACTOR = 5.0

        /** [metas] is sorted oldest first. Returns null when there is not enough history (3+ snapshots over at least 12 hours). */
        fun of(metas: List<SnapshotMeta>, nowMs: Long): Forecast? {
            val recent = metas.filter { it.takenMs >= nowMs - WINDOW_DAYS * DAY_MS.toLong() }
            if (recent.size < MIN_POINTS) return null
            val t0 = recent.first().takenMs
            val span = (recent.last().takenMs - t0) / DAY_MS
            if (span < MIN_SPAN_DAYS) return null

            val deltas = (1 until recent.size).map { recent[it].free - recent[it - 1].free }
            val gapDays = (1 until recent.size).map { (recent[it].takenMs - recent[it - 1].takenMs) / DAY_MS }
            val limit = maxOf(STEP_MIN_BYTES.toDouble(), STEP_FACTOR * median(deltas.map { Math.abs(it).toDouble() }))
            val isStep = deltas.map { Math.abs(it) >= limit }

            // the steady pace is the change over the intervals that are not jumps (ratio of sums, so a few seconds-long gaps do not skew it)
            var calmDelta = 0.0
            var calmDays = 0.0
            for (k in deltas.indices) if (!isStep[k]) { calmDelta += deltas[k]; calmDays += gapDays[k] }
            val calmRate = if (calmDays > 0) calmDelta / calmDays else 0.0

            val steps = ArrayList<Step>()
            val ys = DoubleArray(recent.size)
            ys[0] = recent[0].free.toDouble()
            for (i in 1 until recent.size) {
                val k = i - 1
                if (isStep[k]) {
                    steps.add(Step(recent[i].takenMs, deltas[k]))
                    ys[i] = ys[i - 1] + calmRate * gapDays[k] // the jump is replaced by the normal drift for that stretch of time
                } else {
                    ys[i] = ys[i - 1] + deltas[k]
                }
            }

            val xs = recent.map { (it.takenMs - t0) / DAY_MS }
            val mx = xs.average()
            val my = ys.average()
            var sxy = 0.0
            var sxx = 0.0
            for (i in xs.indices) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx) }
            if (sxx == 0.0) return null
            val slope = sxy / sxx // bytes per day, negative while free space shrinks
            val left = if (slope <= -STABLE_BYTES_PER_DAY) (recent.last().free / -slope).takeIf { it <= MAX_DAYS } else null
            return Forecast(slope, left, recent.size, span, steps)
        }

        private fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
    }
}
