package com.binarybug.storagetracker.ui

import android.view.View

/** One bottom-navigation destination. [refresh] rebuilds it from the current [com.binarybug.storagetracker.Model]. */
interface Screen {
    val root: View
    fun refresh()
}

/** What screens may ask the host activity to do. */
interface Actions {
    fun goToTab(index: Int)
    fun requestScan()
    fun requestNotificationPermission()
    fun openAllFilesSettings()
    fun openUsageAccessSettings()
    fun openBatterySettings()
    fun openAppSettings()

    /** Opens Android's own "App info" page for [pkg], where it can be cleared, force-stopped or uninstalled. */
    fun openAppInfo(pkg: String)

    /** Opens Android's own storage page (Settings > Storage), which has its own clean-up tools. */
    fun openStorageSettings()

    /** Asks Android to add the "Scan storage" Quick Settings tile (Android 13+), or explains how to add it by hand. */
    fun addQuickTile()

    /** Saves every snapshot into one zip file in a place the user picks (Downloads, Drive, ...). */
    fun exportSnapshots()

    /** Adds the snapshots from a zip made by [exportSnapshots]; snapshots already on the phone are kept as they are. */
    fun importSnapshots()

    /** Shares a plain-text summary of the comparison currently shown. */
    fun shareReport()
    fun openInBrowse(path: String)

    /** Opens Browse > Categories, optionally straight into one file category (key like "cat:IMAGES"). */
    fun openCategory(key: String?)
}
