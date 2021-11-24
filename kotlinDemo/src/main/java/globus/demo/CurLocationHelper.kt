package globus.demo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import globus.glmap.*
import kotlin.math.cos
import kotlin.math.sin

/** Created by destman on 6/1/17.  */
internal class CurLocationHelper(private val renderer: GLMapViewRenderer, private val imageManager: ImageManager) : LocationListener {
    private var userMovementImage: GLMapDrawable? = null
    private var userLocationImage: GLMapDrawable? = null
    private var accuracyCircle: GLMapDrawable? = null
    var isFollowLocationEnabled = false
        set(value) {
            field = value
            if (field) {
                val lastLocation = lastLocation
                if (lastLocation != null)
                    onLocationChanged(lastLocation)
            }
        }
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null

    fun onDestroy() {
        val locationManager = locationManager
        if (locationManager != null) {
            this.locationManager = null
            locationManager.removeUpdates(this)
        }
    }

    fun initLocationManager(activity: Activity): Boolean {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        var locationManager = locationManager
        if (locationManager == null) {
            try {
                // Setup get location service
                locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                // Configure criteria
                val criteria = Criteria()
                criteria.accuracy = Criteria.ACCURACY_FINE
                criteria.isSpeedRequired = true
                criteria.isAltitudeRequired = true
                criteria.isBearingRequired = true
                criteria.bearingAccuracy = Criteria.ACCURACY_HIGH

                // Find the best location that currently have all location providers
                var bestAccuracy = Float.MAX_VALUE
                var bestTime = Long.MAX_VALUE
                lastLocation = null
                val matchingProviders = locationManager.allProviders
                for (provider in matchingProviders) {
                    var location: Location? = null
                    try {
                        location = locationManager.getLastKnownLocation(provider)
                    } catch (e: Exception) {
                        Log.e("CurLocationHelper", e.localizedMessage ?: "No message")
                    }
                    if (location != null) {
                        val accuracy = location.accuracy
                        val time = location.time
                        if (time >= bestTime && accuracy < bestAccuracy) {
                            lastLocation = location
                            bestAccuracy = accuracy
                            bestTime = time
                        } else if (time < bestTime && bestAccuracy == Float.MAX_VALUE) {
                            lastLocation = location
                            bestTime = time
                            bestAccuracy = accuracy
                        }
                    }
                }
                // Update location to current best
                val lastLocation = lastLocation
                if (lastLocation != null)
                    onLocationChanged(lastLocation)
                // Request location updates
                locationManager.requestLocationUpdates(
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES.toFloat(),
                    criteria,
                    this,
                    activity.mainLooper
                )
            } catch (e: Exception) {
                locationManager = null
            } finally {
                this.locationManager = locationManager
            }
        }
        return true
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        val position = MapPoint.CreateFromGeoCoordinates(location.latitude, location.longitude)
        if (isFollowLocationEnabled)
            renderer.animate { it.flyToPoint(position) }

        // Create drawables if not exist and set initial positions.
        var userLocationImage = userLocationImage
        if (userLocationImage == null) {
            val locationImage = imageManager.open("circle_new.svg", 1f, 0)!!
            userLocationImage = GLMapDrawable(locationImage, 100)
            this.userLocationImage = userLocationImage
            userLocationImage.isHidden = true
            userLocationImage.setOffset(locationImage.width / 2, locationImage.height / 2)
            userLocationImage.position = position
            renderer.add(userLocationImage)
            locationImage.recycle()
        }

        var userMovementImage = userMovementImage
        if (userMovementImage == null) {
            val movementImage = imageManager.open("arrow_new.svg", 1f, 0)!!
            userMovementImage = GLMapDrawable(movementImage, 100)
            this.userMovementImage = userMovementImage
            userMovementImage.isHidden = true
            userMovementImage.setOffset(movementImage.width / 2, movementImage.height / 2)
            userMovementImage.isRotatesWithMap = true
            userMovementImage.position = position
            if (location.hasBearing()) userLocationImage.angle = -location.bearing
            renderer.add(userMovementImage)
            movementImage.recycle()
        }

        // Select what image to display
        if (location.hasBearing()) {
            userMovementImage.isHidden = false
            userLocationImage.isHidden = true
        } else {
            userLocationImage.isHidden = false
            userMovementImage.isHidden = true
        }

        // Calculate radius of accuracy circle
        val r = renderer.convertMetersToInternal(location.accuracy.toDouble()).toFloat()
        // If accuracy circle drawable not exits - create it and set initial position
        var accuracyCircle = accuracyCircle
        if (accuracyCircle == null) {
            val pointCount = 100
            // Use MapPoint to avoid distortions of circle
            val points = arrayOfNulls<MapPoint>(pointCount)
            for (i in 0 until pointCount) {
                val f = 2 * Math.PI * i / pointCount
                // If radius of circle will be 1 only 2 points will be in final geometry (after
                // douglas-peucker)
                points[i] = MapPoint(sin(f) * 2048, cos(f) * 2048)
            }
            val circle = GLMapVectorObject.createPolygon(arrayOf(points), null)
            accuracyCircle = GLMapDrawable(99)
            this.accuracyCircle = accuracyCircle
            accuracyCircle.setTransformMode(GLMapDrawable.TransformMode.Custom)
            accuracyCircle.position = position
            accuracyCircle.scale = r / 2048.0f.toDouble()
            accuracyCircle.setVectorObject(
                circle,
                GLMapVectorCascadeStyle.createStyle("area{layer:100; width:1pt; fill-color:#3D99FA26; color:#3D99FA26;}")!!,
                null
            )
            renderer.add(accuracyCircle)
        }
        renderer.animate {
            it.setTransition(GLMapAnimation.Linear)
            it.setDuration(1.0)
            userMovementImage.position = position
            userLocationImage.position = position
            accuracyCircle.position = position
            accuracyCircle.scale = r / 2048.0f.toDouble()
            if (location.hasBearing()) userLocationImage.angle = -location.bearing
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    companion object {
        private const val MIN_TIME_BW_UPDATES: Long = 1000 // 1 second
        private const val MIN_DISTANCE_CHANGE_FOR_UPDATES: Long = 1 // 1 meter
    }
}
