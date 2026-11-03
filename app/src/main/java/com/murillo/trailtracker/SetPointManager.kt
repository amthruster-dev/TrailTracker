package com.murillo.trailtracker

import android.content.Context
import android.location.Location

/**
 * Stores the user's "set point" (trailhead / parking / camp) in SharedPreferences
 * so return-to-point works even after the app is killed and restarted.
 */
object SetPointManager {
    private const val PREFS = "trail_tracker_prefs"
    private const val KEY_LAT = "set_point_lat"
    private const val KEY_LON = "set_point_lon"

    fun save(context: Context, location: Location) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LON, location.longitude.toFloat())
            .apply()
    }

    fun current(context: Context): Location? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAT)) return null
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        return Location("set_point").apply {
            latitude = lat
            longitude = lon
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
