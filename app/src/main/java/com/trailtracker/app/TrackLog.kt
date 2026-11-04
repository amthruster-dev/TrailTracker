package com.trailtracker.app

import android.content.Context
import android.location.Location
import java.io.File

/**
 * Appends GPS fixes to a simple GPX-ish CSV on disk so the trail can be
 * redrawn even if the app restarts. Lightweight — no DB needed.
 */
object TrackLog {
    private const val FILE_NAME = "track_log.csv"

    fun appendPoint(context: Context, location: Location) {
        val file = File(context.filesDir, FILE_NAME)
        file.appendText("${location.latitude},${location.longitude},${location.time}\n")
    }

    fun readPoints(context: Context): List<DoubleArray> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 2) {
                doubleArrayOf(parts[0].toDouble(), parts[1].toDouble())
            } else null
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
