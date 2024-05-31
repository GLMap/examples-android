package globus.javaDemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.multidex.MultiDexApplication;
import com.google.android.gms.location.*;
import globus.glmap.GLMapManager;
import java.util.ArrayList;
import java.util.List;

public class DemoApp extends MultiDexApplication implements LocationListener {
    public interface LocationCallback {
        void onLocationChanged(@NonNull Location location);
    }

    private FusedLocationProviderClient fusedLocationClient;
    public List<LocationCallback> locationListeners = new ArrayList<>();
    public Location lastLocation;

    @Override
    public void onCreate() {
        super.onCreate();
        // Uncomment and insert your API key into api_key in res/values/strings.xml
        String apiKey = this.getString(R.string.api_key);
        if (!GLMapManager.Initialize(this, apiKey, null)) {
            Log.e("DemoApp", "Error caching resources. Check free space for world database (~25MB)");
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    public boolean initLocationManager() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build();

        try {
            // Get the last known location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            lastLocation = location;
                            onLocationChanged(location);
                        }
                    })
                    .addOnFailureListener(e -> Log.e("CurLocationHelper", getErrorMessage(e)));

            // Request location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, this, Looper.getMainLooper());
        } catch (Exception e) {
            Log.e("CurLocationHelper", getErrorMessage(e));
        }
        return true;
    }

    private String getErrorMessage(Exception e) {
        return e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "";
    }

    public void onLocationChanged(@NonNull Location location) {
        for (LocationCallback listener : locationListeners) {
            listener.onLocationChanged(location);
        }
    }
}
