package com.trailtracker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Waypoint(
    val id: Long,
    val lat: Double,
    val lon: Double,
    var note: String
)

/**
 * Stores user-dropped waypoints (markers with attached notes) separately
 * from the trail's Start point. Waypoints can be freely added, edited,
 * and deleted without affecting the Start point or the recorded trail
 * log — deleting one only removes that marker and its note.
 */
object WaypointManager {
    private const val FILE_NAME = "waypoints.json"

    fun all(context: Context): List<Waypoint> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Waypoint(
                    id = obj.getLong("id"),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                    note = obj.optString("note", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, lat: Double, lon: Double, note: String = ""): Waypoint {
        val list = all(context).toMutableList()
        val newWaypoint = Waypoint(id = System.currentTimeMillis(), lat = lat, lon = lon, note = note)
        list.add(newWaypoint)
        saveAll(context, list)
        return newWaypoint
    }

    fun updateNote(context: Context, id: Long, note: String) {
        val list = all(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index == -1) return
        list[index] = list[index].copy(note = note)
        saveAll(context, list)
    }

    fun delete(context: Context, id: Long) {
        val list = all(context).filterNot { it.id == id }
        saveAll(context, list)
    }

    private fun saveAll(context: Context, list: List<Waypoint>) {
        val arr = JSONArray()
        for (wp in list) {
            val obj = JSONObject()
            obj.put("id", wp.id)
            obj.put("lat", wp.lat)
            obj.put("lon", wp.lon)
            obj.put("note", wp.note)
            arr.put(obj)
        }
        File(context.filesDir, FILE_NAME).writeText(arr.toString())
    }
}
