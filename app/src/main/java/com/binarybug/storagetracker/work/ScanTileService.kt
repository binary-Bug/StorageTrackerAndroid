package com.binarybug.storagetracker.work

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.binarybug.storagetracker.MainActivity
import com.binarybug.storagetracker.scan.ScanController

/** Quick Settings tile "Scan storage": one tap from the notification shade starts a scan, no need to open the app. */
class ScanTileService : TileService() {
    private val listener = ScanController.Listener { render() }

    override fun onStartListening() {
        ScanController.listeners.add(listener)
        render()
    }

    override fun onStopListening() {
        ScanController.listeners.remove(listener)
    }

    override fun onClick() {
        when {
            ScanController.running -> {} // already scanning; the tile shows it
            !Environment.isExternalStorageManager() -> openApp() // needs the permission first, so show the app
            else -> { Scheduler.scanNow(this); render(true) }
        }
    }

    private fun render(starting: Boolean = false) {
        val tile = qsTile ?: return
        val busy = starting || ScanController.running
        tile.state = if (busy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Scan storage"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (busy) "Scanning…" else "Tap to scan"
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
