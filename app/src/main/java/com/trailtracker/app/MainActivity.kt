package com.trailtracker.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.trailtracker.app.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var prefs: SharedPreferences
    private var currentLocation: Location? = null
    private var setPointMarker: Marker? = null
    private var trailLine: Polyline? = null
    private var youAreHereMarker: Marker? = null
    private var isFollowing = true
    private var locationCallback: LocationCallback? = null
    private val waypointMarkers = mutableMapOf<Long, Marker>()

    // Live compass heading via rotation vector sensor — low power, event-driven
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastDisplayedHeading = -1f

    // Free, no-API-key tile sources
    private val streetSource = XYTileSource(
        "OSM_Street", 0, 19, 256, ".png",
        arrayOf("https://a.tile.openstreetmap.org/", "https://b.tile.openstreetmap.org/", "https://c.tile.openstreetmap.org/")
    )
    private val topoSource = XYTileSource(
        "OpenTopoMap", 0, 17, 256, ".png",
        arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/")
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTrackingServiceIfReady()
            requestSingleLocationUpdate()
        } else {
            Toast.makeText(this, "Location permission is required to track trails.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("trail_tracker_prefs", Context.MODE_PRIVATE)
        applySavedNightMode()

        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(16.0)

        restoreMapLayer()
        applyMapContrastForTheme()

        drawExistingTrail()
        drawExistingSetPoint()
        drawExistingWaypoints()
        centerMapOnRestore()
        updateCompassColors()
        updateNightButtonLabel()

        binding.btnLayerStreet.setOnClickListener {
            binding.map.setTileSource(streetSource)
            applyMapContrastForTheme()
            prefs.edit().putString(KEY_MAP_LAYER, LAYER_STREET).apply()
            binding.map.invalidate()
        }
        binding.btnLayerTopo.setOnClickListener {
            binding.map.setTileSource(topoSource)
            applyMapContrastForTheme()
            prefs.edit().putString(KEY_MAP_LAYER, LAYER_TOPO).apply()
            binding.map.invalidate()
        }

        binding.btnSetPoint.setOnClickListener { setPointHere() }
        binding.btnReturn.setOnClickListener { centerOnReturnBearing() }
        binding.btnDayNight.setOnClickListener { toggleNightMode() }
        binding.btnFollow.setOnClickListener { toggleFollowMode() }
        binding.btnStopRecording.setOnClickListener { promptEndTrail() }
        binding.btnAddWaypoint.setOnClickListener { addWaypointHere() }

        updateFollowButtonLabel()
        updateRecordingStatusUi()
        checkPermissionsAndStart()
    }

    private fun toggleFollowMode() {
        isFollowing = !isFollowing
        updateFollowButtonLabel()
        if (isFollowing) {
            // Snap back to current location immediately when resuming follow
            currentLocation?.let {
                binding.map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
        }
    }

    private fun updateFollowButtonLabel() {
        binding.btnFollow.text = if (isFollowing) "Stop" else "Start"
    }

    private fun applySavedNightMode() {
        val isNight = prefs.getBoolean(KEY_NIGHT_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isNight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun toggleNightMode() {
        val isCurrentlyNight = prefs.getBoolean(KEY_NIGHT_MODE, false)
        val newValue = !isCurrentlyNight
        prefs.edit().putBoolean(KEY_NIGHT_MODE, newValue).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (newValue) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        // Recreate so the theme (and map tile overlay, if desired) applies cleanly
        recreate()
    }

    private fun updateNightButtonLabel() {
        val isNight = prefs.getBoolean(KEY_NIGHT_MODE, false)
        binding.btnDayNight.text = if (isNight) "Day" else "Night"
    }

    private fun updateCompassColors() {
        val isNight = prefs.getBoolean(KEY_NIGHT_MODE, false)
        binding.compassDial.isDarkMode = isNight
    }

    /**
     * After onCreate (including the recreate() from a night-mode toggle),
     * the map starts out centered on the world default, not on the saved
     * start point or any waypoints — which made them look like they'd
     * vanished when they were really just off-screen. Center on the start
     * point if there is one, otherwise the most recent GPS fix, so the
     * map opens where the user's markers actually are.
     */
    private fun centerMapOnRestore() {
        val target = SetPointManager.current(this) ?: currentLocation
        target?.let {
            binding.map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
        }
    }

    private fun restoreMapLayer() {
        val savedLayer = prefs.getString(KEY_MAP_LAYER, LAYER_STREET)
        if (savedLayer == LAYER_TOPO) {
            binding.map.setTileSource(topoSource)
        } else {
            binding.map.setTileSource(streetSource)
        }
    }

    /**
     * Darkens the map tiles in night mode so the map actually matches the
     * app's dark theme, rather than staying bright regardless of the toggle.
     * osmdroid's built-in INVERT_COLORS filter turns bright basemap tiles
     * into a dark, low-glare map suited to night viewing.
     */
    private fun applyMapContrastForTheme() {
        val isNight = prefs.getBoolean(KEY_NIGHT_MODE, false)
        binding.map.overlayManager.getTilesOverlay().setColorFilter(
            if (isNight) TilesOverlay.INVERT_COLORS else null
        )
        binding.map.invalidate()
    }

    private fun checkPermissionsAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            startTrackingServiceIfReady()
            requestSingleLocationUpdate()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun startTrackingServiceIfReady() {
        val intent = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestSingleLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        fusedClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                currentLocation = it
                binding.map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                updateYouAreHereMarker(it)
            }
        }

        startLiveLocationUpdates()
    }

    /**
     * Continuous location updates so the map can follow the user live,
     * not just center once on launch. Balanced-power priority keeps this
     * low-drain; the map only re-centers automatically while isFollowing
     * is true, so panning the map (after pressing Stop) isn't fought.
     */
    private fun startLiveLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        if (locationCallback != null) return // already running

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L
        ).setMinUpdateIntervalMillis(3_000L).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLocation = loc
                updateYouAreHereMarker(loc)
                if (isFollowing) {
                    binding.map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                }
                // Trail is appended by the background TrackingService; redraw
                // here so the on-screen line grows live while this activity
                // is visible, and check whether we've looped back to start.
                drawExistingTrail()
                checkAutoStopAtSetPoint(loc)
            }
        }
        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, mainLooper)
    }

    /**
     * Draws/updates a small "you are here" person icon at the current
     * GPS position so there's always a clear marker for where you are
     * on the map, distinct from the set-point marker.
     */
    private fun updateYouAreHereMarker(location: Location) {
        val marker = youAreHereMarker
        if (marker == null) {
            val newMarker = Marker(binding.map)
            newMarker.position = GeoPoint(location.latitude, location.longitude)
            newMarker.title = "You are here"
            newMarker.icon = androidx.core.content.ContextCompat.getDrawable(
                this, R.drawable.ic_person_location
            )
            newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            binding.map.overlays.add(newMarker)
            youAreHereMarker = newMarker
        } else {
            marker.position = GeoPoint(location.latitude, location.longitude)
        }
        binding.map.invalidate()
    }

    /**
     * Sets the trail's Start Point. Once a Start Point exists it cannot be
     * removed except by ending the trail (Save/Delete prompt) — this is
     * intentional so the user can't accidentally lose their whole walking
     * path with a stray tap.
     */
    private fun setPointHere() {
        if (SetPointManager.current(this) != null) {
            Toast.makeText(
                this,
                "Start point already set. End the trail to clear it.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "No GPS fix yet. Wait a moment and try again.", Toast.LENGTH_SHORT).show()
            return
        }

        SetPointManager.save(this, loc)
        drawExistingSetPoint()

        TrackLog.clear(this)
        trailLine?.let { binding.map.overlays.remove(it) }
        trailLine = null
        TrackLog.startRecording(this)
        updateRecordingStatusUi()
        Toast.makeText(this, "Start point saved. Recording trail.", Toast.LENGTH_SHORT).show()
    }

    private fun updateRecordingStatusUi() {
        val recording = TrackLog.isRecording(this)
        binding.recordingStatus.text = if (recording) "Recording trail" else "Not recording"
        binding.btnStopRecording.isEnabled = recording
    }

    /**
     * Auto-stops recording once the user crosses back over their start
     * point (within GPS-accuracy range). This only stops the log — it does
     * not clear anything, so the Save/Delete choice still happens when the
     * user presses End Trail afterward.
     */
    private fun checkAutoStopAtSetPoint(loc: Location) {
        if (!TrackLog.isRecording(this)) return
        val sp = SetPointManager.current(this) ?: return
        if (loc.distanceTo(sp) <= AUTO_STOP_RADIUS_METERS) {
            TrackLog.stopRecording(this)
            updateRecordingStatusUi()
            Toast.makeText(this, "Back at start point — trail recording stopped.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Ends the trail: if there's nothing recorded yet, just stops quietly.
     * Otherwise asks whether to Save (keep the start point, trail log, and
     * waypoints, just stop recording) or Delete (clear everything — start
     * point, trail log, and waypoints) this track.
     */
    private fun promptEndTrail() {
        TrackLog.stopRecording(this)
        updateRecordingStatusUi()

        if (SetPointManager.current(this) == null && TrackLog.readPoints(this).isEmpty()) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle("End trail")
            .setMessage("Save this track, or delete it?")
            .setPositiveButton("Save") { _, _ ->
                Toast.makeText(this, "Trail saved.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Delete") { _, _ ->
                deleteEntireTrack()
            }
            .setCancelable(true)
            .show()
    }

    /** Clears the start point, the recorded trail log, and all waypoints. */
    private fun deleteEntireTrack() {
        SetPointManager.clear(this)
        TrackLog.clear(this)
        for (wp in WaypointManager.all(this)) {
            WaypointManager.delete(this, wp.id)
        }

        setPointMarker?.let { binding.map.overlays.remove(it) }
        setPointMarker = null
        trailLine?.let { binding.map.overlays.remove(it) }
        trailLine = null
        for (marker in waypointMarkers.values) {
            binding.map.overlays.remove(marker)
        }
        waypointMarkers.clear()

        binding.map.invalidate()
        Toast.makeText(this, "Track deleted.", Toast.LENGTH_SHORT).show()
    }

    private fun drawExistingSetPoint() {
        val sp = SetPointManager.current(this) ?: return
        setPointMarker?.let { binding.map.overlays.remove(it) }
        val marker = Marker(binding.map)
        marker.position = GeoPoint(sp.latitude, sp.longitude)
        marker.title = "Start Point"
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_start_point)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setOnMarkerClickListener { _, _ ->
            showStartPointNoteDialog()
            true
        }
        binding.map.overlays.add(marker)
        setPointMarker = marker
        binding.map.invalidate()
    }

    private fun showStartPointNoteDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.setText(SetPointManager.note(this))
        input.hint = "Note for start point"

        AlertDialog.Builder(this)
            .setTitle("Start point note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                SetPointManager.saveNote(this, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Drops a new waypoint marker at the current GPS position and asks for a note. */
    private fun addWaypointHere() {
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "No GPS fix yet. Wait a moment and try again.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.hint = "Note for this waypoint (optional)"

        AlertDialog.Builder(this)
            .setTitle("Add waypoint")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val wp = WaypointManager.add(this, loc.latitude, loc.longitude, input.text.toString())
                drawWaypointMarker(wp)
                Toast.makeText(this, "Waypoint added.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun drawExistingWaypoints() {
        for (marker in waypointMarkers.values) {
            binding.map.overlays.remove(marker)
        }
        waypointMarkers.clear()
        for (wp in WaypointManager.all(this)) {
            drawWaypointMarker(wp)
        }
        binding.map.invalidate()
    }

    private fun drawWaypointMarker(wp: Waypoint) {
        waypointMarkers[wp.id]?.let { binding.map.overlays.remove(it) }
        val marker = Marker(binding.map)
        marker.position = GeoPoint(wp.lat, wp.lon)
        marker.title = "Waypoint"
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_waypoint)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setOnMarkerClickListener { _, _ ->
            showWaypointDialog(wp.id)
            true
        }
        binding.map.overlays.add(marker)
        waypointMarkers[wp.id] = marker
        binding.map.invalidate()
    }

    /** Tapping a waypoint shows its note with options to edit or delete it. */
    private fun showWaypointDialog(id: Long) {
        val wp = WaypointManager.all(this).find { it.id == id } ?: return
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.setText(wp.note)

        AlertDialog.Builder(this)
            .setTitle("Waypoint note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                WaypointManager.updateNote(this, id, input.text.toString())
            }
            .setNegativeButton("Delete") { _, _ ->
                WaypointManager.delete(this, id)
                waypointMarkers.remove(id)?.let { binding.map.overlays.remove(it) }
                binding.map.invalidate()
                Toast.makeText(this, "Waypoint deleted.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun drawExistingTrail() {
        val points = TrackLog.readPoints(this)
        if (points.isEmpty()) return
        trailLine?.let { binding.map.overlays.remove(it) }
        val line = Polyline()
        line.setPoints(points.map { GeoPoint(it[0], it[1]) })
        binding.map.overlays.add(line)
        trailLine = line
        binding.map.invalidate()
    }

    private fun centerOnReturnBearing() {
        val loc = currentLocation
        val sp = SetPointManager.current(this)
        if (loc == null || sp == null) {
            Toast.makeText(this, "Need both a GPS fix and a saved set point.", Toast.LENGTH_SHORT).show()
            return
        }
        val distance = loc.distanceTo(sp)
        val bearing = loc.bearingTo(sp)
        Toast.makeText(
            this,
            "Set point: ${distance.toInt()} m away, bearing ${bearing.toInt()}°",
            Toast.LENGTH_LONG
        ).show()
        binding.map.controller.animateTo(GeoPoint(sp.latitude.toDouble(), sp.longitude.toDouble()))
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        rotationSensor?.let {
            // UI-rate updates: smooth enough for a compass without draining battery
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        updateRecordingStatusUi()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val azimuthRad = orientationAngles[0]
        var headingDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
        if (headingDeg < 0) headingDeg += 360f

        // Skip near-identical updates to avoid redundant redraws
        if (Math.abs(headingDeg - lastDisplayedHeading) < 0.5f) return
        lastDisplayedHeading = headingDeg
        binding.compassDial.setHeading(headingDeg)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed — rotation vector accuracy changes are rare and self-correcting
    }

    companion object {
        private const val KEY_NIGHT_MODE = "night_mode_enabled"
        private const val KEY_MAP_LAYER = "map_layer"
        private const val LAYER_STREET = "street"
        private const val LAYER_TOPO = "topo"
        // Treat "crossed back over the start point" as within this radius,
        // since GPS fixes rarely land on the exact same coordinate twice.
        private const val AUTO_STOP_RADIUS_METERS = 5f
    }
}
