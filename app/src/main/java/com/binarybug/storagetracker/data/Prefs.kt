package com.binarybug.storagetracker.data

import android.content.Context

/** User settings and the record of the last run, in SharedPreferences. */
object Prefs {
    class LastRun(val startMs: Long, val endMs: Long, val trigger: String, val status: String, val summary: String)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("storage_tracker", Context.MODE_PRIVATE)

    fun scheduleEnabled(c: Context) = sp(c).getBoolean("sched_on", true)
    fun setScheduleEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("sched_on", v).apply()

    fun intervalMin(c: Context) = sp(c).getInt("interval_min", 120)
    fun setIntervalMin(c: Context, v: Int) = sp(c).edit().putInt("interval_min", v).apply()

    fun retentionDays(c: Context) = sp(c).getInt("retention_days", 7)
    fun setRetentionDays(c: Context, v: Int) = sp(c).edit().putInt("retention_days", v).apply()

    /** Free-space drop that triggers a notification; 0 turns alerts off. */
    fun alertDropGb(c: Context) = sp(c).getFloat("alert_drop_gb", 2f)
    fun setAlertDropGb(c: Context, v: Float) = sp(c).edit().putFloat("alert_drop_gb", v).apply()

    /** Once-a-day notice when less than 10% of the storage is free. */
    fun lowSpaceNotice(c: Context) = sp(c).getBoolean("low_notice", true)
    fun setLowSpaceNotice(c: Context, v: Boolean) = sp(c).edit().putBoolean("low_notice", v).apply()
    fun lastLowNoticeMs(c: Context) = sp(c).getLong("low_notice_ms", 0L)
    fun setLastLowNoticeMs(c: Context, v: Long) = sp(c).edit().putLong("low_notice_ms", v).apply()

    /** An app or folder growing by this much between two scans triggers a notification; 0 turns it off. */
    fun growthAlertGb(c: Context) = sp(c).getFloat("growth_alert_gb", 1f)
    fun setGrowthAlertGb(c: Context, v: Float) = sp(c).edit().putFloat("growth_alert_gb", v).apply()

    fun scheduledAtMs(c: Context) = sp(c).getLong("scheduled_at", 0L)
    fun setScheduledAtMs(c: Context, v: Long) = sp(c).edit().putLong("scheduled_at", v).apply()

    fun askedNotificationPermission(c: Context) = sp(c).getBoolean("asked_notif", false)
    fun setAskedNotificationPermission(c: Context) = sp(c).edit().putBoolean("asked_notif", true).apply()

    /** End time of the last scan started by the scheduler, used to estimate the next run. */
    fun lastScheduledEndMs(c: Context) = sp(c).getLong("last_sched_end", 0L)

    fun lastRun(c: Context): LastRun? {
        val s = sp(c)
        if (!s.contains("last_end")) return null
        return LastRun(
            s.getLong("last_start", 0L), s.getLong("last_end", 0L),
            s.getString("last_trigger", "") ?: "", s.getString("last_status", "") ?: "", s.getString("last_summary", "") ?: "",
        )
    }

    fun recordRun(c: Context, startMs: Long, endMs: Long, trigger: String, status: String, summary: String) {
        val e = sp(c).edit()
            .putLong("last_start", startMs).putLong("last_end", endMs)
            .putString("last_trigger", trigger).putString("last_status", status).putString("last_summary", summary)
        if (trigger == "scheduled") e.putLong("last_sched_end", endMs)
        e.apply()
    }
}
