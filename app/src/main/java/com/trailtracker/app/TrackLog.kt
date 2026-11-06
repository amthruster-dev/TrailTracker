package com.trailtracker.app

import android.content.Context
import android.location.Location
import java.io.File

/**
 * Appends GPS fixes to a simple GPX-ish CSV on disk so the trail can be
 * redrawn even if the app restarts. Lightweight — no DB needed.
 *
 * Recording only happens while isRecording() is true: it starts when the
 * user sets a Set Point, and stops either manually or automatically when
 * they cross back over that point. appendPoint() is a no-op while not
 * recording, so the trail only reflects the actual logged hike, not every
 * GPS fix since app launch.
 */
object TrackLog {
    private const val FILE_NAME = "track_log.csv"
    private const val PREFS = "trail_tracker_prefs"
    private const val KEY_RECORDING = "is_recording"

    fun appendPoint(context: Context, location: Location) {
        if (!isRecording(context)) return
        val file = File(context.filesDir, FILE_NAME)
        file.appendText("${location.latitude},${location.longitude},${location.time}\n")
    }

    fun isRecording(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RECORDING, false)

    fun startRecording(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RECORDING, true).apply()
    }

    fun stopRecording(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RECORDING, false).apply()
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
