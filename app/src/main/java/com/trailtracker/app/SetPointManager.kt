package com.trailtracker.app

import android.content.Context
import android.location.Location

/**
 * Stores the user's Start Point (trailhead / parking / camp) in
 * SharedPreferences so Return-to-point works even after the app is killed
 * and restarted. The Start Point is intentionally NOT deletable through
 * the normal UI once a trail is being recorded — it can only be cleared
 * as part of ending/discarding the whole trail (see MainActivity's
 * End Trail -> Save/Delete prompt), so it can't be lost by an accidental
 * tap. Waypoints (separate markers with notes) are handled by
 * WaypointManager and can be freely added/removed independently.
 */
object SetPointManager {
    private const val PREFS = "trail_tracker_prefs"
    private const val KEY_LAT = "set_point_lat"
    private const val KEY_LON = "set_point_lon"
    private const val KEY_NOTE = "set_point_note"

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

    fun note(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTE, "") ?: ""

    fun saveNote(context: Context, note: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NOTE, note)
            .apply()
    }

    /** Clears the Start Point and its note. Does NOT touch night mode/map layer prefs. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAT)
            .remove(KEY_LON)
            .remove(KEY_NOTE)
            .apply()
    }
}
