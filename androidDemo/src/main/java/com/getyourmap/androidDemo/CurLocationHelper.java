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
import com.glmapview.GLMapVectorCascadeStyle;
import com.glmapview.GLMapVectorObject;
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
    private GLMapVectorObject accuracyCircle;
    private boolean isFollowLocationEnabled = false;
    private LocationManager locationManager;
    private Location lastLocation;
    private GLMapView mapView;

    CurLocationHelper(GLMapView mapView)
    {
        this.mapView = mapView;
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
        final MapPoint position = new MapPoint(geoPoint);
        if(isFollowLocationEnabled)
            mapView.flyTo(geoPoint, mapView.getMapZoom(), 0, 0);

        if (userLocationImage == null) {
            Bitmap locationImage = mapView.imageManager.open("DefaultStyle.bundle/circle-new.svgpb", 1, 0);
            if (locationImage != null) {
                userLocationImage = mapView.displayImage(locationImage, 100);
                userLocationImage.setHidden(true);
                userLocationImage.setOffset(new MapPoint(locationImage.getWidth() / 2, locationImage.getHeight() / 2));
                userLocationImage.setPosition(position);
                locationImage.recycle();
            }
        }

        if (userMovementImage == null) {
            Bitmap movementImage = mapView.imageManager.open("DefaultStyle.bundle/arrow-new.svgpb", 1, 0);
            if (movementImage != null) {
                userMovementImage = mapView.displayImage(movementImage, 100);
                userMovementImage.setHidden(true);
                userMovementImage.setOffset(new MapPoint(movementImage.getWidth() / 2, movementImage.getHeight() / 2));
                userMovementImage.setRotatesWithMap(true);
                userMovementImage.setPosition(position);
                movementImage.recycle();
            }
        }

        if (location.hasBearing())
        {
            userMovementImage.setHidden(false);
            userMovementImage.setAngle(-location.getBearing());
            userLocationImage.setHidden(false);
        } else
        {
            userLocationImage.setHidden(false);
            userMovementImage.setHidden(true);
        }

        mapView.startImagePositionAnimation(userMovementImage, null, position, 1, GLMapView.Animation.Linear);
        mapView.startImagePositionAnimation(userLocationImage, null, position, 1, GLMapView.Animation.Linear);

        if(accuracyCircle == null)
        {
            final int pointCount = 100;
            //Use MapPoint to avoid distortions of circle
            MapPoint[] points = new MapPoint[pointCount];
            for (int i = 0; i < pointCount; i++)
            {
                double f = 2 * Math.PI * i / pointCount;
                //If radius of circle will be 1 only 2 points will be in final geometry (after douglas-peucker)
                points[i] = new MapPoint(Math.sin(f) * 2048, Math.cos(f) * 2048);
            }
            accuracyCircle = GLMapVectorObject.createPolygon(new MapPoint[][]{points}, null);
            accuracyCircle.useTransform();

            //Set layer to 100 so circle will draw above map objects
            mapView.addVectorObjectWithStyle(accuracyCircle, GLMapVectorCascadeStyle.createStyle("area{layer:100; width:1pt; fill-color:#3D99FA26; color:#3D99FA26;}"), null);

            // setTransform could be used only when mapView surface is created
            mapView.doWhenSurfaceCreated(new Runnable() {
                @Override
                public void run() {
                    accuracyCircle.setTransform(mapView, position, 0.1f);
                }
            });
        }
        float r = (float)mapView.convertMetersToInternal(location.getAccuracy());
        r = (float)mapView.convertMetersToInternal(30);
        mapView.startVectorObjectAnimation(accuracyCircle, null, position, Float.NaN, r/2048.0f, 1, GLMapView.Animation.Linear);
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
