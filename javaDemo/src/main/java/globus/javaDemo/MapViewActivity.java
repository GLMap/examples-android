package globus.javaDemo;

import static globus.javaDemo.RoutingActivity.getValhallaConfig;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import globus.glmap.GLMapBBox;
import globus.glmap.GLMapDownloadTask;
import globus.glmap.GLMapError;
import globus.glmap.GLMapImage;
import globus.glmap.GLMapImageGroup;
import globus.glmap.GLMapImageGroupCallback;
import globus.glmap.GLMapInfo;
import globus.glmap.GLMapLocaleSettings;
import globus.glmap.GLMapManager;
import globus.glmap.GLMapMarkerImage;
import globus.glmap.GLMapMarkerLayer;
import globus.glmap.GLMapMarkerStyleCollection;
import globus.glmap.GLMapMarkerStyleCollectionDataCallback;
import globus.glmap.GLMapScaleRuler;
import globus.glmap.GLMapStyleParser;
import globus.glmap.GLMapTextAlignment;
import globus.glmap.GLMapTrack;
import globus.glmap.GLMapTrackData;
import globus.glmap.GLMapValue;
import globus.glmap.GLMapVectorCascadeStyle;
import globus.glmap.GLMapVectorLayer;
import globus.glmap.GLMapVectorObject;
import globus.glmap.GLMapVectorObjectList;
import globus.glmap.GLMapVectorStyle;
import globus.glmap.GLMapView;
import globus.glmap.GLMapViewRenderer;
import globus.glmap.MapGeoPoint;
import globus.glmap.MapPoint;
import globus.glmap.SVGRender;
import globus.glroute.CostingOptions;
import globus.glroute.GLRoute;
import globus.glroute.GLRoutePoint;
import globus.glroute.GLRouteRequest;
import globus.glsearch.GLSearch;
import globus.glsearch.GLSearchCategories;
import globus.glsearch.GLSearchCategory;
import globus.glsearch.GLSearchFilter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@SuppressLint({"ClickableViewAccessibility", "StaticFieldLeak", "SetTextI18n"})
public class MapViewActivity extends Activity implements GLMapViewRenderer.ScreenCaptureCallback, GLMapManager.StateListener {

    private static class Pin {
        MapPoint pos;
        int imageVariant;
    }

    private static class Pins implements GLMapImageGroupCallback {
        private final ReentrantLock lock;
        private final Bitmap[] images;
        private final List<Pin> pins;

        Pins(MapViewActivity activity)
        {
            lock = new ReentrantLock();
            images = new Bitmap[3];
            pins = new ArrayList<>();
            float screenScale = activity.screenScale;
            images[0] = SVGRender.render(activity.getAssets(), "1.svg", SVGRender.transform(screenScale, 0xFFFF0000));
            images[1] = SVGRender.render(activity.getAssets(), "2.svg", SVGRender.transform(screenScale, 0xFF00FF00));
            images[2] = SVGRender.render(activity.getAssets(), "3.svg", SVGRender.transform(screenScale, 0xFF0000FF));
        }

        @Override
        public int getImagesCount()
        {
            return pins.size();
        }

        @Override
        public int getImageIndex(int i)
        {
            return pins.get(i).imageVariant;
        }

        @Override
        public MapPoint getImagePos(int i)
        {
            return pins.get(i).pos;
        }

        @Override
        public void updateStarted()
        {
            Log.i("GLMapImageGroupCallback", "Update started");
            lock.lock();
        }

        @Override
        public void updateFinished()
        {
            Log.i("GLMapImageGroupCallback", "Update finished");
            lock.unlock();
        }

        @Override
        public int getImageVariantsCount()
        {
            return images.length;
        }

        @Override
        public Bitmap getImageVariantBitmap(int i)
        {
            return images[i];
        }

        @Override
        public MapPoint getImageVariantOffset(int i)
        {
            return new MapPoint(images[i].getWidth() / 2.0, 0);
        }

        int size()
        {
            int rv;
            lock.lock();
            rv = pins.size();
            lock.unlock();
            return rv;
        }

        void add(Pin pin)
        {
            lock.lock();
            pins.add(pin);
            lock.unlock();
        }

        void remove(Pin pin)
        {
            lock.lock();
            pins.remove(pin);
            lock.unlock();
        }

        Pin findPin(GLMapView mapView, float touchX, float touchY)
        {
            Pin rv = null;
            lock.lock();
            for (int i = 0; i < pins.size(); ++i) {
                Pin pin = pins.get(i);
                MapPoint screenPos = mapView.renderer.convertInternalToDisplay(new MapPoint(pin.pos));
                Rect rt = new Rect(-40, -40, 40, 40);
                rt.offset((int)screenPos.x, (int)screenPos.y);
                if (rt.contains((int)touchX, (int)touchY)) {
                    rv = pin;
                    break;
                }
            }
            lock.unlock();
            return rv;
        }
    }
    private GLMapImage image;
    private GLMapImageGroup imageGroup;
    private Pins pins;
    private GestureDetector gestureDetector;
    protected GLMapView mapView;
    private GLMapInfo mapToDownload;
    private Button btnDownloadMap;
    private GLMapMarkerLayer markerLayer;
    private CurLocationHelper curLocationHelper;
    private int trackPointIndex;
    private GLMapTrack track;
    private final GLMapVectorStyle trackStyle = GLMapVectorStyle.createStyle("{width: 7pt; fill-image:\"track-arrow.svgpb\";}");
    private GLMapTrackData trackData;
    private Runnable trackRecordRunnable;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    protected float screenScale;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutID());

        mapView = this.findViewById(R.id.map_view);
        screenScale = mapView.renderer.screenScale;

        // Map list is updated, because download button depends on available map list and during
        // first
        // launch this list is empty
        GLMapManager.UpdateMapList(null);

        btnDownloadMap = this.findViewById(R.id.button_dl_map);
        btnDownloadMap.setOnClickListener(v -> {
            if (mapToDownload != null) {
                List<GLMapDownloadTask> tasks = GLMapManager.getDownloadTasks(mapToDownload.getMapID(), GLMapInfo.DataSetMask.ALL);
                if (tasks != null && !tasks.isEmpty()) {
                    for (GLMapDownloadTask task : tasks)
                        task.cancel();
                } else {
                    GLMapManager.DownloadDataSets(mapToDownload, GLMapInfo.DataSetMask.ALL);
                }
                updateMapDownloadButtonText();
            } else {
                Intent i = new Intent(v.getContext(), DownloadActivity.class);

                MapPoint pt = mapView.renderer.getMapCenter();
                i.putExtra("cx", pt.x);
                i.putExtra("cy", pt.y);
                v.getContext().startActivity(i);
            }
        });

        GLMapManager.addStateListener(this);

        GLMapStyleParser parser = new GLMapStyleParser(getAssets(), "DefaultStyle.bundle");
        mapView.renderer.setStyle(Objects.requireNonNull(parser.parseFromResources()));
        checkAndRequestLocationPermission();

        GLMapScaleRuler ruler = new GLMapScaleRuler(Integer.MAX_VALUE);
        ruler.setPlacement(GLMapViewRenderer.GLMapPlacement.BottomCenter, 10, 10, 200);
        mapView.renderer.add(ruler);
        mapView.renderer.setAttributionPosition(GLMapViewRenderer.GLMapPlacement.TopCenter);

        mapView.renderer.setCenterTileStateChangedCallback(this::updateMapDownloadButton);
        mapView.renderer.setMapDidMoveCallback(this::updateMapDownloadButtonText);

        runTest();
    }

    protected int getLayoutID() { return R.layout.map; }

    protected void runTest()
    {
        Bundle b = getIntent().getExtras();
        SampleSelectActivity.Samples example;
        if (b != null)
            example = SampleSelectActivity.Samples.values()[b.getInt("example")];
        else
            example = SampleSelectActivity.Samples.MAP;
        switch (example) {
        case DARK_THEME:
            loadDarkTheme();
            break;
        case MAP_EMBEDDED:
            showEmbedded();
            break;
        case MAP_ONLINE:
            GLMapManager.SetTileDownloadingAllowed(true);
            break;
        case MAP_ONLINE_RASTER:
            mapView.renderer.setBase(new OSMTileSource(this));
            break;
        case ZOOM_BBOX:
            zoomToBBox();
            break;
        case FLY_TO:
            mapView.renderer.setMapCenter(MapPoint.CreateFromGeoCoordinates(37.3257, -122.0353));
            mapView.renderer.setMapZoom(14);

            Button btn = this.findViewById(R.id.button_action);
            btn.setVisibility(View.VISIBLE);
            btn.setText("Fly");
            btn.setOnClickListener(v -> {
                double min_lat = 33;
                double max_lat = 48;
                double min_lon = -118;
                double max_lon = -85;

                double lat = min_lat + (max_lat - min_lat) * Math.random();
                double lon = min_lon + (max_lon - min_lon) * Math.random();

                final MapPoint point = MapPoint.CreateFromGeoCoordinates(lat, lon);
                mapView.renderer.animate(animation -> {
                    mapView.renderer.setMapZoom(15);
                    animation.flyToPoint(point);
                });
            });
            GLMapManager.SetTileDownloadingAllowed(true);
            break;
        case OFFLINE_SEARCH:
            GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, null, "Montenegro.vm", getAssets(), null);
            zoomToPoint();
            offlineSearch();
            break;
        case MARKERS:
            mapView.setLongClickable(true);

            gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e)
                {
                    deleteMarker(e.getX(), e.getY());
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e)
                {
                    addMarker(e.getX(), e.getY());
                }
            });

            mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));

            addMarkers();
            GLMapManager.SetTileDownloadingAllowed(true);
            break;
        case MARKERS_MAPCSS:
            addMarkersWithMapcss();

            gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e)
                {
                    deleteMarker(e.getX(), e.getY());
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e)
                {
                    addMarkerAsVectorObject(e.getX(), e.getY());
                }
            });

            mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));

            GLMapManager.SetTileDownloadingAllowed(true);
            break;
        case MULTILINE:
            addMultiline();
            break;
        case POLYGON:
            addPolygon();
            break;
        case CAPTURE_SCREEN:
            zoomToPoint();
            captureScreen();
            break;
        case IMAGE_SINGLE:
            btn = this.findViewById(R.id.button_action);
            btn.setVisibility(View.VISIBLE);
            delImage(btn);
            break;
        case IMAGE_MULTI:
            mapView.setLongClickable(true);

            gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e)
                {
                    deletePin(e.getX(), e.getY());
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e)
                {
                    addPin(e.getX(), e.getY());
                }
            });

            mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));
            break;
        case GEO_JSON:
            loadGeoJSON();
            break;
        case DOWNLOAD_IN_BBOX:
            downloadInBBox();
            break;
        case STYLE_LIVE_RELOAD:
            styleLiveReload();
            break;
        case RECORD_TRACK:
            recordTrack();
            break;
        }

        mapView.renderer.setMapDidMoveCallback(() -> {
            if (example == SampleSelectActivity.Samples.CALLBACK_TEST) {
                Log.w("GLMapView", "Did move");
            }
            updateMapDownloadButtonText();
        });
    }

    public void checkAndRequestLocationPermission()
    {
        DemoApp app = (DemoApp)getApplication();
        // Create helper if not exist
        if (curLocationHelper == null) {
            curLocationHelper = new CurLocationHelper(mapView.renderer);
            app.locationListeners.add(curLocationHelper);
        }

        // Try to start location updates. If we need permissions - ask for them
        // Setup get location service
        if (!app.initLocationManager())
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ((DemoApp)getApplication()).initLocationManager();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy()
    {
        GLMapManager.removeStateListener(this);

        if (markerLayer != null) {
            markerLayer.dispose();
            markerLayer = null;
        }

        if (imageGroup != null) {
            imageGroup.dispose();
            imageGroup = null;
        }

        if (curLocationHelper != null) {
            ((DemoApp)getApplication()).locationListeners.remove(curLocationHelper);
            curLocationHelper = null;
        }

        if (mapView != null) {
            mapView.dispose();
        }

        handler.removeCallbacks(trackRecordRunnable);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        mapView.renderer.animate(animation -> mapView.renderer.setMapZoom(mapView.renderer.getMapZoom() - 1));
        return false;
    }

    @Override
    public void onStartDownloading(@NonNull GLMapDownloadTask task)
    {}

    @Override
    public void onDownloadProgress(@NonNull GLMapDownloadTask task)
    {
        updateMapDownloadButtonText();
    }

    @Override
    public void onFinishDownloading(@NonNull GLMapDownloadTask task)
    {
        mapView.renderer.reloadTiles();
    }

    @Override
    public void onStateChanged(GLMapInfo map, int dataSet)
    {
        updateMapDownloadButtonText();
    }

    private void updateMapDownloadButtonText()
    {
        if (btnDownloadMap.getVisibility() == View.VISIBLE) {
            MapPoint center = mapView.renderer.getMapCenter();

            GLMapInfo[] maps = GLMapManager.MapsAtPoint(center);
            if (maps == null || maps.length == 0)
                mapToDownload = null;
            else
                mapToDownload = maps[0];

            if (mapToDownload != null) {
                long total = 0;
                long downloaded = 0;
                String text;
                List<GLMapDownloadTask> tasks = GLMapManager.getDownloadTasks(mapToDownload.getMapID(), GLMapInfo.DataSetMask.ALL);
                if (tasks != null && !tasks.isEmpty()) {
                    for (GLMapDownloadTask task : tasks) {
                        total += task.total;
                        downloaded += task.downloaded;
                    }
                }

                if (total != 0) {
                    long progress = downloaded * 100 / total;
                    text = String.format(
                        Locale.getDefault(),
                        "Downloading %s %d%%",
                        mapToDownload.getLocalizedName(mapView.renderer.getLocaleSettings()),
                        progress);
                } else {
                    text = String.format(Locale.getDefault(), "Download %s", mapToDownload.getLocalizedName(mapView.renderer.getLocaleSettings()));
                }
                btnDownloadMap.setText(text);
            } else {
                btnDownloadMap.setText("Download maps");
            }
        }
    }

    void showEmbedded()
    {
        GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, null, "Montenegro.vm", getAssets(), null);
        zoomToPoint();
    }

    public void updateMapDownloadButton()
    {
        switch (mapView.renderer.getCenterTileState()) {
        case GLMapViewRenderer.GLMapTileState.NoData: {
            if (btnDownloadMap.getVisibility() == View.INVISIBLE) {
                btnDownloadMap.setVisibility(View.VISIBLE);
                btnDownloadMap.getParent().requestLayout();
                updateMapDownloadButtonText();
            }
            break;
        }

        case GLMapViewRenderer.GLMapTileState.Loaded: {
            if (btnDownloadMap.getVisibility() == View.VISIBLE) {
                btnDownloadMap.setVisibility(View.INVISIBLE);
            }
            break;
        }
        case GLMapViewRenderer.GLMapTileState.Unknown:
            break;
        }
    }

    void offlineSearch()
    {
        // You should initialize GLSearch before use, to let it load ICU collation tables and
        // categories.
        GLSearch.Initialize(this);
        GLSearch searchOffline = new GLSearch();
        searchOffline.setCenter(MapPoint.CreateFromGeoCoordinates(42.4341, 19.26)); // Set center of search
        searchOffline.setLimit(20);                                                 // Set maximum number of results. By default is is 100
        searchOffline.setLocaleSettings(mapView.renderer.getLocaleSettings());      // Locale settings to give bonus for results that
        // match to user language
        GLMapLocaleSettings localeSettingsEN = new GLMapLocaleSettings(new String[] {"en", "native"}, GLMapLocaleSettings.UnitSystem.International);
        GLSearchCategory[] category = GLSearchCategories.getShared().getStartedWith(new String[] {"restaurant"},
                                                                                    localeSettingsEN); // find categories by name
        if (category == null || category.length == 0)
            return;

        // Logical operations between filters is AND
        //
        // Let's find all restaurants
        searchOffline.addFilter(GLSearchFilter.createWithCategory(category[0])); // Filter results by category

        // Additionally search for objects with
        // word beginning "Baj" in name or alt_name,
        // "Crno" as word beginning in addr:* tags,
        // and exact "60/1" in addr:* tags.
        //
        // Expected result is restaurant Bajka at Bulevar Ivana Crnojevića 60/1 (
        // https://www.openstreetmap.org/node/4397752292 )
        searchOffline.addFilter(GLSearchFilter.createWithQuery("Baj", GLSearch.TagSetMask.NAME | GLSearch.TagSetMask.ALT_NAME));
        searchOffline.addFilter(GLSearchFilter.createWithQuery("Crno", GLSearch.TagSetMask.ADDRESS));

        GLSearchFilter filter = GLSearchFilter.createWithQuery("60/1", GLSearch.TagSetMask.ADDRESS);
        // Default match type is WordStart. But we could change it to Exact or Word.
        filter.setMatchType(GLSearch.MatchType.EXACT);
        searchOffline.addFilter(filter);

        searchOffline.searchAsync(objects -> runOnUiThread(() -> displaySearchResults(objects)));
    }

    static class SearchStyle extends GLMapMarkerStyleCollectionDataCallback {
        @Override
        public MapPoint getLocation(@NonNull Object marker)
        {
            if (marker instanceof GLMapVectorObject)
                return ((GLMapVectorObject)marker).point();
            return new MapPoint(0, 0);
        }

        @Override
        public void fillUnionData(int markersCount, long nativeMarker)
        {
            // Not called if clustering is off
        }

        @Override
        public void fillData(@NonNull Object marker, long nativeMarker)
        {
            GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0);
        }
    }

    void displaySearchResults(GLMapVectorObjectList objects)
    {
        if (objects == null)
            return;
        Object[] objectsArray = objects.toArray();

        final GLMapMarkerStyleCollection style = new GLMapMarkerStyleCollection();
        SVGRender.Transform transform = SVGRender.transform(0.2f * screenScale, Color.argb(0xFF, 0, 0, 0xFF));
        Bitmap image = Objects.requireNonNull(SVGRender.render(getAssets(), "cluster.svg", transform));
        style.addStyle(new GLMapMarkerImage("marker", image));
        style.setDataCallback(new SearchStyle());
        markerLayer = new GLMapMarkerLayer(objectsArray, style, 0, 4);
        mapView.renderer.add(markerLayer);

        // Zoom to results
        if (objectsArray.length != 0) {
            // Calculate bbox
            final GLMapBBox bbox = new GLMapBBox();
            for (Object object : objectsArray) {
                if (object instanceof GLMapVectorObject) {
                    bbox.addPoint(((GLMapVectorObject)object).point());
                }
            }
            // Zoom to bbox
            mapView.renderer.setMapCenter(bbox.center());
            mapView.renderer.setMapZoom(mapView.renderer.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
        }
    }

    // Example how to calculate zoom level for some bbox
    void zoomToBBox(GLMapBBox bbox)
    {
        // When surface will be created - surfaceWidth and surfaceHeight will have valid values
        mapView.renderer.doWhenSurfaceCreated(() -> {
            GLMapViewRenderer renderer = mapView.renderer;
            renderer.setMapCenter(bbox.center());
            renderer.setMapZoom(renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight));
        });
    }

    void zoomToBBox()
    {
        GLMapBBox bbox = new GLMapBBox();
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102)); // Berlin
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.9024, 27.5618)); // Minsk
        zoomToBBox(bbox);
    }

    void zoomToPoint()
    {
        // New York
        // MapPoint pt = new MapPoint(-74.0059700 , 40.7142700	);

        // Belarus
        // MapPoint pt = new MapPoint(27.56, 53.9);

        // Move map to the Montenegro capital
        MapPoint pt = MapPoint.CreateFromGeoCoordinates(42.4341, 19.26);
        mapView.renderer.setMapCenter(pt);
        mapView.renderer.setMapZoom(16);
    }

    void addPin(float touchX, float touchY)
    {
        if (pins == null)
            pins = new Pins(this);

        if (imageGroup == null) {
            imageGroup = new GLMapImageGroup(pins, 3);
            mapView.renderer.add(imageGroup);
        }

        MapPoint pt = mapView.renderer.convertDisplayToInternal(new MapPoint(touchX, touchY));
        Pin pin = new Pin();
        pin.pos = pt;
        pin.imageVariant = pins.size() % 3;
        pins.add(pin);
        imageGroup.setNeedsUpdate(false);
    }

    void deletePin(float touchX, float touchY)
    {
        Pin pin = pins != null ? pins.findPin(mapView, touchX, touchY) : null;
        if (pin != null) {
            pins.remove(pin);
            imageGroup.setNeedsUpdate(false);
        }
    }

    void deleteMarker(float x, float y)
    {
        if (markerLayer != null) {
            GLMapViewRenderer renderer = mapView.renderer;
            Object[] markersToRemove = markerLayer.objectsNearPoint(renderer, renderer.convertDisplayToInternal(new MapPoint(x, y)), 30);
            if (markersToRemove != null && markersToRemove.length == 1) {
                markerLayer.modify(null, Collections.singleton(markersToRemove[0]), true, () -> Log.d("MarkerLayer", "Marker deleted"));
            }
        }
    }

    void addMarker(float x, float y)
    {
        if (markerLayer != null) {
            MapPoint[] newMarkers = new MapPoint[1];
            newMarkers[0] = mapView.renderer.convertDisplayToInternal(new MapPoint(x, y));

            markerLayer.modify(newMarkers, null, true, () -> Log.d("MarkerLayer", "Marker added"));
        }
    }

    void addMarkerAsVectorObject(float x, float y)
    {
        if (markerLayer != null) {
            GLMapVectorObject[] newMarkers = new GLMapVectorObject[1];
            newMarkers[0] = GLMapVectorObject.createPoint(mapView.renderer.convertDisplayToInternal(new MapPoint(x, y)));
            markerLayer.modify(newMarkers, null, true, () -> Log.d("MarkerLayer", "Marker added"));
        }
    }

    private static final int[] unionColours = {
        Color.argb(255, 33, 0, 255),
        Color.argb(255, 68, 195, 255),
        Color.argb(255, 63, 237, 198),
        Color.argb(255, 15, 228, 36),
        Color.argb(255, 168, 238, 25),
        Color.argb(255, 214, 234, 25),
        Color.argb(255, 223, 180, 19),
        Color.argb(255, 255, 0, 0)};

    void addMarkersWithMapcss()
    {
        final GLMapMarkerStyleCollection styleCollection = new GLMapMarkerStyleCollection();
        for (int i = 0; i < unionColours.length; i++) {
            float scale = (float)(0.2 + 0.1 * i);
            SVGRender.Transform transform = SVGRender.transform(screenScale * scale, unionColours[i]);
            Bitmap bitmap = Objects.requireNonNull(SVGRender.render(getAssets(), "cluster.svg", transform));
            int index = styleCollection.addStyle(new GLMapMarkerImage("marker" + scale, bitmap));
            styleCollection.setStyleName(i, "uni" + index);
        }

        final GLMapVectorCascadeStyle style = Objects.requireNonNull(GLMapVectorCascadeStyle.createStyle(
            "node{icon-image:\"uni0\"; text:eval(tag(\"name\"));"
            + " text-color:#2E2D2B; font-size:12; font-stroke-width:1pt;"
            + " font-stroke-color:#FFFFFFEE;}node[count>=2]{icon-image:\"uni1\";"
            + " text:eval(tag(\"count\"));}node[count>=4]{icon-image:\"uni2\";}"
            + "node[count>=8]{icon-image:\"uni3\";}"
            + "node[count>=16]{icon-image:\"uni4\";}"
            + "node[count>=32]{icon-image:\"uni5\";}"
            + "node[count>=64]{icon-image:\"uni6\";}"
            + "node[count>=128]{icon-image:\"uni7\";}"));

        executor.execute(() -> {
            try {
                Log.w("GLMapView", "Start parsing");
                GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(getAssets().open("cluster_data.json"));
                Log.w("GLMapView", "Finish parsing");

                Log.w("GLMapView", "Start creating layer");
                GLMapMarkerLayer layer = new GLMapMarkerLayer(objects, style, styleCollection, 35, 3);
                GLMapBBox bbox = objects.getBBox();
                Log.w("GLMapView", "Finish creating layer");
                objects.dispose();
                handler.post(() -> {
                    markerLayer = layer;
                    mapView.renderer.add(layer);
                    mapView.renderer.setMapCenter(bbox.center());
                    mapView.renderer.setMapZoom(mapView.renderer.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
                });
            } catch (Exception ignore) {
            }
        });
    }

    static class MarkersStyle extends GLMapMarkerStyleCollectionDataCallback {
        static int[] unionCounts = {1, 2, 4, 8, 16, 32, 64, 128};
        GLMapVectorStyle textStyle = GLMapVectorStyle.createStyle("{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:#FFFFFFEE;}");

        @Override
        public MapPoint getLocation(@NonNull Object marker)
        {
            if (marker instanceof MapPoint) {
                return (MapPoint)marker;
            } else if (marker instanceof GLMapVectorObject) {
                return ((GLMapVectorObject)marker).point();
            }
            return new MapPoint(0, 0);
        }

        @Override
        public void fillUnionData(int markersCount, long nativeMarker)
        {
            for (int i = unionCounts.length - 1; i >= 0; i--) {
                if (markersCount > unionCounts[i]) {
                    GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, i);
                    break;
                }
            }
            GLMapMarkerStyleCollection.setMarkerText(nativeMarker, Integer.toString(markersCount), GLMapTextAlignment.Undefined, new Point(0, 0), textStyle);
        }

        @Override
        public void fillData(@NonNull Object marker, long nativeMarker)
        {
            if (marker instanceof MapPoint) {
                GLMapMarkerStyleCollection.setMarkerText(nativeMarker, "Test", GLMapTextAlignment.Undefined, new Point(0, 0), textStyle);
            } else if (marker instanceof GLMapVectorObject) {
                GLMapValue nameValue = ((GLMapVectorObject)marker).valueForKey("name");
                if (nameValue != null) {
                    String name = nameValue.getString();
                    if (name != null) {
                        GLMapMarkerStyleCollection.setMarkerText(nativeMarker, name, GLMapTextAlignment.Undefined, new Point(0, 15 / 2), textStyle);
                    }
                }
            }
            GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0);
        }
    }

    void addMarkers()
    {
        executor.execute(() -> {
            try {
                GLMapMarkerStyleCollection style = new GLMapMarkerStyleCollection();
                for (int i = 0; i < MarkersStyle.unionCounts.length; i++) {
                    float scale = (float)(0.2 + 0.1 * i);
                    SVGRender.Transform transform = SVGRender.transform(scale * screenScale, unionColours[i]);
                    Bitmap bitmap = Objects.requireNonNull(SVGRender.render(getAssets(), "cluster.svg", transform));
                    style.addStyle(new GLMapMarkerImage("marker" + scale, bitmap));
                }
                style.setDataCallback(new MarkersStyle());

                Log.w("GLMapView", "Start parsing");
                GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(getAssets().open("cluster_data.json"));
                Log.w("GLMapView", "Finish parsing");
                Log.w("GLMapView", "Start creating layer");
                GLMapMarkerLayer layer = new GLMapMarkerLayer(objects.toArray(), style, 35, 3);
                Log.w("GLMapView", "Finish creating layer");
                GLMapBBox bbox = objects.getBBox();
                objects.dispose();
                handler.post(() -> {
                    markerLayer = layer;
                    GLMapViewRenderer renderer = mapView.renderer;
                    renderer.add(layer);
                    renderer.setMapCenter(bbox.center());
                    renderer.setMapZoom(renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight));
                });
            } catch (Exception ignore) {
            }
        });
    }

    void addImage(final Button btn)
    {
        SVGRender.Transform transform = SVGRender.transform(screenScale);
        Bitmap bmp = Objects.requireNonNull(SVGRender.render(getAssets(), "arrow-maphint.svg", transform));
        image = new GLMapImage(2);
        image.setBitmap(bmp);
        image.setOffset(bmp.getWidth(), bmp.getHeight() / 2);
        image.setRotatesWithMap(true);
        image.setAngle((float)Math.random() * 360);
        image.setPosition(mapView.renderer.getMapCenter());
        mapView.renderer.add(image);

        btn.setText("Move image");
        btn.setOnClickListener(v -> moveImage(btn));
    }

    void moveImage(final Button btn)
    {
        image.setPosition(mapView.renderer.getMapCenter());
        btn.setText("Remove image");
        btn.setOnClickListener(v -> delImage(btn));
    }

    void delImage(final Button btn)
    {
        if (image != null) {
            mapView.renderer.remove(image);
            image.dispose();
            image = null;
        }
        btn.setText("Add image");
        btn.setOnClickListener(v -> addImage(btn));
    }

    void addMultiline()
    {
        MapPoint[] line1 = new MapPoint[5];
        line1[0] = MapPoint.CreateFromGeoCoordinates(53.8869, 27.7151); // Minsk
        line1[1] = MapPoint.CreateFromGeoCoordinates(50.4339, 30.5186); // Kiev
        line1[2] = MapPoint.CreateFromGeoCoordinates(52.2251, 21.0103); // Warsaw
        line1[3] = MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102); // Berlin
        line1[4] = MapPoint.CreateFromGeoCoordinates(48.8505, 2.3343);  // Paris

        MapPoint[] line2 = new MapPoint[3];
        line2[0] = MapPoint.CreateFromGeoCoordinates(52.3690, 4.9021); // Amsterdam
        line2[1] = MapPoint.CreateFromGeoCoordinates(50.8263, 4.3458); // Brussel
        line2[2] = MapPoint.CreateFromGeoCoordinates(49.6072, 6.1296); // Luxembourg

        MapPoint[][] multiline = {line1, line2};
        final GLMapVectorObject obj = GLMapVectorObject.createMultiline(multiline);
        // style applied to all lines added. Style is string with mapcss rules. Read more in manual.

        GLMapVectorLayer drawable = new GLMapVectorLayer();
        drawable.setVectorObject(obj, Objects.requireNonNull(GLMapVectorCascadeStyle.createStyle("line{width: 2pt;color:green;layer:100;}")), null);
        mapView.renderer.add(drawable);
    }

    void addPolygon()
    {
        int pointCount = 25;
        MapGeoPoint[] outerRing = new MapGeoPoint[pointCount];
        MapGeoPoint[] innerRing = new MapGeoPoint[pointCount];

        float rOuter = 10, rInner = 5;
        MapGeoPoint centerPoint = new MapGeoPoint(53, 27);

        // let's display circle
        for (int i = 0; i < pointCount; i++) {
            double sin = Math.sin(2 * Math.PI / pointCount * i);
            double cos = Math.cos(2 * Math.PI / pointCount * i);
            outerRing[i] = new MapGeoPoint(centerPoint.lat + sin * rOuter, centerPoint.lon + cos * rOuter);
            innerRing[i] = new MapGeoPoint(centerPoint.lat + sin * rInner, centerPoint.lon + cos * rInner);
        }

        MapGeoPoint[][] outerRings = {outerRing};
        MapGeoPoint[][] innerRings = {innerRing};

        GLMapVectorObject obj = GLMapVectorObject.createPolygonGeo(outerRings, innerRings);
        GLMapVectorLayer drawable = new GLMapVectorLayer();
        // #RRGGBBAA format
        drawable.setVectorObject(
            obj,
            Objects.requireNonNull(GLMapVectorCascadeStyle.createStyle(
                "area{fill-color:#10106050; fill-color:#10106050;"
                + " width:4pt;color:green;}")),
            null);
        mapView.renderer.add(drawable);

        mapView.renderer.setMapGeoCenter(centerPoint);
    }

    private void downloadInBBox()
    {
        GLMapBBox bbox = new GLMapBBox();
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53, 27));
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.5, 27.5));
        zoomToBBox(bbox);

        File cacheDir = getCacheDir();
        File mapPath = new File(cacheDir, "test.vmtar");
        File navigationPath = new File(cacheDir, "test.navtar");
        File elevationPath = new File(cacheDir, "test.eletar");

        if (mapPath.exists())
            GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, bbox, mapPath.getAbsolutePath(), null, null);
        if (navigationPath.exists())
            GLMapManager.AddDataSet(GLMapInfo.DataSet.NAVIGATION, bbox, navigationPath.getAbsolutePath(), null, null);
        if (elevationPath.exists())
            GLMapManager.AddDataSet(GLMapInfo.DataSet.ELEVATION, bbox, elevationPath.getAbsolutePath(), null, null);

        mapView.renderer.setDrawElevationLines(true);
        mapView.renderer.setDrawHillshades(true);
        mapView.renderer.reloadTiles();

        class Action {
            String title;
            int dataSet;
            File path;
        }

        final Button btn = this.findViewById(R.id.button_action);
        final Action action;

        if (!mapPath.exists()) {
            action = new Action();
            action.title = "Download map";
            action.dataSet = GLMapInfo.DataSet.MAP;
            action.path = mapPath;
        } else if (!navigationPath.exists()) {
            action = new Action();
            action.title = "Download navigation";
            action.dataSet = GLMapInfo.DataSet.NAVIGATION;
            action.path = navigationPath;
        } else if (!elevationPath.exists()) {
            action = new Action();
            action.title = "Download elevation";
            action.dataSet = GLMapInfo.DataSet.ELEVATION;
            action.path = elevationPath;
        } else {
            action = new Action();
            action.title = "Calc route";
        }

        if (action != null) {
            btn.setVisibility(View.VISIBLE);
            btn.setText(action.title);
            btn.setOnClickListener(view -> {
                if (action.path != null) {
                    GLMapManager.DownloadDataSet(action.dataSet, action.path.getAbsolutePath(), bbox, new GLMapManager.DownloadCallback() {
                        @Override
                        public void onProgress(long totalSize, long downloadedSize, double downloadSpeed)
                        {
                            Log.i("BulkDownload", String.format("Download %d stats: %d, %f", action.dataSet, downloadedSize, downloadSpeed));
                        }

                        @Override
                        public void onFinished(@Nullable GLMapError error)
                        {
                            if (error == null) {
                                downloadInBBox();
                            }
                        }
                    });
                } else {
                    GLRouteRequest request = new GLRouteRequest();
                    request.addPoint(new GLRoutePoint(new MapGeoPoint(53.2328, 27.2699), Double.NaN, true, true));
                    request.addPoint(new GLRoutePoint(new MapGeoPoint(53.1533, 27.0909), Double.NaN, true, true));
                    request.setLocale("en");
                    request.setAutoWithOptions(new CostingOptions.Auto());
                    request.setOfflineWithConfig(getValhallaConfig(getResources()));
                    request.start(new GLRouteRequest.ResultsCallback() {
                        @Override
                        public void onResult(@NonNull GLRoute route)
                        {
                            Log.i("Route", "Success");
                            GLMapTrackData trackData = route.getTrackData(Color.argb(255, 255, 0, 0));
                            if (track != null) {
                                track.setData(trackData, trackStyle, null);
                            } else {
                                track = new GLMapTrack(5);
                                track.setData(trackData, trackStyle, null);
                                mapView.renderer.add(track);
                            }
                        }

                        @Override
                        public void onError(@NonNull GLMapError error)
                        {
                            Log.i("Route", "Error: " + error);
                        }
                    });
                }
            });
        } else {
            btn.setVisibility(View.GONE);
        }
    }

    private void loadDarkTheme()
    {
        GLMapStyleParser parser = new GLMapStyleParser(getAssets(), "DefaultStyle.bundle");
        parser.setOptions(Collections.singletonMap("Theme", "Dark"), true);
        mapView.renderer.setStyle(Objects.requireNonNull(parser.parseFromResources()));
    }

    private void loadCustomStyle(byte[] newStyleData)
    {
        GLMapStyleParser parser = new GLMapStyleParser(name -> {
            byte[] rv;
            if (name.equals("Style.mapcss")) {
                rv = newStyleData;
            } else {
                try {
                    InputStream stream = getAssets().open("DefaultStyle.bundle/" + name);
                    rv = new byte[stream.available()];
                    if (stream.read(rv) < rv.length) {
                        rv = null;
                    }
                    stream.close();
                } catch (IOException ignore) {
                    rv = null;
                }
            }
            return rv;
        });
        mapView.renderer.setStyle(Objects.requireNonNull(parser.parseFromResources()));
    }

    private void styleLiveReload()
    {
        final EditText editText = this.findViewById(R.id.edit_text);
        editText.setVisibility(View.VISIBLE);

        final Button btn = this.findViewById(R.id.button_action);
        btn.setVisibility(View.VISIBLE);
        btn.setText("Reload");
        btn.setOnClickListener(view -> {
            String url = editText.getText().toString();
            executor.execute(() -> {
                try {
                    URLConnection connection = new URL(url).openConnection();
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();

                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    byte[] newStyleData = buffer.toByteArray();
                    buffer.close();
                    inputStream.close();

                    handler.post(() -> loadCustomStyle(newStyleData));
                } catch (Exception ignore) {
                }
            });
        });
    }

    int colorForTrack(float angle)
    {
        float[] hsv = new float[] {(float)(angle * 180.f / Math.PI) % 360, 1.f, 0.5f};

        return Color.HSVToColor(hsv);
    }

    private void recordTrack()
    {
        final float rStart = 10f;
        final float rDelta = (float)(Math.PI / 30);
        final float rDiff = 0.01f;
        final float clat = 30f, clon = 30f;

        // Create trackData with initial data
        trackPointIndex = 100;
        trackData = new GLMapTrackData(
            (index, nativePoint)
                -> GLMapTrackData.setPointDataGeo(
                    nativePoint,
                    clat + Math.sin(rDelta * index) * (rStart - rDiff * index),
                    clon + Math.cos(rDelta * index) * (rStart - rDiff * index),
                    colorForTrack(rDelta * index)),
            trackPointIndex);

        track = new GLMapTrack(2);
        // To use files from style, (e.g. track-arrow.svgpb) you should create DefaultStyle.bundle
        // inside assets and put all additional resources inside.
        track.setData(trackData, trackStyle, null);

        mapView.renderer.add(track);
        mapView.renderer.setMapCenter(MapPoint.CreateFromGeoCoordinates(clat, clon));
        mapView.renderer.setMapZoom(4);

        trackRecordRunnable = () ->
        {
            // Create new trackData with additional point
            GLMapTrackData newData = trackData.copyTrackAndAddGeoPoint(
                clat + Math.sin(rDelta * trackPointIndex) * (rStart - rDiff * trackPointIndex),
                clon + Math.cos(rDelta * trackPointIndex) * (rStart - rDiff * trackPointIndex),
                colorForTrack(rDelta * trackPointIndex),
                false);
            // Set data to track
            track.setData(newData, trackStyle, null);

            trackData.dispose(); // Release native data before GC will occur
            trackData = newData;

            trackPointIndex++;
            handler.postDelayed(trackRecordRunnable, 1000);
        };
        // Let's one more point every second.
        handler.postDelayed(trackRecordRunnable, 1000);
    }

    private void zoomToObjects(GLMapVectorObjectList objects)
    {
        // Zoom to bbox
        GLMapBBox bbox = objects.getBBox();
        mapView.renderer.doWhenSurfaceCreated(() -> {
            mapView.renderer.setMapCenter(bbox.center());
            mapView.renderer.setMapZoom(mapView.renderer.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
        });
    }

    private void loadGeoJSONPostcode()
    {
        try {
            GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(getAssets().open("uk_postcodes.geojson"));

            GLMapVectorCascadeStyle style = Objects.requireNonNull(GLMapVectorCascadeStyle.createStyle("area{fill-color:green; width:1pt; color:red;}"));
            GLMapVectorLayer drawable = new GLMapVectorLayer();
            drawable.setVectorObjects(objects, style, null);
            mapView.renderer.add(drawable);
            zoomToObjects(objects);
            gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e)
                {
                    for (int i = 0; i < objects.size(); i++) {
                        GLMapVectorObject obj = objects.get(i);
                        MapPoint mapPoint = new MapPoint(e.getX(), e.getY());
                        mapPoint = mapView.renderer.convertDisplayToInternal(mapPoint);
                        // When checking polygons it will check if point is inside
                        // polygon.
                        // For lines and points it will check if distance is less
                        // then maxDistance.
                        if (obj.findNearestPoint(mapView.renderer, mapPoint, 10.0) != null) {
                            String message = "Tapped object: " + obj.asGeoJSON();
                            Toast.makeText(MapViewActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e)
                {}
            });

            mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadGeoJSONWithCSSStyle()
    {
        GLMapVectorObjectList objects;
        try {
            objects = GLMapVectorObject.createFromGeoJSONOrThrow(
                "[{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\","
                + " \"coordinates\": [30.5186, 50.4339]}, \"properties\": {\"id\":"
                + " \"1\", \"text\": \"test1\"}},{\"type\": \"Feature\","
                + " \"geometry\": {\"type\": \"Point\", \"coordinates\": [27.7151,"
                + " 53.8869]}, \"properties\": {\"id\": \"2\", \"text\":"
                + " \"test2\"}},{\"type\":\"LineString\",\"coordinates\": ["
                + " [27.7151, 53.8869], [30.5186, 50.4339], [21.0103, 52.2251],"
                + " [13.4102, 52.5037], [2.3343,"
                + " 48.8505]]},{\"type\":\"Polygon\",\"coordinates\":[[ [0.0,"
                + " 10.0], [10.0, 10.0], [10.0, 20.0], [0.0, 20.0] ],[ [2.0, 12.0],"
                + " [ 8.0, 12.0], [ 8.0, 18.0], [2.0, 18.0] ]]}]");
        } catch (Exception e) {
            objects = null;
            e.printStackTrace();
        }

        GLMapVectorCascadeStyle style = Objects.requireNonNull(GLMapVectorCascadeStyle.createStyle(
            "node[id=1]{icon-image:\"bus.svgpb\";icon-scale:0.5;icon-tint:green;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
            +
            "node|z-9[id=2]{icon-image:\"bus.svgpb\";icon-scale:0.7;icon-tint:blue;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}line{linecap:"
            + " round; width: 5pt; color:blue;}area{fill-color:green;"
            + " width:1pt; color:red;}"));

        if (objects != null) {
            GLMapVectorLayer drawable = new GLMapVectorLayer();
            drawable.setVectorObjects(objects, style, null);
            mapView.renderer.add(drawable);
            zoomToObjects(objects);
        }
    }

    private void loadGeoJSON()
    {
        // loadGeoJSONWithCSSStyle();
        loadGeoJSONPostcode();
    }

    void captureScreen()
    {
        GLMapView mapView = this.findViewById(R.id.map_view);
        mapView.renderer.captureFrameWhenFinish(this);
    }

    @Override
    public void screenCaptured(final Bitmap bmp)
    {
        this.runOnUiThread(() -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            try {
                FileOutputStream fo = openFileOutput("screenCapture", Context.MODE_PRIVATE);
                fo.write(bytes.toByteArray());
                fo.close();

                File file = new File(getExternalFilesDir(null), "Test.jpg");
                fo = new FileOutputStream(file);
                fo.write(bytes.toByteArray());
                fo.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(MapViewActivity.this, DisplayImageActivity.class);
            Bundle b = new Bundle();
            b.putString("imageName", "screenCapture");
            intent.putExtras(b);
            startActivity(intent);
        });
    }
}
