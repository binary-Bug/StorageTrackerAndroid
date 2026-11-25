package com.binarybug.storagetracker.work

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Environment
import com.binarybug.storagetracker.data.Prefs
import com.binarybug.storagetracker.data.SnapshotStore
import com.binarybug.storagetracker.scan.ScanController

/** Called by Android's JobScheduler on the schedule set up in [Scheduler]. Starts the same scan as the Scan now button. */
private const val MIN_GAP_MS = 30 * 60_000L

class ScanJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (!Environment.isExternalStorageManager()) {
            val now = System.currentTimeMillis()
            Prefs.recordRun(this, now, now, "scheduled", "skipped", "All files access is not granted.")
            return false
        }
        val manual = params.extras.getBoolean(Scheduler.EXTRA_MANUAL, false)
        if (manual) return ScanController.start(this, "tile") { jobFinished(params, false) }
        // Android runs a newly registered periodic job immediately and the app re-registers it on each start,
        // so skip when a snapshot is already fresh. Manual scans are never skipped.
        val newest = SnapshotStore.listMeta(this).lastOrNull()
        if (newest != null && System.currentTimeMillis() - newest.takenMs < MIN_GAP_MS) {
            val now = System.currentTimeMillis()
            Prefs.recordRun(this, now, now, "scheduled", "skipped", "Newest snapshot is under 30 minutes old.")
            return false
        }
        // true = work continues on the scan thread; jobFinished() is called when it ends. false = a scan is already running.
        return ScanController.start(this, "scheduled") { jobFinished(params, false) }
    }

    /** Android is stopping the job (time limit, battery, conditions): stop scanning and ask to be rescheduled. */
    override fun onStopJob(params: JobParameters): Boolean {
        ScanController.cancel()
        return true
    }
}

object Scheduler {
    private const val JOB_ID = 1001
    private const val NOW_JOB_ID = 1002
    const val EXTRA_MANUAL = "manual"

    private fun js(c: Context) = c.getSystemService(JobScheduler::class.java)

    /** Runs one scan as soon as possible (used by the Quick Settings tile). Expedited, so Android keeps the app alive for it. */
    fun scanNow(c: Context) {
        val extras = android.os.PersistableBundle().apply { putBoolean(EXTRA_MANUAL, true) }
        val info = JobInfo.Builder(NOW_JOB_ID, ComponentName(c, ScanJobService::class.java))
            .setExpedited(true)
            .setExtras(extras)
            .build()
        js(c).schedule(info)
    }

    fun pending(c: Context): JobInfo? = js(c).getPendingJob(JOB_ID)

    /** Registers (or re-registers) the periodic job. Android enforces a minimum period of 15 minutes. */
    fun schedule(c: Context) {
        val minutes = Prefs.intervalMin(c).coerceAtLeast(15)
        val info = JobInfo.Builder(JOB_ID, ComponentName(c, ScanJobService::class.java))
            .setPeriodic(minutes * 60_000L)
            .setPersisted(true)
            .setRequiresBatteryNotLow(true)
            .build()
        js(c).schedule(info)
        Prefs.setScheduledAtMs(c, System.currentTimeMillis())
    }

    fun cancel(c: Context) { js(c).cancel(JOB_ID) }

    /** Makes the registered job match the settings, without resetting the timer when nothing changed. */
    fun ensure(c: Context) {
        val wanted = Prefs.scheduleEnabled(c)
        val current = pending(c)
        when {
            !wanted -> if (current != null) cancel(c)
            current == null || current.intervalMillis != Prefs.intervalMin(c).coerceAtLeast(15) * 60_000L -> schedule(c)
        }
    }
}
