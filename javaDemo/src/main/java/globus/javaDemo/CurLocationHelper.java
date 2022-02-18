package globus.javaDemo;

import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import globus.glmap.GLMapAnimation;
import globus.glmap.GLMapDrawable;
import globus.glmap.GLMapVectorCascadeStyle;
import globus.glmap.GLMapVectorObject;
import globus.glmap.GLMapViewRenderer;
import globus.glmap.ImageManager;
import globus.glmap.MapPoint;

import java.util.List;
import java.util.Objects;

/** Created by destman on 6/1/17. */
class CurLocationHelper implements LocationListener {
    private static final long MIN_TIME_BW_UPDATES = 1000; // 1 second
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meter

    private GLMapDrawable userMovementImage, userLocationImage, accuracyCircle;
    private boolean isFollowLocationEnabled = false;
    private LocationManager locationManager;
    private Location lastLocation;
    private final GLMapViewRenderer renderer;
    private final ImageManager imageManager;

    CurLocationHelper(GLMapViewRenderer renderer, ImageManager imageManager) {
        this.renderer = renderer;
        this.imageManager = imageManager;
    }

    void onDestroy() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            locationManager = null;
        }
    }

    boolean initLocationManager(Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                                activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) return false;

        if (locationManager == null) {
            try {
                // Setup get location service
                locationManager = (LocationManager) activity.getSystemService(LOCATION_SERVICE);

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
                locationManager.requestLocationUpdates(
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES,
                        criteria,
                        this,
                        activity.getMainLooper());
            } catch (Exception e) {
                locationManager = null;
            }
        }
        return true;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setEnableFollowLocation(boolean enable) {
        this.isFollowLocationEnabled = enable;
        if (isFollowLocationEnabled) {
            onLocationChanged(lastLocation);
        }
    }

    @Override
    public void onLocationChanged(final Location location) {
        if (location == null) return;

        lastLocation = location;
        final MapPoint position =
                MapPoint.CreateFromGeoCoordinates(location.getLatitude(), location.getLongitude());
        if (isFollowLocationEnabled) {
            renderer.animate(animation -> animation.flyToPoint(position));
        }

        // Create drawables if not exist and set initial positions.
        if (userLocationImage == null) {
            Bitmap locationImage =
                    Objects.requireNonNull(imageManager.open("circle_new.svg", 1, 0));
            userLocationImage = new GLMapDrawable(locationImage, 100);
            userLocationImage.setHidden(true);
            userLocationImage.setOffset(
                    locationImage.getWidth() / 2, locationImage.getHeight() / 2);
            userLocationImage.setPosition(position);
            renderer.add(userLocationImage);
            locationImage.recycle();
        }

        if (userMovementImage == null) {
            Bitmap movementImage = Objects.requireNonNull(imageManager.open("arrow_new.svg", 1, 0));
            userMovementImage = new GLMapDrawable(movementImage, 100);
            userMovementImage.setHidden(true);
            userMovementImage.setOffset(
                    movementImage.getWidth() / 2, movementImage.getHeight() / 2);
            userMovementImage.setRotatesWithMap(true);
            userMovementImage.setPosition(position);
            if (location.hasBearing()) userLocationImage.setAngle(-location.getBearing());
            renderer.add(userMovementImage);
            movementImage.recycle();
        }

        // Select what image to display
        if (location.hasBearing()) {
            userMovementImage.setHidden(false);
            userLocationImage.setHidden(true);
        } else {
            userLocationImage.setHidden(false);
            userMovementImage.setHidden(true);
        }

        // Calculate radius of accuracy circle
        final float r = (float) renderer.convertMetersToInternal(location.getAccuracy());
        // If accuracy circle drawable not exits - create it and set initial position
        if (accuracyCircle == null) {
            final int pointCount = 100;
            // Use MapPoint to avoid distortions of circle
            MapPoint[] points = new MapPoint[pointCount];
            for (int i = 0; i < pointCount; i++) {
                double f = 2 * Math.PI * i / pointCount;
                // If radius of circle will be 1 only 2 points will be in final geometry (after
                // douglas-peucker)
                points[i] = new MapPoint(Math.sin(f) * 2048, Math.cos(f) * 2048);
            }
            GLMapVectorObject circle =
                    GLMapVectorObject.createPolygon(new MapPoint[][] {points}, null);

            accuracyCircle = new GLMapDrawable(99);
            accuracyCircle.setTransformMode(GLMapDrawable.TransformMode.Custom);
            accuracyCircle.setPosition(position);
            accuracyCircle.setScale(r / 2048.0f);
            accuracyCircle.setVectorObject(
                    circle,
                    Objects.requireNonNull(
                            GLMapVectorCascadeStyle.createStyle(
                                    "area{layer:100; width:1pt; fill-color:#3D99FA26;"
                                            + " color:#3D99FA26;}")),
                    null);
            renderer.add(accuracyCircle);
        }

        renderer.animate(
                animation -> {
                    animation.setTransition(GLMapAnimation.Linear);
                    animation.setDuration(1);
                    userMovementImage.setPosition(position);
                    userLocationImage.setPosition(position);
                    accuracyCircle.setPosition(position);
                    accuracyCircle.setScale(r / 2048.0f);
                    if (location.hasBearing()) userLocationImage.setAngle(-location.getBearing());
                });
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
}