package com.getyourmap.androidDemo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.glmapview.GLMapImage;
import com.glmapview.GLMapView;
import com.glmapview.MapGeoPoint;
import com.glmapview.MapPoint;

import java.util.List;

import static android.content.Context.LOCATION_SERVICE;

/**
 * Created by destman on 6/1/17.
 */

class CurLocationHelper implements LocationListener
{
    private static final long MIN_TIME_BW_UPDATES = 1000; // 1 second
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meter

    private GLMapImage userMovementImage, userLocationImage;
    private boolean isFollowLocationEnabled = false;
    private LocationManager locationManager;
    private Location lastLocation;
    private GLMapView mapView;

    CurLocationHelper(GLMapView mapView)
    {
        this.mapView = mapView;
        Bitmap locationImage = mapView.imageManager.open("DefaultStyle.bundle/circle-new.svgpb", 1, 0);
        if (locationImage != null)
        {
            userLocationImage = mapView.displayImage(locationImage);
            userLocationImage.setDrawOrder(100);
            userLocationImage.setHidden(true);
            userLocationImage.setOffset(new MapPoint(locationImage.getWidth() / 2, locationImage.getHeight() / 2));
            locationImage.recycle();
        }

        Bitmap movementImage = mapView.imageManager.open("DefaultStyle.bundle/arrow-new.svgpb", 1, 0);
        if (movementImage != null)
        {
            userMovementImage = mapView.displayImage(movementImage);
            userMovementImage.setDrawOrder(100);
            userMovementImage.setHidden(true);
            userMovementImage.setOffset(new MapPoint(movementImage.getWidth() / 2, movementImage.getHeight() / 2));
            userMovementImage.setRotatesWithMap(true);
            movementImage.recycle();
        }
    }

    void onDestroy()
    {
        if(locationManager!=null)
        {
            locationManager.removeUpdates(this);
        }
    }

    boolean initLocationManager(Activity activity)
    {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return false;

        if(locationManager == null)
        {
            try
            {
                //Setup get location service
                locationManager = (LocationManager) activity.getSystemService(LOCATION_SERVICE);

                //Configure criteria
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setSpeedRequired(true);
                criteria.setAltitudeRequired(true);
                criteria.setBearingRequired(true);
                criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);

                //Find the best location that currently have all location providers
                float bestAccuracy = Float.MAX_VALUE;
                long bestTime = Long.MAX_VALUE;
                lastLocation = null;
                List<String> matchingProviders = locationManager.getAllProviders();
                for (String provider : matchingProviders) {
                    Location location = null;
                    try {
                        location = locationManager.getLastKnownLocation(provider);
                    } catch (Exception e)
                    {
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
                //Update location to current best
                onLocationChanged(lastLocation);
                //Request location updates
                locationManager.requestLocationUpdates(MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, criteria, this, activity.getMainLooper());
            } catch (Exception e)
            {
                locationManager = null;
            }
        }
        return true;
    }

    public Location getLastLocation()
    {
        return lastLocation;
    }

    public void setEnableFollowLocation(boolean enable)
    {
        this.isFollowLocationEnabled = enable;
        if(isFollowLocationEnabled)
        {
            onLocationChanged(lastLocation);
        }
    }

    @Override
    public void onLocationChanged(Location location)
    {
        if(location == null)
            return;

        lastLocation = location;

        MapGeoPoint geoPoint = new MapGeoPoint(location.getLatitude(), location.getLongitude());
        MapPoint point = new MapPoint(geoPoint);
        if(isFollowLocationEnabled)
            mapView.flyTo(geoPoint, mapView.getMapZoom(), 0, 0);

        if (location.hasBearing() && userMovementImage!=null)
        {
            userMovementImage.setHidden(false);
            userMovementImage.setPosition(point);
            userMovementImage.setAngle(-location.getBearing());
            if (userLocationImage != null)
                userLocationImage.setHidden(false);
        } else if (userLocationImage != null)
        {
            userLocationImage.setHidden(false);
            userLocationImage.setPosition(point);
            if (userMovementImage != null)
                userMovementImage.setHidden(true);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }

    @Override
    public void onProviderEnabled(String provider)
    {
    }

    @Override
    public void onProviderDisabled(String provider)
    {
    }
}
