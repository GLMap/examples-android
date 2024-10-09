package globus.kotlinDemo

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.multidex.MultiDexApplication
import com.google.android.gms.location.*
import globus.glmap.GLMapManager

class DemoApp :
    MultiDexApplication(),
    LocationListener {
    interface LocationCallback {
        fun onLocationChanged(location: Location)
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var lastLocation: Location? = null
    var locationListeners = ArrayList<LocationCallback>()

    override fun onCreate() {
        super.onCreate()
        // Uncomment and insert your API key into api_key in res/values/strings.xml
        if (!GLMapManager.Initialize(this, this.getString(R.string.api_key), null)) {
            // Error caching resources. Check free space for world database (~25MB)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    fun initLocationManager(): Boolean {
        if (::fusedLocationClient.isInitialized) {
            return true
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()

        try {
            // Get the last known location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        lastLocation = it
                        onLocationChanged(it)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CurLocationHelper", e.localizedMessage ?: "No message")
                }

            // Request location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, this, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("CurLocationHelper", e.localizedMessage ?: "No message")
        }
        return true
    }

    override fun onLocationChanged(location: Location) {
        for (listener in locationListeners) {
            listener.onLocationChanged(location)
        }
    }
}
