package com.binarybug.storagetracker.scan

/** Where tapping a notification should take the user. Travels inside the notification's intent as a short string. */
sealed class AlertTarget {
    object Overview : AlertTarget()
    object Changes : AlertTarget()
    object Apps : AlertTarget()

    /** The Browse tab, opened at this folder (a path relative to shared storage, "/" is the root). */
    class Folder(val path: String) : AlertTarget()

    fun encode(): String = when (this) {
        Overview -> "overview"
        Changes -> "changes"
        Apps -> "apps"
        is Folder -> "folder:$path"
    }

    companion object {
        /** The reverse of [encode]; null for anything that is not a known target. */
        fun parse(text: String?): AlertTarget? = when {
            text == null -> null
            text == "overview" -> Overview
            text == "changes" -> Changes
            text == "apps" -> Apps
            text.startsWith("folder:") && text.length > "folder:".length -> Folder(text.removePrefix("folder:"))
            else -> null
        }
    }
}
