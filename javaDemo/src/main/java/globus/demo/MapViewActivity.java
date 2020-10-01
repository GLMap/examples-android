package globus.demo;

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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import globus.glmap.GLMapBBox;
import globus.glmap.GLMapDownloadTask;
import globus.glmap.GLMapDrawable;
import globus.glmap.GLMapError;
import globus.glmap.GLMapImageGroup;
import globus.glmap.GLMapImageGroupCallback;
import globus.glmap.GLMapInfo;
import globus.glmap.GLMapLocaleSettings;
import globus.glmap.GLMapManager;
import globus.glmap.GLMapMarkerImage;
import globus.glmap.GLMapMarkerLayer;
import globus.glmap.GLMapMarkerStyleCollection;
import globus.glmap.GLMapMarkerStyleCollectionDataCallback;
import globus.glmap.GLMapRasterTileSource;
import globus.glmap.GLMapStyleParser;
import globus.glmap.GLMapTrack;
import globus.glmap.GLMapTrackData;
import globus.glmap.GLMapVectorCascadeStyle;
import globus.glmap.GLMapVectorObject;
import globus.glmap.GLMapVectorObjectList;
import globus.glmap.GLMapVectorStyle;
import globus.glmap.GLMapView;
import globus.glmap.GLMapView.GLMapPlacement;
import globus.glmap.GLMapView.GLMapTileState;
import globus.glmap.GLMapView.GLUnitSystem;
import globus.glmap.ImageManager;
import globus.glmap.MapGeoPoint;
import globus.glmap.MapPoint;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

@ParametersAreNonnullByDefault
@SuppressLint({"ClickableViewAccessibility", "StaticFieldLeak", "SetTextI18n"})
public class MapViewActivity extends Activity
    implements GLMapView.ScreenCaptureCallback, GLMapManager.StateListener {

  private static class Pin {
    MapPoint pos;
    int imageVariant;
  }

  private static class Pins implements GLMapImageGroupCallback {
    private ReentrantLock lock;
    private Bitmap[] images;
    private List<Pin> pins;

    Pins(ImageManager imageManager) {
      lock = new ReentrantLock();
      images = new Bitmap[3];
      pins = new ArrayList<>();
      images[0] = imageManager.open("1.svgpb", 1, 0xFFFF0000);
      images[1] = imageManager.open("2.svgpb", 1, 0xFF00FF00);
      images[2] = imageManager.open("3.svgpb", 1, 0xFF0000FF);
    }

    @Override
    public int getImagesCount() {
      return pins.size();
    }

    @Override
    public int getImageIndex(int i) {
      return pins.get(i).imageVariant;
    }

    @Override
    public MapPoint getImagePos(int i) {
      return pins.get(i).pos;
    }

    @Override
    public void updateStarted() {
      Log.i("GLMapImageGroupCallback", "Update started");
      lock.lock();
    }

    @Override
    public void updateFinished() {
      Log.i("GLMapImageGroupCallback", "Update finished");
      lock.unlock();
    }

    @Override
    public int getImageVariantsCount() {
      return images.length;
    }

    @Override
    public Bitmap getImageVariantBitmap(int i) {
      return images[i];
    }

    @Override
    public MapPoint getImageVariantOffset(int i) {
      return new MapPoint(images[i].getWidth() / 2.0, 0);
    }

    int size() {
      int rv;
      lock.lock();
      rv = pins.size();
      lock.unlock();
      return rv;
    }

    void add(Pin pin) {
      lock.lock();
      pins.add(pin);
      lock.unlock();
    }

    void remove(Pin pin) {
      lock.lock();
      pins.remove(pin);
      lock.unlock();
    }

    Pin findPin(GLMapView mapView, float touchX, float touchY) {
      Pin rv = null;
      lock.lock();
      for (int i = 0; i < pins.size(); ++i) {
        Pin pin = pins.get(i);

        MapPoint screenPos = mapView.convertInternalToDisplay(new MapPoint(pin.pos));
        Rect rt = new Rect(-40, -40, 40, 40);
        rt.offset((int) screenPos.x, (int) screenPos.y);
        if (rt.contains((int) touchX, (int) touchY)) {
          rv = pin;
          break;
        }
      }
      lock.unlock();
      return rv;
    }
  }

  private GLMapDrawable image;
  private GLMapImageGroup imageGroup;
  private Pins pins;
  private GestureDetector gestureDetector;
  protected GLMapView mapView;
  private GLMapInfo mapToDownload;
  private Button btnDownloadMap;

  private GLMapMarkerLayer markerLayer;
  private GLMapLocaleSettings localeSettings;
  private CurLocationHelper curLocationHelper;

  private int trackPointIndex;
  private GLMapTrack track;
  private GLMapTrackData trackData;
  private Handler handler;
  private Runnable trackRecordRunnable;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getLayoutID());
    mapView = this.findViewById(R.id.map_view);

    // Map list is updated, because download button depends on available map list and during first
    // launch this list is empty
    GLMapManager.UpdateMapList(null);

    btnDownloadMap = this.findViewById(R.id.button_dl_map);
    btnDownloadMap.setOnClickListener(v -> {
      if (mapToDownload != null) {
        List<GLMapDownloadTask> tasks = GLMapManager.getDownloadTasks(mapToDownload.getMapID(), GLMapInfo.DataSetMask.ALL);
        if (tasks != null && !tasks.isEmpty()) {
          for(GLMapDownloadTask task : tasks)
            task.cancel();
        } else {
          GLMapManager.DownloadDataSets(mapToDownload, GLMapInfo.DataSetMask.ALL);
        }
        updateMapDownloadButtonText();
      } else {
        Intent i = new Intent(v.getContext(), DownloadActivity.class);

        MapPoint pt = mapView.getMapCenter();
        i.putExtra("cx", pt.x);
        i.putExtra("cy", pt.y);
        v.getContext().startActivity(i);
      }
    });

    GLMapManager.addStateListener(this);

    localeSettings = new GLMapLocaleSettings();
    mapView.setLocaleSettings(localeSettings);

    GLMapStyleParser parser = new GLMapStyleParser(getAssets(), "DefaultStyle.bundle");
    mapView.setStyle(parser.parseFromResources());
    checkAndRequestLocationPermission();

    mapView.setScaleRulerStyle(
        GLUnitSystem.International, GLMapPlacement.BottomCenter, new MapPoint(10, 10), 200);
    mapView.setAttributionPosition(GLMapPlacement.TopCenter);

    mapView.setCenterTileStateChangedCallback(this::updateMapDownloadButton);
    mapView.setMapDidMoveCallback(this::updateMapDownloadButtonText);

    runTest();
  }

  protected int getLayoutID() {
    return R.layout.map;
  }

  protected void runTest() {
    Bundle b = getIntent().getExtras();
    SampleSelectActivity.Samples example;
    if(b != null)
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
        mapView.setTileSources(new GLMapRasterTileSource[] {new OSMTileSource(this)});
        break;
      case ZOOM_BBOX:
        zoomToBBox();
        break;
      case FLY_TO:
        {
          mapView.setMapCenter(MapPoint.CreateFromGeoCoordinates(37.3257, -122.0353));
          mapView.setMapZoom(14);

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
            mapView.animate(animation -> {
              mapView.setMapZoom(15);
              animation.flyToPoint(point);
            });
          });
          GLMapManager.SetTileDownloadingAllowed(true);
          break;
        }
      case OFFLINE_SEARCH:
        GLMapManager.AddMap(getAssets(), "Montenegro.vm", null);
        zoomToPoint();
        offlineSearch();
        break;
      case MARKERS:
        mapView.setLongClickable(true);

        gestureDetector =
            new GestureDetector(
                this,
                new SimpleOnGestureListener() {
                  @Override
                  public boolean onSingleTapConfirmed(MotionEvent e) {
                    deleteMarker(e.getX(), e.getY());
                    return true;
                  }

                  @Override
                  public void onLongPress(MotionEvent e) {
                    addMarker(e.getX(), e.getY());
                  }
                });

        mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));

        addMarkers();
        GLMapManager.SetTileDownloadingAllowed(true);
        break;
      case MARKERS_MAPCSS:
        addMarkersWithMapcss();

        gestureDetector =
            new GestureDetector(
                this,
                new SimpleOnGestureListener() {
                  @Override
                  public boolean onSingleTapConfirmed(MotionEvent e) {
                    deleteMarker(e.getX(), e.getY());
                    return true;
                  }

                  @Override
                  public void onLongPress(MotionEvent e) {
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
        {
          Button btn = this.findViewById(R.id.button_action);
          btn.setVisibility(View.VISIBLE);
          delImage(btn);
          break;
        }
      case IMAGE_MULTI:
        mapView.setLongClickable(true);

        gestureDetector =
            new GestureDetector(
                this,
                new SimpleOnGestureListener() {
                  @Override
                  public boolean onSingleTapConfirmed(MotionEvent e) {
                    deletePin(e.getX(), e.getY());
                    return true;
                  }

                  @Override
                  public void onLongPress(MotionEvent e) {
                    addPin(e.getX(), e.getY());
                  }
                });

        mapView.setOnTouchListener((arg0, ev) -> gestureDetector.onTouchEvent(ev));
        break;
      case GEO_JSON:
        loadGeoJSON();
        break;
      case TILES_BULK_DOWNLOAD:
        bulkDownload();
        break;
      case STYLE_LIVE_RELOAD:
        styleLiveReload();
        break;
      case RECORD_TRACK:
        recordTrack();
        break;
    }

    mapView.setMapDidMoveCallback(() -> {
      if (example == SampleSelectActivity.Samples.CALLBACK_TEST) {
        Log.w("GLMapView", "Did move");
      }
      updateMapDownloadButtonText();
    });
  }

  public void checkAndRequestLocationPermission() {
    // Create helper if not exist
    if (curLocationHelper == null) curLocationHelper = new CurLocationHelper(mapView);

    // Try to start location updates. If we need permissions - ask for them
    if (!curLocationHelper.initLocationManager(this))
      ActivityCompat.requestPermissions(
          this,
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          },
          0);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch (requestCode) {
      case 0:
        {
          if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            curLocationHelper.initLocationManager(this);
          break;
        }
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        break;
    }
  }

  @Override
  protected void onDestroy() {
    GLMapManager.removeStateListener(this);
    if (mapView != null) {
      mapView.removeAllObjects();
      mapView.setCenterTileStateChangedCallback(null);
      mapView.setMapDidMoveCallback(null);
    }

    if (markerLayer != null) {
      markerLayer.dispose();
      markerLayer = null;
    }

    if (imageGroup != null) {
      imageGroup.dispose();
      imageGroup = null;
    }

    if (curLocationHelper != null) {
      curLocationHelper.onDestroy();
      curLocationHelper = null;
    }

    if (handler != null) {
      handler.removeCallbacks(trackRecordRunnable);
      handler = null;
    }
    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    mapView.animate(animation -> mapView.setMapZoom(mapView.getMapZoom() - 1));
    return false;
  }

  @Override
  public void onStartDownloading(GLMapDownloadTask task) {}

  @Override
  public void onDownloadProgress(GLMapDownloadTask task) {
    updateMapDownloadButtonText();
  }

  @Override
  public void onFinishDownloading(GLMapDownloadTask task) {
    mapView.reloadTiles();
  }

  @Override
  public void onStateChanged(GLMapInfo map) {
    updateMapDownloadButtonText();
  }

  private void updateMapDownloadButtonText() {
    if (btnDownloadMap.getVisibility() == View.VISIBLE) {
      MapPoint center = mapView.getMapCenter();

      GLMapInfo[] maps = GLMapManager.MapsAtPoint(center);
      if(maps == null || maps.length == 0)
        mapToDownload = null;
      else
        mapToDownload = maps[0];

      if (mapToDownload != null) {
        long total = 0;
        long downloaded = 0;
        String text;
        List<GLMapDownloadTask> tasks = GLMapManager.getDownloadTasks(mapToDownload.getMapID(), GLMapInfo.DataSetMask.ALL);
        if (tasks != null && !tasks.isEmpty()) {
          for(GLMapDownloadTask task : tasks) {
            total += task.total;
            downloaded += task.downloaded;
          }
        }

        if(total != 0) {
          long progress = downloaded * 100 / total;
          text = String.format(Locale.getDefault(), "Downloading %s %d%%",
                  mapToDownload.getLocalizedName(localeSettings), progress);
        }else {
          text = String.format(Locale.getDefault(), "Download %s",
                  mapToDownload.getLocalizedName(localeSettings));
        }
        btnDownloadMap.setText(text);
      } else {
        btnDownloadMap.setText("Download maps");
      }
    }
  }

  void showEmbedded() {
    if (!GLMapManager.AddMap(getAssets(), "Montenegro.vm", null)) {
      // Failed to unpack to caches. Check free space.
    }
    zoomToPoint();
  }

  public void updateMapDownloadButton() {
    switch (mapView.getCenterTileState()) {
      case GLMapTileState.NoData:
        {
          if (btnDownloadMap.getVisibility() == View.INVISIBLE) {
            btnDownloadMap.setVisibility(View.VISIBLE);
            btnDownloadMap.getParent().requestLayout();
            updateMapDownloadButtonText();
          }
          break;
        }

      case GLMapTileState.Loaded:
        {
          if (btnDownloadMap.getVisibility() == View.VISIBLE) {
            btnDownloadMap.setVisibility(View.INVISIBLE);
          }
          break;
        }
      case GLMapTileState.Unknown:
        break;
    }
  }

  void offlineSearch() {
    // You should initialize GLSearch before use, to let it load ICU collation tables and categories.
    GLSearch.Initialize(this);
    GLSearch searchOffline = new GLSearch();
    searchOffline.setCenter(MapPoint.CreateFromGeoCoordinates(42.4341, 19.26)); // Set center of search
    searchOffline.setLimit(20); // Set maximum number of results. By default is is 100
    searchOffline.setLocaleSettings(mapView.getLocaleSettings()); // Locale settings to give bonus for results that match to user language
    GLSearchCategory[] category = GLSearchCategories.getShared().getStartedWith(new String[]{"restaurant"}, new GLMapLocaleSettings(new String[]{"en", "native"})); // find categories by name
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
    // Expected result is restaurant Bajka at Bulevar Ivana Crnojevića 60/1 ( https://www.openstreetmap.org/node/4397752292 )
    searchOffline.addFilter(GLSearchFilter.createWithQuery("Baj", GLSearch.TagSetMask.NAME | GLSearch.TagSetMask.ALT_NAME));
    searchOffline.addFilter(GLSearchFilter.createWithQuery("Crno", GLSearch.TagSetMask.ADDRESS));

    GLSearchFilter filter = GLSearchFilter.createWithQuery("60/1", GLSearch.TagSetMask.ADDRESS);
    // Default match type is WordStart. But we could change it to Exact or Word.
    filter.setMatchType(GLSearch.MatchType.EXACT);
    searchOffline.addFilter(filter);

    searchOffline.searchAsync(objects -> runOnUiThread(() -> displaySearchResults(objects.toArray())));
  }

  static class SearchStyle extends GLMapMarkerStyleCollectionDataCallback {
    @Override
    public MapPoint getLocation(Object marker) {
      if (marker instanceof GLMapVectorObject) return ((GLMapVectorObject) marker).point();
      return new MapPoint(0, 0);
    }

    @Override
    public void fillUnionData(int markersCount, long nativeMarker) {
      // Not called if clustering is off
    }

    @Override
    public void fillData(Object marker, long nativeMarker) {
      GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0);
    }
  }

  void displaySearchResults(Object[] objects) {
    final GLMapMarkerStyleCollection style = new GLMapMarkerStyleCollection();
    style.addStyle(
        new GLMapMarkerImage(
            "marker", mapView.imageManager.open("cluster.svgpb", 0.2f, Color.argb(0xFF,0, 0, 0xFF))));
    style.setDataCallback(new SearchStyle());
    markerLayer = new GLMapMarkerLayer(objects, style, 0, 4);
    mapView.add(markerLayer);

    // Zoom to results
    if (objects.length != 0) {
      // Calculate bbox
      final GLMapBBox bbox = new GLMapBBox();
      for (Object object : objects) {
        if (object instanceof GLMapVectorObject) {
          bbox.addPoint(((GLMapVectorObject) object).point());
        }
      }
      // Zoom to bbox
      mapView.setMapCenter(bbox.center());
      mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
    }
  }

  // Example how to calculate zoom level for some bbox
  void zoomToBBox() {
    // When surface will be created - getWidth and getHeight will have valid values
    mapView.doWhenSurfaceCreated(() -> {
      GLMapBBox bbox = new GLMapBBox();
      bbox.addPoint(MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102)); // Berlin
      bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.9024, 27.5618)); // Minsk

      mapView.setMapCenter(bbox.center());
      mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
    });
  }

  void zoomToPoint() {
    // New York
    // MapPoint pt = new MapPoint(-74.0059700 , 40.7142700	);

    // Belarus
    // MapPoint pt = new MapPoint(27.56, 53.9);
    // ;

    // Move map to the Montenegro capital
    MapPoint pt = MapPoint.CreateFromGeoCoordinates(42.4341, 19.26);
    GLMapView mapView = this.findViewById(R.id.map_view);
    mapView.setMapCenter(pt);
    mapView.setMapZoom(16);
  }

  void addPin(float touchX, float touchY) {
    if (pins == null) pins = new Pins(mapView.imageManager);

    if (imageGroup == null) {
      imageGroup = new GLMapImageGroup(pins, 3);
      mapView.add(imageGroup);
    }

    MapPoint pt = mapView.convertDisplayToInternal(new MapPoint(touchX, touchY));
    Pin pin = new Pin();
    pin.pos = pt;
    pin.imageVariant = pins.size() % 3;
    pins.add(pin);
    imageGroup.setNeedsUpdate(false);
  }

  void deletePin(float touchX, float touchY) {
    Pin pin = pins != null ? pins.findPin(mapView, touchX, touchY) : null;
    if (pin != null) {
      pins.remove(pin);
      imageGroup.setNeedsUpdate(false);
    }
  }

  void deleteMarker(float x, float y) {
    if (markerLayer != null) {
      Object[] markersToRemove = markerLayer.objectsNearPoint(mapView, mapView.convertDisplayToInternal(new MapPoint(x, y)), 30);
      if (markersToRemove != null && markersToRemove.length == 1) {
        markerLayer.modify(
            null,
            Collections.singleton(markersToRemove[0]),
            null,
            true, () -> Log.d("MarkerLayer", "Marker deleted"));
      }
    }
  }

  void addMarker(float x, float y) {
    if (markerLayer != null) {
      MapPoint[] newMarkers = new MapPoint[1];
      newMarkers[0] = mapView.convertDisplayToInternal(new MapPoint(x, y));

      markerLayer.modify(
          newMarkers,
          null,
          null,
          true, () -> Log.d("MarkerLayer", "Marker added"));
    }
  }

  void addMarkerAsVectorObject(float x, float y) {
    if (markerLayer != null) {
      GLMapVectorObject[] newMarkers = new GLMapVectorObject[1];
      newMarkers[0] =
          GLMapVectorObject.createPoint(mapView.convertDisplayToInternal(new MapPoint(x, y)));

      markerLayer.modify(
          newMarkers,
          null,
          null,
          true, () -> Log.d("MarkerLayer", "Marker added"));
    }
  }

  private static int[] unionColours = {Color.argb(255, 33, 0, 255),
          Color.argb(255, 68, 195, 255),
          Color.argb(255, 63, 237, 198),
          Color.argb(255, 15, 228, 36),
          Color.argb(255, 168, 238, 25),
          Color.argb(255, 214, 234, 25),
          Color.argb(255, 223, 180, 19),
          Color.argb(255, 255, 0, 0)};

  void addMarkersWithMapcss() {
    final GLMapMarkerStyleCollection styleCollection = new GLMapMarkerStyleCollection();
    for (int i = 0; i < unionColours.length; i++) {
      float scale = (float) (0.2 + 0.1 * i);
      int index =
          styleCollection.addStyle(
              new GLMapMarkerImage(
                  "marker" + scale,
                  mapView.imageManager.open("cluster.svgpb", scale, unionColours[i])));
      styleCollection.setStyleName(i, "uni" + index);
    }

    final GLMapVectorCascadeStyle style =
        GLMapVectorCascadeStyle.createStyle(
            "node{icon-image:\"uni0\"; text:eval(tag(\"name\")); text-color:#2E2D2B; font-size:12; font-stroke-width:1pt; font-stroke-color:#FFFFFFEE;}"
                + "node[count>=2]{icon-image:\"uni1\"; text:eval(tag(\"count\"));}"
                + "node[count>=4]{icon-image:\"uni2\";}"
                + "node[count>=8]{icon-image:\"uni3\";}"
                + "node[count>=16]{icon-image:\"uni4\";}"
                + "node[count>=32]{icon-image:\"uni5\";}"
                + "node[count>=64]{icon-image:\"uni6\";}"
                + "node[count>=128]{icon-image:\"uni7\";}");

    new AsyncTask<Void, Void, GLMapMarkerLayer>() {
      private GLMapBBox bbox;
      @Override
      protected GLMapMarkerLayer doInBackground(Void... voids) {
        GLMapMarkerLayer rv;
        try {
          Log.w("GLMapView", "Start parsing");
          GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(getAssets().open("cluster_data.json"));
          Log.w("GLMapView", "Finish parsing");

          bbox = objects.getBBox();

          Log.w("GLMapView", "Start creating layer");
          rv = new GLMapMarkerLayer(objects, style, styleCollection, 35, 3);
          Log.w("GLMapView", "Finish creating layer");
          objects.dispose();
        } catch (Exception e) {
          rv = null;
        }
        return rv;
      }

      @Override
      protected void onPostExecute(GLMapMarkerLayer layer) {
        if (layer != null) {
          markerLayer = layer;
          mapView.add(layer);
          mapView.setMapCenter(bbox.center());
          mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
        }
      }
    }.execute();
  }

  static class MarkersStyle extends GLMapMarkerStyleCollectionDataCallback {
    static int unionCounts[] = {1, 2, 4, 8, 16, 32, 64, 128};
    GLMapVectorStyle textStyle =
        GLMapVectorStyle.createStyle(
            "{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:#FFFFFFEE;}");

    @Override
    public MapPoint getLocation(Object marker) {
      if (marker instanceof MapPoint) {
        return (MapPoint) marker;
      } else if (marker instanceof GLMapVectorObject) {
        return ((GLMapVectorObject) marker).point();
      }
      return new MapPoint(0, 0);
    }

    @Override
    public void fillUnionData(int markersCount, long nativeMarker) {
      for (int i = unionCounts.length - 1; i >= 0; i--) {
        if (markersCount > unionCounts[i]) {
          GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, i);
          break;
        }
      }
      GLMapMarkerStyleCollection.setMarkerText(
          nativeMarker, Integer.toString(markersCount), new Point(0, 0), textStyle);
    }

    @Override
    public void fillData(Object marker, long nativeMarker) {
      if (marker instanceof MapPoint) {
        GLMapMarkerStyleCollection.setMarkerText(nativeMarker, "Test", new Point(0, 0), textStyle);
      } else if (marker instanceof GLMapVectorObject) {
        String name = ((GLMapVectorObject) marker).valueForKey("name");
        if (name != null) {
          GLMapMarkerStyleCollection.setMarkerText(
              nativeMarker, name, new Point(0, 15 / 2), textStyle);
        }
      }
      GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0);
    }
  }

  void addMarkers() {
    new AsyncTask<Void, Void, GLMapMarkerLayer>() {
      private GLMapBBox bbox;
      @Override
      protected GLMapMarkerLayer doInBackground(Void... voids) {
        GLMapMarkerLayer rv;
        try {
          GLMapMarkerStyleCollection style = new GLMapMarkerStyleCollection();
          for (int i = 0; i < MarkersStyle.unionCounts.length; i++) {
            float scale = (float) (0.2 + 0.1 * i);
            style.addStyle(
                new GLMapMarkerImage(
                    "marker" + scale,
                    mapView.imageManager.open("cluster.svgpb", scale, unionColours[i])));
          }
          style.setDataCallback(new MarkersStyle());

          Log.w("GLMapView", "Start parsing");
          GLMapVectorObjectList objects =
              GLMapVectorObject.createFromGeoJSONStreamOrThrow(getAssets().open("cluster_data.json"));
          Log.w("GLMapView", "Finish parsing");

          bbox = objects.getBBox();

          Log.w("GLMapView", "Start creating layer");
          rv = new GLMapMarkerLayer(objects.toArray(), style, 35, 3);
          Log.w("GLMapView", "Finish creating layer");
          objects.dispose();
        } catch (Exception e) {
          rv = null;
        }
        return rv;
      }

      @Override
      protected void onPostExecute(GLMapMarkerLayer layer) {
        if (layer != null) {
          markerLayer = layer;
          mapView.add(layer);
          mapView.setMapCenter(bbox.center());
          mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
        }
      }
    }.execute();
  }

  void addImage(final Button btn) {
    Bitmap bmp = mapView.imageManager.open("arrow-maphint.svgpb", 1, 0);
    image = new GLMapDrawable(bmp, 2);
    image.setOffset(bmp.getWidth(), bmp.getHeight() / 2);
    image.setRotatesWithMap(true);
    image.setAngle((float) Math.random() * 360);
    image.setPosition(mapView.getMapCenter());
    mapView.add(image);

    btn.setText("Move image");
    btn.setOnClickListener(v -> moveImage(btn));
  }

  void moveImage(final Button btn) {
    image.setPosition(mapView.getMapCenter());
    btn.setText("Remove image");
    btn.setOnClickListener(v -> delImage(btn));
  }

  void delImage(final Button btn) {
    if (image != null) {
      mapView.remove(image);
      image.dispose();
      image = null;
    }
    btn.setText("Add image");
    btn.setOnClickListener(v -> addImage(btn));
  }

  void addMultiline() {
    MapPoint[] line1 = new MapPoint[5];
    line1[0] = MapPoint.CreateFromGeoCoordinates(53.8869, 27.7151); // Minsk
    line1[1] = MapPoint.CreateFromGeoCoordinates(50.4339, 30.5186); // Kiev
    line1[2] = MapPoint.CreateFromGeoCoordinates(52.2251, 21.0103); // Warsaw
    line1[3] = MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102); // Berlin
    line1[4] = MapPoint.CreateFromGeoCoordinates(48.8505, 2.3343); // Paris

    MapPoint[] line2 = new MapPoint[3];
    line2[0] = MapPoint.CreateFromGeoCoordinates(52.3690, 4.9021); // Amsterdam
    line2[1] = MapPoint.CreateFromGeoCoordinates(50.8263, 4.3458); // Brussel
    line2[2] = MapPoint.CreateFromGeoCoordinates(49.6072, 6.1296); // Luxembourg

    MapPoint[][] multiline = {line1, line2};
    final GLMapVectorObject obj = GLMapVectorObject.createMultiline(multiline);
    // style applied to all lines added. Style is string with mapcss rules. Read more in manual.

    GLMapDrawable drawable = new GLMapDrawable();
    drawable.setVectorObject(obj, GLMapVectorCascadeStyle.createStyle("line{width: 2pt;color:green;layer:100;}"), null);
    mapView.add(drawable);
  }

  void addPolygon() {
    int pointCount = 25;
    MapGeoPoint[] outerRing = new MapGeoPoint[pointCount];
    MapGeoPoint[] innerRing = new MapGeoPoint[pointCount];

    float rOuter = 10, rInner = 5;
    MapGeoPoint centerPoint = new MapGeoPoint(53, 27);

    // let's display circle
    for (int i = 0; i < pointCount; i++) {
      outerRing[i] =
          new MapGeoPoint(
              centerPoint.lat + Math.sin(2 * Math.PI / pointCount * i) * rOuter,
              centerPoint.lon + Math.cos(2 * Math.PI / pointCount * i) * rOuter);

      innerRing[i] =
          new MapGeoPoint(
                  centerPoint.lat + Math.sin(2 * Math.PI / pointCount * i) * rInner,
                  centerPoint.lon + Math.cos(2 * Math.PI / pointCount * i) * rInner);
    }

    MapGeoPoint[][] outerRings = {outerRing};
    MapGeoPoint[][] innerRings = {innerRing};

    GLMapVectorObject obj = GLMapVectorObject.createPolygonGeo(outerRings, innerRings);
    GLMapDrawable drawable = new GLMapDrawable();
    drawable.setVectorObject(obj,
            GLMapVectorCascadeStyle.createStyle("area{fill-color:#10106050; fill-color:#10106050; width:4pt; color:green;}"),
            null); // #RRGGBBAA format
    mapView.add(drawable);

    mapView.setMapGeoCenter(centerPoint);
  }

  private void bulkDownload() {
    mapView.setMapCenter(MapPoint.CreateFromGeoCoordinates(53, 27));
    mapView.setMapZoom(12.5);

    final Button btn = this.findViewById(R.id.button_action);
    btn.setVisibility(View.VISIBLE);
    btn.setText("Download");
    btn.setOnClickListener(view -> {
      long[] allTiles = GLMapManager.VectorTilesAtBBox(mapView.getBBox());
      Log.i("BulkDownload", String.format("tilesCount = %d", allTiles.length));
      GLMapManager.CacheTiles(
          allTiles,
          new GLMapManager.TileDownloadProgress() {
            @Override
            public boolean onSuccess(long tile) {
              Log.i("BulkDownloadSuccess", String.format("tile = %d", tile));
              return true;
            }

            @Override
            public boolean onError(long tile, GLMapError errorCode) {
              Log.i(
                  "BulkDownloadError",
                  String.format(
                      "tile = %d, domain = %s, errorCode = %d",
                      tile, errorCode.getErrorDomain(), errorCode.getErrorCode()));
              return true;
            }
          });
    });
  }

  private void loadDarkTheme() {
    GLMapStyleParser parser = new GLMapStyleParser(getAssets(), "DefaultStyle.bundle");
    parser.setOptions(Collections.singletonMap("Theme", "Dark"), true);
    mapView.setStyle(parser.parseFromResources());
  }

  private void styleLiveReload() {
    final EditText editText = this.findViewById(R.id.edit_text);
    editText.setVisibility(View.VISIBLE);

    final Button btn = this.findViewById(R.id.button_action);
    btn.setVisibility(View.VISIBLE);
    btn.setText("Reload");
    btn.setOnClickListener(view -> new AsyncTask<String, String, byte[]>() {
      @Override
      protected byte[] doInBackground(String... strings) {
        byte[] rv;
        try {
          URLConnection connection = new URL(strings[0]).openConnection();
          connection.connect();
          InputStream inputStream = connection.getInputStream();

          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          int nRead;
          byte[] data = new byte[16384];
          while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
          }
          buffer.flush();
          rv = buffer.toByteArray();
          buffer.close();
          inputStream.close();
        } catch (Exception ignore) {
          rv = null;
        }
        return rv;
      }

      @Override
      protected void onPostExecute(final byte[] newStyleData) {
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
        mapView.setStyle(parser.parseFromResources());
      }
    }.execute(editText.getText().toString()));
  }

  int colorForTrack(float angle) {
    float[] hsv = new float[]{(float)(angle * 180.f / Math.PI) % 360, 1.f, 0.5f};

    return Color.HSVToColor(hsv);
  }

  private void recordTrack() {
    final float rStart = 10f;
    final float rDelta = (float) (Math.PI / 30);
    final float rDiff = 0.01f;
    final float clat = 30f, clon = 30f;

    // Create trackData with initial data
    trackPointIndex = 100;
    trackData =
        new GLMapTrackData((index, nativePoint) -> GLMapTrackData.setPointDataGeo(
            nativePoint,
            clat + Math.sin(rDelta * index) * (rStart - rDiff * index),
            clon + Math.cos(rDelta * index) * (rStart - rDiff * index),
            colorForTrack(rDelta * index)),
            trackPointIndex);

    track = new GLMapTrack(trackData, 2);
    // To use files from style, (e.g. track-arrow.svgpb) you should create DefaultStyle.bundle inside assets and put all additional resources inside.
    track.setStyle(GLMapVectorStyle.createStyle("{width: 7pt; fill-image:\"track-arrow.svgpb\";}"));

    mapView.add(track);
    mapView.setMapCenter(MapPoint.CreateFromGeoCoordinates(clat, clon));
    mapView.setMapZoom(4);

    if (handler == null) {
      handler = new Handler();
    }

    trackRecordRunnable = () -> {
      // Create new trackData with additional point
      GLMapTrackData newData =
          trackData.copyTrackAndAddGeoPoint(
              clat + Math.sin(rDelta * trackPointIndex) * (rStart - rDiff * trackPointIndex),
              clon + Math.cos(rDelta * trackPointIndex) * (rStart - rDiff * trackPointIndex),
              colorForTrack(rDelta * trackPointIndex),
              false);
      // Set data to track
      track.setData(newData);

      trackData.dispose(); // Release native data before GC will occur
      trackData = newData;

      trackPointIndex++;
      handler.postDelayed(trackRecordRunnable, 1000);
    };
    // Let's one more point every second.
    handler.postDelayed(trackRecordRunnable, 1000);
  }

  private void zoomToObjects(GLMapVectorObjectList objects) {
    // Zoom to bbox
    GLMapBBox bbox = objects.getBBox();
    mapView.doWhenSurfaceCreated(() -> {
      mapView.setMapCenter(bbox.center());
      mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()));
    });
  }

  private void loadGeoJSONPostcode()
  {
    GLMapVectorObjectList objects = null;
    try
    {
      objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(getAssets().open("uk_postcodes.geojson"));
    } catch (Exception e)
    {
      e.printStackTrace();
    }
    if (objects!=null)
    {
      GLMapVectorCascadeStyle style = GLMapVectorCascadeStyle.createStyle("area{fill-color:green; width:1pt; color:red;}");
      GLMapDrawable drawable = new GLMapDrawable();
      drawable.setVectorObjects(objects, style, null);
      mapView.add(drawable);
      zoomToObjects(objects);
    }
  }

  private void loadGeoJSONWithCSSStyle()
  {
    GLMapVectorObjectList objects;
    try {
      objects = GLMapVectorObject.createFromGeoJSONOrThrow(
              "[{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [30.5186, 50.4339]}, \"properties\": {\"id\": \"1\", \"text\": \"test1\"}},"
                      + "{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [27.7151, 53.8869]}, \"properties\": {\"id\": \"2\", \"text\": \"test2\"}},"
                      + "{\"type\":\"LineString\",\"coordinates\": [ [27.7151, 53.8869], [30.5186, 50.4339], [21.0103, 52.2251], [13.4102, 52.5037], [2.3343, 48.8505]]},"
                      + "{\"type\":\"Polygon\",\"coordinates\":[[ [0.0, 10.0], [10.0, 10.0], [10.0, 20.0], [0.0, 20.0] ],[ [2.0, 12.0], [ 8.0, 12.0], [ 8.0, 18.0], [2.0, 18.0] ]]}]");
    }catch (Exception e) {
      objects = null;
      e.printStackTrace();
    }

    GLMapVectorCascadeStyle style =
        GLMapVectorCascadeStyle.createStyle(
            "node[id=1]{icon-image:\"bus.svgpb\";icon-scale:0.5;icon-tint:green;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
                + "node|z-9[id=2]{icon-image:\"bus.svgpb\";icon-scale:0.7;icon-tint:blue;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
                + "line{linecap: round; width: 5pt; color:blue;}"
                + "area{fill-color:green; width:1pt; color:red;}");

    if(objects != null)
    {
      GLMapDrawable drawable = new GLMapDrawable();
      drawable.setVectorObjects(objects, style, null);
      mapView.add(drawable);
      zoomToObjects(objects);
    }
  }


  private void loadGeoJSON()
  {
    //loadGeoJSONWithCSSStyle();
    loadGeoJSONPostcode();
  }

  void captureScreen() {
    GLMapView mapView = this.findViewById(R.id.map_view);
    mapView.captureFrameWhenFinish(this);
  }

  @Override
  public void screenCaptured(final Bitmap bmp) {
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