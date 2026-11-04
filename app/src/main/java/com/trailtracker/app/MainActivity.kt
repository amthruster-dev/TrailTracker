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
import android.widget.Toast
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

        checkPermissionsAndStart()
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
        binding.compassStrip.isDarkMode = isNight
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
            }
        }
    }

    private fun setPointHere() {
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "No GPS fix yet. Wait a moment and try again.", Toast.LENGTH_SHORT).show()
            return
        }
        SetPointManager.save(this, loc)
        drawExistingSetPoint()
        Toast.makeText(this, "Set point saved.", Toast.LENGTH_SHORT).show()
    }

    private fun drawExistingSetPoint() {
        val sp = SetPointManager.current(this) ?: return
        setPointMarker?.let { binding.map.overlays.remove(it) }
        val marker = Marker(binding.map)
        marker.position = GeoPoint(sp.latitude, sp.longitude)
        marker.title = "Set Point"
        binding.map.overlays.add(marker)
        setPointMarker = marker
        binding.map.invalidate()
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
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        sensorManager.unregisterListener(this)
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
        binding.compassStrip.setHeading(headingDeg)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed — rotation vector accuracy changes are rare and self-correcting
    }

    companion object {
        private const val KEY_NIGHT_MODE = "night_mode_enabled"
        private const val KEY_MAP_LAYER = "map_layer"
        private const val LAYER_STREET = "street"
        private const val LAYER_TOPO = "topo"
    }
}
