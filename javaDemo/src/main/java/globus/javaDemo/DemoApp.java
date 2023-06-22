package globus.javaDemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.multidex.MultiDexApplication;
import globus.glmap.GLMapManager;
import java.util.ArrayList;
import java.util.List;

/** Created by destman on 10/18/17. */
public class DemoApp extends MultiDexApplication implements LocationListener {
    private static final long MIN_TIME_BW_UPDATES = 1000;          // 1 second
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meter

    public interface LocationCallback {
        void onLocationChanged(@NonNull Location location);
    }

    LocationManager locationManager;
    Location lastLocation;
    List<LocationCallback> locationListeners = new ArrayList<>();

    @Override
    public void onCreate()
    {
        super.onCreate();
        // Uncomment and insert your API key into api_key in res/values/strings.xml
        String apiKey = this.getString(R.string.api_key);
        if (!GLMapManager.Initialize(this, apiKey, null)) {
            // Error caching resources. Check free space for world database (~25MB)
        }
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
    }

    boolean initLocationManager()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return false;

        try {
            // Configure criteria
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setSpeedRequired(true);
            criteria.setAltitudeRequired(true);
            criteria.setBearingRequired(true);
            criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);

            // Find the best location that currently have all location providers
            float bestAccuracy = Float.MAX_VALUE;
            long bestTime = Long.MAX_VALUE;
            lastLocation = null;
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider : matchingProviders) {
                Location location = null;
                try {
                    location = locationManager.getLastKnownLocation(provider);
                } catch (Exception e) {
                    Log.e("CurLocationHelper", e.getLocalizedMessage());
                }
                if (location != null) {
                    float accuracy = location.getAccuracy();
                    long time = location.getTime();
                    if ((time >= bestTime && accuracy < bestAccuracy)) {
                        lastLocation = location;
                        bestAccuracy = accuracy;
                        bestTime = time;
                    } else if (time < bestTime && bestAccuracy == Float.MAX_VALUE) {
                        lastLocation = location;
                        bestTime = time;
                        bestAccuracy = accuracy;
                    }
                }
            }
            // Update location to current best
            onLocationChanged(lastLocation);
            // Request location updates
            locationManager.requestLocationUpdates(MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, criteria, this, getMainLooper());
        } catch (Exception ignore) {
        }
        return true;
    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        for (LocationCallback listener : locationListeners) {
            listener.onLocationChanged(location);
        }
    }
}
