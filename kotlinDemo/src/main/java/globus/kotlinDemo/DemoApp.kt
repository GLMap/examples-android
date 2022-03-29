package globus.kotlinDemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.multidex.MultiDexApplication
import globus.glmap.GLMapManager

/** Created by destman on 10/18/17.  */
class DemoApp : MultiDexApplication(), LocationListener {
    interface LocationCallback {
        fun onLocationChanged(location: Location)
    }

    private lateinit var locationManager: LocationManager
    var lastLocation: Location? = null
    var locationListeners = ArrayList<LocationCallback>()

    override fun onCreate() {
        super.onCreate()
        // Uncomment and insert your API key into api_key in res/values/strings.xml
        if (!GLMapManager.Initialize(this, this.getString(R.string.api_key), null)) {
            // Error caching resources. Check free space for world database (~25MB)
        }
    }

    fun initLocationManager(): Boolean {
        if (::locationManager.isInitialized)
            return true

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        )
            return false
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            // Setup get location service
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
            val matchingProviders = lm.allProviders
            for (provider in matchingProviders) {
                var location: Location? = null
                try {
                    location = lm.getLastKnownLocation(provider)
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
            lm.requestLocationUpdates(1000, 1.0f, criteria, this, mainLooper)
        } catch (e: Exception) {
        } finally {
            locationManager = lm
        }
        return true
    }

    override fun onLocationChanged(location: Location) {
        for (listener in locationListeners) {
            listener.onLocationChanged(location)
        }
    }
}
