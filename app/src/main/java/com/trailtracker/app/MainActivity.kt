package com.trailtracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.trailtracker.app.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var setPointMarker: Marker? = null
    private var trailLine: Polyline? = null

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
        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        binding.map.setTileSource(streetSource)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(16.0)

        drawExistingTrail()
        drawExistingSetPoint()

        binding.btnLayerStreet.setOnClickListener {
            binding.map.setTileSource(streetSource)
            binding.map.invalidate()
        }
        binding.btnLayerTopo.setOnClickListener {
            binding.map.setTileSource(topoSource)
            binding.map.invalidate()
        }

        binding.btnSetPoint.setOnClickListener { setPointHere() }
        binding.btnReturn.setOnClickListener { centerOnReturnBearing() }

        checkPermissionsAndStart()
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
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}
