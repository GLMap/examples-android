package globus.demo;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Toast;

import globus.demo.utils.ActionItem;
import globus.demo.utils.QuickAction;
import globus.glmap.GLMapBBox;
import globus.glmap.GLMapError;
import globus.glmap.GLMapTrack;
import globus.glmap.GLMapTrackData;
import globus.glmap.GLMapView;
import globus.glmap.MapGeoPoint;
import globus.glmap.MapPoint;
import globus.glroute.GLRoute;
import globus.glroute.GLRoutePoint;
import globus.glroute.GLRouteRequest;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.annotation.ParametersAreNonnullByDefault;

//import globus.glroute.GLRoute;

@ParametersAreNonnullByDefault
public class RoutingActivity extends MapViewActivity {

    private enum NetworkMode {
        Online,
        Offline
    }

    private static final int ID_DEPARTURE = 0;
    private static final int ID_DESTINATION = 1;

    private QuickAction quickAction;
    private int routingMode = GLRoute.Mode.DRIVE;
    private NetworkMode networkMode = NetworkMode.Online;
    private TabLayout onlineOfflineSwitch, routeTypeSwitch;
    private MapGeoPoint departure, destination;
    private static String valhallaConfig;
    private GLMapTrack track;

    @Override
    protected int getLayoutID()
    {
        return R.layout.routing;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void runTest()
    {
        GestureDetector gestureDetector = new GestureDetector(
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

        departure = new MapGeoPoint(53.844720, 27.482352);
        destination = new MapGeoPoint(53.931935, 27.583995);
        mapView.doWhenSurfaceCreated(() -> {
            GLMapBBox bbox = new GLMapBBox();
            bbox.addPoint(new MapPoint(departure));
            bbox.addPoint(new MapPoint(destination));
            mapView.setMapCenter(bbox.center());
            mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()) - 1);
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
        GLRouteRequest request = new GLRouteRequest();
        request.addPoint(new GLRoutePoint(departure, Float.NaN, true, true));
        request.addPoint(new GLRoutePoint(destination, Float.NaN, true, true));
        request.locale = "en";
        request.unitSystem = GLMapView.GLUnitSystem.International;
        request.mode = routingMode;

        if(networkMode == NetworkMode.Offline)
            request.setOfflineWithConfig(getValhallaConfig(getResources()));

        request.start(new GLRouteRequest.ResultsCallback()
        {
            @Override
            public void onResult(GLRoute route)
            {
                GLMapTrackData trackData = route.getTrackData(Color.argb(255, 255, 0, 0));
                if (track != null) {
                    track.setData(trackData);
                } else {
                    track = new GLMapTrack(trackData, 5);
                    mapView.add(track);
                }
            }

            @Override
            public void onError(GLMapError error)
            {
                String message;
                if(error.message != null)
                    message = error.message;
                else
                    message = error.toString();
                Toast.makeText(RoutingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static String getValhallaConfig(Resources resources) {
        if (valhallaConfig == null) {
            byte[] raw = null;
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
}
