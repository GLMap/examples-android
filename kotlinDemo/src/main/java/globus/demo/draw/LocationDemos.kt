package globus.demo.draw

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapAnimation
import globus.glmap.GLMapDrawable
import globus.glmap.GLMapImage
import globus.glmap.GLMapTrack
import globus.glmap.GLMapTrackData
import globus.glmap.GLMapVectorCascadeStyle
import globus.glmap.GLMapVectorLayer
import globus.glmap.GLMapVectorStyle
import globus.glmap.GeometryBuilder
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import globus.glmap.SVGRender
import kotlin.math.cos
import kotlin.math.sin

abstract class LocationMapActivity : MapDemoActivity() {
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var locationImage: GLMapImage
    private lateinit var accuracyCircle: GLMapVectorLayer
    private var locationAnimation: GLMapAnimation? = null
    private var firstLocation = true
    private var objectsReady = false
    private var started = false
    private var requestedPermission = false
    private var requestGeneration = 0
    private var lastFixNanos = Long.MIN_VALUE
    internal var locationUpdatesRequested = false
        private set

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (started) {
            if (hasLocationPermission()) startLocations() else locationUnavailable()
        }
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (started && locationUpdatesRequested) result.locations.forEach(::showLocation)
        }
        override fun onLocationAvailability(availability: LocationAvailability) {
            if (started && !availability.isLocationAvailable) {
                supportActionBar?.subtitle = "Location unavailable. Check device location settings."
            }
        }
    }

    final override fun onMapReady() {
        setupDemo()
        objectsReady = setupLocationObjects()
    }

    override fun onStart() {
        super.onStart()
        started = true
        if (!objectsReady) return
        if (hasLocationPermission()) {
            startLocations()
        } else if (!requestedPermission) {
            requestedPermission = true
            permissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            locationUnavailable()
        }
    }

    override fun onStop() {
        started = false
        requestGeneration++
        locationUpdatesRequested = false
        locationClient.removeLocationUpdates(locationCallback)
        locationAnimation?.cancel(false)
        didStopLocations()
        super.onStop()
    }

    protected abstract fun setupDemo()
    protected open fun didUpdateLocation(location: Location) = Unit
    protected open fun updateCameraForLocation(location: Location) = Unit
    protected open fun didStopLocations() = Unit

    private fun setupLocationObjects(): Boolean {
        val bitmap = SVGRender.render(assets, "circle_new.svg", SVGRender.transform(renderer.screenScale.toDouble()))
        if (bitmap == null) {
            showError("Cannot render user location SVG")
            return false
        }
        locationImage = GLMapImage(100).apply {
            setBitmap(bitmap)
            setOffset(bitmap.width / 2, bitmap.height / 2)
            isHidden = true
            renderer.add(this)
        }
        val points = Array(64) { index ->
            val angle = 2 * Math.PI * index / 64
            MapPoint(sin(angle) * 2048, cos(angle) * 2048)
        }
        accuracyCircle = GLMapVectorLayer(99).apply {
            setTransformMode(GLMapDrawable.TransformMode.Custom)
            GeometryBuilder().use { builder ->
                builder.beginPolygon()
                builder.addLine(points)
                builder.build()!!
            }.use { polygon ->
                GLMapVectorCascadeStyle.createStyle(
                    "area{width:1pt;fill-color:#3D99FA26;color:#3D99FA66;}"
                )!!.use { style ->
                    setVectorObject(polygon, style, null)
                }
            }
            isHidden = true
            renderer.add(this)
        }
        return true
    }

    private fun showLocation(location: Location) {
        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy < 0 ||
            !location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.elapsedRealtimeNanos <= lastFixNanos
        ) return
        lastFixNanos = location.elapsedRealtimeNanos
        supportActionBar?.subtitle = null
        val position = MapPoint.CreateFromGeoCoordinates(location.latitude, location.longitude)
        val accuracyScale = renderer.convertMetersToInternal(location.accuracy.toDouble()) / 2048.0
        locationAnimation?.cancel(false)
        if (firstLocation) {
            firstLocation = false
            locationImage.position = position
            locationImage.isHidden = false
            accuracyCircle.position = position
            accuracyCircle.scale = accuracyScale
            accuracyCircle.isHidden = false
            renderer.mapCenter = position
            updateCameraForLocation(location)
        } else {
            locationAnimation = renderer.animate { animation ->
                animation.setTransition(GLMapAnimation.Linear)
                animation.setDuration(1.0)
                animation.setPosition(locationImage, position)
                animation.setPosition(accuracyCircle, position)
                animation.setScale(accuracyCircle, accuracyScale)
                renderer.mapCenter = position
                updateCameraForLocation(location)
            }
        }
        didUpdateLocation(location)
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locationUnavailable() {
        supportActionBar?.subtitle = "Allow location access in Settings → Apps → GLMap → Permissions."
    }

    @SuppressLint("MissingPermission")
    private fun startLocations() {
        if (!started || !objectsReady || locationUpdatesRequested || !hasLocationPermission()) return
        val generation = ++requestGeneration
        locationUpdatesRequested = true
        supportActionBar?.subtitle = "Waiting for location…"
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000).build()
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    if (started && generation == requestGeneration) {
                        locationUpdatesRequested = false
                        supportActionBar?.subtitle = error.localizedMessage ?: "Cannot start location updates"
                        showError("Cannot start location updates. Check device location settings.")
                    }
                }
        } catch (_: SecurityException) {
            locationUpdatesRequested = false
            locationUnavailable()
        }
    }
}

class UserLocationActivity : LocationMapActivity() {
    override fun setupDemo() {
        title = "User Location"
        renderer.mapGeoCenter = MapGeoPoint(60.3913, 5.3221)
        renderer.mapZoom = 14.0
    }
}

class GPSTrackActivity : LocationMapActivity() {
    private val track = GLMapTrack(2)
    private val style = GLMapVectorStyle.createStyle("{width:5pt;}")!!
    private var trackData: GLMapTrackData? = null
    private var startNewSegment = false

    override fun setupDemo() {
        title = "GPS Track"
        renderer.mapGeoCenter = MapGeoPoint(43.3183, -1.9812)
        renderer.mapZoom = 15.0
        renderer.add(track)
    }

    override fun didStopLocations() {
        // Only an actual pause in recording starts a new segment, not every hundredth fix.
        startNewSegment = trackData != null
    }

    override fun didUpdateLocation(location: Location) {
        val previous = trackData
        val updated = previous?.copyTrackAndAddGeoPoint(
            location.latitude,
            location.longitude,
            Color.YELLOW,
            startNewSegment
        ) ?: GLMapTrackData(
            { _, point ->
                GLMapTrackData.setPointDataGeo(point, location.latitude, location.longitude, Color.YELLOW)
            },
            1
        )
        track.setData(updated, style, null)
        trackData = updated
        startNewSegment = false
        previous?.dispose()
    }

    override fun updateCameraForLocation(location: Location) {
        // Both Android bearings and mapAngle are clockwise degrees from north.
        if (location.hasBearing()) renderer.mapAngle = location.bearing
    }

    override fun onDestroy() {
        super.onDestroy()
        trackData?.dispose()
        track.dispose()
        style.dispose()
    }
}
