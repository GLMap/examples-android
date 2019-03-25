package com.getyourmap.androidDemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Toast;

import com.getyourmap.androidDemo.utils.ActionItem;
import com.getyourmap.androidDemo.utils.QuickAction;
import com.getyourmap.glmap.GLMapBBox;
import com.getyourmap.glmap.GLMapDownloadTask;
import com.getyourmap.glmap.GLMapError;
import com.getyourmap.glmap.GLMapInfo;
import com.getyourmap.glmap.GLMapLocaleSettings;
import com.getyourmap.glmap.GLMapManager;
import com.getyourmap.glmap.GLMapTrack;
import com.getyourmap.glmap.GLMapTrackData;
import com.getyourmap.glmap.GLMapView;
import com.getyourmap.glmap.MapGeoPoint;
import com.getyourmap.glmap.MapPoint;
import com.getyourmap.glroute.GLRoute;
import com.getyourmap.glroute.GLRoutePoint;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.Nullable;

//import com.getyourmap.glroute.GLRoute;

@ParametersAreNonnullByDefault
public class RoutingActivity extends Activity implements GLMapManager.StateListener {

    private enum NetworkMode {
        Online,
        Offline
    }

    private static final int ID_DEPARTURE = 0;
    private static final int ID_DESTINATION = 1;

    private GestureDetector gestureDetector;
    private QuickAction quickAction;
    private GLMapView mapView;
    private int routingMode = GLRoute.Mode.DRIVE;
    private NetworkMode networkMode = NetworkMode.Online;
    private TabLayout onlineOfflineSwitch, routeTypeSwitch;
    private MapGeoPoint departure, destination;
    private static String valhallaConfig;
    private Button btnDownloadMap;
    private GLMapInfo mapToDownload;
    private GLMapLocaleSettings localeSettings;
    private GLMapTrack track;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.routing);
        setMapView();
        setMapDownloadButton();
        departure = new MapGeoPoint(53.844720, 27.482352);
        destination = new MapGeoPoint(53.931935, 27.583995);
        mapView.doWhenSurfaceCreated(
                new Runnable() {
                    @Override
                    public void run() {
                        GLMapBBox bbox = new GLMapBBox();
                        bbox.addPoint(new MapPoint(departure));
                        bbox.addPoint(new MapPoint(destination));
                        mapView.setMapCenter(bbox.center());
                        mapView.setMapZoom(
                                mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()) - 1);
                    }
                });
        updateRoute();
        setTabSwitches();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setSwitchesValues();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture)
    {

    }

    private void updateRoute() {
        GLRoutePoint pts[] = {
                new GLRoutePoint(departure, Float.NaN, true), new GLRoutePoint(destination, Float.NaN, true)
        };

        GLRoute.ResultsCallback callback =
                new GLRoute.ResultsCallback() {
                    @Override
                    public void onResult(GLRoute route) {
                        GLMapTrackData trackData = route.getTrackData(Color.argb(255, 255, 0, 0));
                        if (track != null) {
                            track.setData(trackData);
                        } else {
                            track = new GLMapTrack(trackData, 5);
                            mapView.add(track);
                        }
                    }

                    @Override
                    public void onError(GLMapError glMapError) {
                        Toast.makeText(RoutingActivity.this, glMapError.message, Toast.LENGTH_LONG).show();
                    }
                };

        switch (networkMode) {
            case Online:
                GLRoute.requestOnlineRouteData(pts, routingMode, "en", GLMapView.GLUnitSystem.International, callback);
                break;
            case Offline:
                GLRoute.requestOfflineRouteData(
                        getValhallaConfig(getResources()),
                        pts,
                        routingMode,
                        "en",
                        GLMapView.GLUnitSystem.International,
                        callback);
                break;
        }
    }

    private void setMapDownloadButton() {
        // Map list is updated, because download button depends on available map list and during first
        // launch this list is empty
        GLMapManager.UpdateMapList(null);

        btnDownloadMap = this.findViewById(R.id.button_dl_map);
        btnDownloadMap.setOnClickListener(v -> {
            if (mapToDownload != null) {
                GLMapDownloadTask task = GLMapManager.getDownloadTask(mapToDownload);
                if (task != null) {
                    task.cancel();
                } else {
                    GLMapManager.DownloadDataSets(
                            mapToDownload, GLMapInfo.DataSetMask.ALL, RoutingActivity.this);
                }
                MapViewActivity.updateMapDownloadButtonText(
                        mapView, btnDownloadMap, mapToDownload, localeSettings);
            } else {
                Intent i = new Intent(v.getContext(), DownloadActivity.class);

                MapPoint pt = mapView.getMapCenter();
                i.putExtra("cx", pt.x);
                i.putExtra("cy", pt.y);
                v.getContext().startActivity(i);
            }
        });

        GLMapManager.addStateListener(this);
    }

    public static String getValhallaConfig(Resources resources) {
        if (valhallaConfig == null) {
            byte raw[] = null;
            try {
                // Read prepared categories
                InputStream stream = resources.openRawResource(R.raw.valhalla);
                raw = new byte[stream.available()];
                stream.read(raw);
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Construct categories
            valhallaConfig = new String(raw, Charset.defaultCharset());
        }
        return valhallaConfig;
    }

    private void setSwitchesValues() {
        if (onlineOfflineSwitch == null || routeTypeSwitch == null) return;
        onlineOfflineSwitch.getTabAt(networkMode.ordinal()).select();
        routeTypeSwitch.getTabAt(routingMode).select();
    }

    private void showDefaultPopupMenu(final float x, final float y) {
        if (quickAction != null) {
            quickAction.dismiss();
        }

        quickAction = new QuickAction(this);
        quickAction.addActionItem(new ActionItem(ID_DEPARTURE, "Departure"));
        quickAction.addActionItem(new ActionItem(ID_DESTINATION, "Destination"));

        quickAction.setonActionItemClickListener((source, actionId) -> {
            final MapPoint mapPoint = new MapPoint(x, y);
            switch (actionId) {
                case ID_DEPARTURE:
                    departure = new MapGeoPoint(mapView.convertDisplayToInternal(mapPoint));
                    break;
                case ID_DESTINATION:
                    destination = new MapGeoPoint(mapView.convertDisplayToInternal(mapPoint));
                    break;
            }
            if (departure != null && destination != null) updateRoute();
        });
        quickAction.show(mapView, x, y);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setMapView() {
        mapView = this.findViewById(R.id.map_view);
        localeSettings = new GLMapLocaleSettings();
        mapView.setLocaleSettings(localeSettings);
        mapView.loadStyle(getAssets(), "DefaultStyle.bundle");
        mapView.setScaleRulerStyle(
                GLMapView.GLUnitSystem.International,
                GLMapView.GLMapPlacement.BottomCenter,
                new MapPoint(10, 10),
                200);
        mapView.setAttributionPosition(GLMapView.GLMapPlacement.TopCenter);
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent e) {
                                showDefaultPopupMenu(e.getX(), e.getY());
                                return true;
                            }

                            @Override
                            public void onLongPress(MotionEvent e) {}
                        });

        mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));

        mapView.setCenterTileStateChangedCallback(() -> MapViewActivity.updateMapDownloadButton(
                mapView, btnDownloadMap, mapToDownload, localeSettings));

        mapView.setMapDidMoveCallback(() -> MapViewActivity.updateMapDownloadButtonText(
                mapView, btnDownloadMap, mapToDownload, localeSettings));
    }

    private void setTabSwitches() {
        onlineOfflineSwitch = findViewById(R.id.tab_layout_left);
        routeTypeSwitch = findViewById(R.id.tab_layout_right);
        onlineOfflineSwitch.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {

                        switch (tab.getPosition()) {
                            case 0:
                                networkMode = NetworkMode.Online;
                                break;
                            case 1:
                                networkMode = NetworkMode.Offline;
                                break;
                        }
                        updateRoute();
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });
        routeTypeSwitch.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {

                        switch (tab.getPosition()) {
                            case 0:
                                routingMode = GLRoute.Mode.DRIVE;
                                break;
                            case 1:
                                routingMode = GLRoute.Mode.CYCLE;
                                break;
                            case 2:
                                routingMode = GLRoute.Mode.WALK;
                                break;
                        }
                        updateRoute();
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });
    }

    @Override
    public void onStartDownloading(GLMapDownloadTask glMapDownloadTask) {}

    @Override
    public void onDownloadProgress(GLMapDownloadTask glMapDownloadTask) {
        MapViewActivity.updateMapDownloadButtonText(mapView, btnDownloadMap, mapToDownload, localeSettings);
    }

    @Override
    public void onFinishDownloading(GLMapDownloadTask glMapDownloadTask) {}

    @Override
    public void onStateChanged(GLMapInfo glMapInfo) {
        MapViewActivity.updateMapDownloadButtonText(mapView, btnDownloadMap, mapToDownload, localeSettings);
    }
}
