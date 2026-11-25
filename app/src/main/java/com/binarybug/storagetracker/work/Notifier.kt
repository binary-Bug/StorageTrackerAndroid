package com.binarybug.storagetracker.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.binarybug.storagetracker.MainActivity
import com.binarybug.storagetracker.R
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.scan.AlertRules
import com.binarybug.storagetracker.scan.AlertTarget

/** Free-space-drop alert, the Android equivalent of the Windows toast. Uses only framework notifications. */
object Notifier {
    private const val CHANNEL = "free_space"
    private const val ID_DROP = 2001
    private const val ID_LOW = 2002
    private const val ID_GROWTH = 2003

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Free space alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when free storage drops sharply, runs low, or an app or folder grows a lot."
                },
            )
        }
    }

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun freeSpaceDrop(c: Context, dropBytes: Long, freeNow: Long) =
        post(c, ID_DROP, "Free storage dropped by ${formatBytes(dropBytes)}", "${formatBytes(freeNow)} free now. Tap to see what grew.", AlertTarget.Changes)

    fun lowSpace(c: Context, freeNow: Long, total: Long) =
        post(c, ID_LOW, "Storage is almost full", "Only ${formatBytes(freeNow)} free of ${formatBytes(total)} (under 10%). Tap to see what is using it.", AlertTarget.Overview)

    /** Tapping opens what grew the most: that folder in Browse, or the Apps tab for an app. */
    fun growth(c: Context, items: List<AlertRules.Growth>) {
        val top = items.firstOrNull() ?: return
        val rest = items.drop(1).joinToString(", ") { "${it.label} +${formatBytes(it.bytes)}" }
        post(c, ID_GROWTH, "${top.label} grew by ${formatBytes(top.bytes)}", if (rest.isEmpty()) "Tap to see it." else "Also: $rest", top.target)
    }

    private fun post(c: Context, id: Int, title: String, text: String, target: AlertTarget) {
        if (!canNotify(c)) return
        ensureChannel(c)
        // one PendingIntent per alert (request code = id) that updates its extras, so each alert opens its own screen
        val intent = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_TARGET, target.encode())
        val open = PendingIntent.getActivity(c, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_storage)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
