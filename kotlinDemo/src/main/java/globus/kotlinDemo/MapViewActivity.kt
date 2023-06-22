package globus.kotlinDemo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import globus.glmap.*
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import globus.glsearch.GLSearch
import globus.glsearch.GLSearchCategories
import globus.glsearch.GLSearchFilter
import globus.javaDemo.DisplayImageActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
open class MapViewActivity : Activity(), GLMapViewRenderer.ScreenCaptureCallback, GLMapManager.StateListener {

    private data class Pin(
        val pos: MapPoint,
        val imageVariant: Int
    )

    private class Pins(private val renderer: GLMapViewRenderer) : GLMapImageGroupCallback {
        private val lock = ReentrantLock()
        private val images = arrayOfNulls<Bitmap>(3)
        private val pins = mutableListOf<Pin>()

        init {
            val screenScale = renderer.screenScale
            val assets = renderer.attachedView.context.assets
            images[0] = SVGRender.render(assets, "1.svg", SVGRender.transform(screenScale.toDouble(), 0xFFFF0000.toInt()))
            images[1] = SVGRender.render(assets, "2.svg", SVGRender.transform(screenScale.toDouble(), 0xFF00FF00.toInt()))
            images[2] = SVGRender.render(assets, "3.svg", SVGRender.transform(screenScale.toDouble(), 0xFF0000FF.toInt()))
        }

        override fun getImagesCount() = pins.size

        override fun getImageIndex(i: Int) = pins[i].imageVariant

        override fun getImagePos(i: Int) = pins[i].pos

        override fun updateStarted() {
            Log.i("GLMapImageGroupCallback", "Update started")
            lock.lock()
        }

        override fun updateFinished() {
            Log.i("GLMapImageGroupCallback", "Update finished")
            lock.unlock()
        }

        override fun getImageVariantsCount() = images.size

        override fun getImageVariantBitmap(i: Int) = images[i]

        override fun getImageVariantOffset(i: Int) = MapPoint((images[i]?.width ?: 0) / 2.0, 0.0)

        fun size() = lock.withLock { pins.size }

        fun add(pin: Pin) = lock.withLock { pins.add(pin) }

        fun remove(pin: Pin) = lock.withLock { pins.remove(pin) }

        fun findPin(renderer: GLMapViewRenderer, touchX: Float, touchY: Float): Pin? {
            return lock.withLock {
                pins.find { pin ->
                    val screenPos = renderer.convertInternalToDisplay(pin.pos)
                    val rt = Rect(-40, -40, 40, 40).apply { offset(screenPos.x.toInt(), screenPos.y.toInt()) }
                    rt.contains(touchX.toInt(), touchY.toInt())
                }
            }
        }
    }

    private val localeSettings = GLMapLocaleSettings()
    private var image: GLMapImage? = null
    private var imageGroup: GLMapImageGroup? = null
    private val pins: Pins by lazy { Pins(mapView.renderer) }
    private var mapToDownload: GLMapInfo? = null
    private var markerLayer: GLMapMarkerLayer? = null
    private var curLocationHelper: CurLocationHelper? = null
    private var trackPointIndex = 0
    private var track: GLMapTrack? = null
    private val trackStyle = GLMapVectorStyle.createStyle("{width: 7pt; fill-image:\"track-arrow.svgpb\";}")

    private var trackData: GLMapTrackData? = null
    private var trackRecordRunnable: Runnable? = null
    lateinit var mapView: GLMapView

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    inline val renderer: GLMapViewRenderer
        get() = mapView.renderer

    private inline val btnDownloadMap: Button
        get() = findViewById(R.id.button_dl_map)

    private inline val actionButton: Button
        get() = findViewById(R.id.button_action)

    open fun getLayoutID() = R.layout.map

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutID())
        mapView = findViewById(R.id.map_view)

        // Map list is updated, because download button depends on available map list and during first
        // launch this list is empty
        GLMapManager.UpdateMapList(null)
        btnDownloadMap.setOnClickListener {
            val mapToDownload = mapToDownload
            if (mapToDownload != null) {
                val tasks = GLMapManager.getDownloadTasks(mapToDownload.mapID, GLMapInfo.DataSetMask.ALL)
                if (!tasks.isNullOrEmpty()) {
                    for (task in tasks) task.cancel()
                } else {
                    GLMapManager.DownloadDataSets(mapToDownload, GLMapInfo.DataSetMask.ALL)
                }
                updateMapDownloadButtonText()
            } else {
                val i = Intent(it.context, DownloadActivity::class.java)
                val pt = renderer.mapCenter
                i.putExtra("cx", pt.x)
                i.putExtra("cy", pt.y)
                startActivity(i)
            }
        }
        GLMapManager.addStateListener(this)
        renderer.localeSettings = localeSettings
        renderer.setStyle(GLMapStyleParser(assets, "DefaultStyle.bundle").parseFromResources()!!)
        checkAndRequestLocationPermission()

        val ruler = GLMapScaleRuler(Int.MAX_VALUE)
        ruler.setPlacement(GLMapViewRenderer.GLMapPlacement.BottomCenter, 10, 10, 200.0)
        renderer.add(ruler)
        renderer.setAttributionPosition(GLMapViewRenderer.GLMapPlacement.TopCenter)

        renderer.setCenterTileStateChangedCallback { updateMapDownloadButton() }
        renderer.setMapDidMoveCallback { updateMapDownloadButtonText() }
        run(Samples.values()[intent.extras?.getInt("example") ?: 0])
    }

    protected open fun run(test: Samples) {
        when (test) {
            Samples.MAP, Samples.OPEN_ROUTING, Samples.MAP_TEXTURE_VIEW, Samples.SVG_TEST -> {
            }
            Samples.CALLBACK_TEST, Samples.DOWNLOAD_MAP -> {
            }
            Samples.DARK_THEME -> loadDarkTheme()
            Samples.MAP_EMBEDDED -> showEmbedded()
            Samples.MAP_ONLINE -> GLMapManager.SetTileDownloadingAllowed(true)
            Samples.MAP_ONLINE_RASTER -> renderer.setBase(OSMTileSource(this))
            Samples.ZOOM_BBOX -> zoomToBBox()
            Samples.FLY_TO -> {
                renderer.mapCenter = MapPoint.CreateFromGeoCoordinates(37.3257, -122.0353)
                renderer.mapZoom = 14.0
                actionButton.visibility = View.VISIBLE
                actionButton.text = "Fly"
                actionButton.setOnClickListener {
                    val minLat = 33.0
                    val maxLat = 48.0
                    val minLon = -118.0
                    val maxLon = -85.0
                    val lat = minLat + (maxLat - minLat) * Math.random()
                    val lon = minLon + (maxLon - minLon) * Math.random()
                    val point = MapPoint.CreateFromGeoCoordinates(lat, lon)
                    renderer.animate {
                        renderer.mapZoom = 15.0
                        it.flyToPoint(point)
                    }
                }
                GLMapManager.SetTileDownloadingAllowed(true)
            }
            Samples.OFFLINE_SEARCH -> {
                GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, null, "Montenegro.vm", assets, null)
                zoomToPoint()
                offlineSearch()
            }
            Samples.MARKERS -> {
                mapView.isLongClickable = true
                val gestureDetector = GestureDetector(
                    this,
                    object : SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            deleteMarker(e.x.toDouble(), e.y.toDouble())
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            addMarker(e.x.toDouble(), e.y.toDouble())
                        }
                    }
                )
                mapView.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
                addMarkers()
                GLMapManager.SetTileDownloadingAllowed(true)
            }
            Samples.MARKERS_MAPCSS -> {
                addMarkersWithMapcss()
                val gestureDetector = GestureDetector(
                    this,
                    object : SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            deleteMarker(e.x.toDouble(), e.y.toDouble())
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            addMarkerAsVectorObject(e.x.toDouble(), e.y.toDouble())
                        }
                    }
                )
                mapView.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
                GLMapManager.SetTileDownloadingAllowed(true)
            }
            Samples.MULTILINE -> addMultiline()
            Samples.POLYGON -> addPolygon()
            Samples.CAPTURE_SCREEN -> {
                zoomToPoint()
                captureScreen()
            }
            Samples.IMAGE_SINGLE -> {
                actionButton.visibility = View.VISIBLE
                delImage()
            }
            Samples.IMAGE_MULTI -> {
                mapView.isLongClickable = true
                val gestureDetector = GestureDetector(
                    this,
                    object : SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            deletePin(e.x, e.y)
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            addPin(e.x, e.y)
                        }
                    }
                )
                mapView.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
            }
            Samples.GEO_JSON -> loadGeoJSON()
            Samples.DOWNLOAD_IN_BBOX -> downloadInBBox()
            Samples.STYLE_LIVE_RELOAD -> styleLiveReload()
            Samples.RECORD_TRACK -> recordTrack()
        }
        renderer.setMapDidMoveCallback {
            if (test == Samples.CALLBACK_TEST) {
                Log.w("GLMapView", "Did move")
            }
            updateMapDownloadButtonText()
        }
    }

    private fun checkAndRequestLocationPermission() {
        val app = application as DemoApp
        // Create helper if not exist
        var curLocationHelper = curLocationHelper
        if (curLocationHelper == null) {
            curLocationHelper = CurLocationHelper(renderer)
            this.curLocationHelper = curLocationHelper
            app.locationListeners.add(curLocationHelper)
        }

        // Try to start location updates. If we need permissions - ask for them
        if (!app.initLocationManager()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                0
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            0 -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                (application as DemoApp).initLocationManager()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroy() {
        GLMapManager.removeStateListener(this)
        markerLayer?.dispose()
        markerLayer = null

        imageGroup?.dispose()
        imageGroup = null

        val curLocationHelper = curLocationHelper
        if (curLocationHelper != null) {
            (application as DemoApp).locationListeners.remove(curLocationHelper)
            this.curLocationHelper = null
        }

        val trackRecordRunnable = trackRecordRunnable
        if (trackRecordRunnable != null) {
            this.trackRecordRunnable = null
            handler.removeCallbacks(trackRecordRunnable)
        }
        mapView.dispose()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        renderer.animate { renderer.mapZoom = renderer.mapZoom - 1 }
        return false
    }

    override fun onStartDownloading(task: GLMapDownloadTask) {}
    override fun onDownloadProgress(task: GLMapDownloadTask) {
        updateMapDownloadButtonText()
    }

    override fun onFinishDownloading(task: GLMapDownloadTask) {
        renderer.reloadTiles()
    }

    override fun onStateChanged(map: GLMapInfo, @GLMapInfo.DataSet dataSet: Int) {
        updateMapDownloadButtonText()
    }

    private fun updateMapDownloadButtonText() {
        if (btnDownloadMap.visibility == View.VISIBLE) {
            val center = renderer.mapCenter
            val maps = GLMapManager.MapsAtPoint(center)
            mapToDownload = if (maps.isNullOrEmpty()) null else maps[0]
            val mapToDownload = mapToDownload
            if (mapToDownload != null) {
                var total: Long = 0
                var downloaded: Long = 0
                val tasks = GLMapManager.getDownloadTasks(mapToDownload.mapID, GLMapInfo.DataSetMask.ALL)
                if (!tasks.isNullOrEmpty()) {
                    for (task in tasks) {
                        total += task.total.toLong()
                        downloaded += task.downloaded.toLong()
                    }
                }
                val text = if (total != 0L) {
                    val progress = downloaded * 100 / total
                    String.format(
                        Locale.getDefault(),
                        "Downloading %s %d%%",
                        mapToDownload.getLocalizedName(localeSettings),
                        progress
                    )
                } else {
                    String.format(
                        Locale.getDefault(),
                        "Download %s",
                        mapToDownload.getLocalizedName(localeSettings)
                    )
                }
                btnDownloadMap.text = text
            } else {
                btnDownloadMap.text = "Download maps"
            }
        }
    }

    private fun showEmbedded() {
        GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, null, "Montenegro.vm", assets, null)
        zoomToPoint()
    }

    private fun offlineSearch() {
        // You should initialize GLSearch before use, to let it load ICU collation tables and categories.
        GLSearch.Initialize(this)
        val searchOffline = GLSearch()
        searchOffline.setCenter(MapPoint.CreateFromGeoCoordinates(42.4341, 19.26)) // Set center of search
        searchOffline.setLimit(20) // Set maximum number of results. By default is is 100
        searchOffline.setLocaleSettings(renderer.localeSettings) // If locale matches result will have bonus score
        val localeEn = GLMapLocaleSettings(arrayOf("en", "native"), GLMapLocaleSettings.UnitSystem.International)
        val category = GLSearchCategories.getShared().getStartedWith(arrayOf("restaurant"), localeEn) // find category
        if (category.isNullOrEmpty()) return

        // Logical operations between filters is AND
        //
        // Let's find all restaurants
        searchOffline.addFilter(GLSearchFilter.createWithCategory(category[0])) // Filter results by category

        // Additionally search for objects with
        // word beginning "Baj" in name or alt_name,
        // "Crno" as word beginning in addr:* tags,
        // and exact "60/1" in addr:* tags.
        //
        // Expected result is restaurant Bajka at Bulevar Ivana Crnojevića 60/1
        // (https://www.openstreetmap.org/node/4397752292 )
        searchOffline.addFilter(
            GLSearchFilter.createWithQuery("Baj", GLSearch.TagSetMask.NAME or GLSearch.TagSetMask.ALT_NAME)
        )
        searchOffline.addFilter(GLSearchFilter.createWithQuery("Crno", GLSearch.TagSetMask.ADDRESS))
        val filter = GLSearchFilter.createWithQuery("60/1", GLSearch.TagSetMask.ADDRESS)
        // Default match type is WordStart. But we could change it to Exact or Word.
        filter.setMatchType(GLSearch.MatchType.EXACT)
        searchOffline.addFilter(filter)
        searchOffline.searchAsync { runOnUiThread { displaySearchResults(it?.toArray()) } }
    }

    internal class SearchStyle : GLMapMarkerStyleCollectionDataCallback() {
        override fun getLocation(marker: Any): MapPoint {
            return if (marker is GLMapVectorObject) marker.point() else MapPoint(0.0, 0.0)
        }

        override fun fillUnionData(markersCount: Int, nativeMarker: Long) {
            // Not called if clustering is off
        }

        override fun fillData(marker: Any, nativeMarker: Long) {
            GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)
        }
    }

    private fun displaySearchResults(objects: Array<GLMapVectorObject>?) {
        val style = GLMapMarkerStyleCollection()

        val transform = SVGRender.transform((0.2f * renderer.screenScale).toDouble(), Color.argb(0xFF, 0, 0, 0xFF))
        val markerImage = SVGRender.render(assets, "cluster.svg", transform)
        if (markerImage != null) {
            style.addStyle(GLMapMarkerImage("marker", markerImage))
            style.setDataCallback(SearchStyle())
        }
        val markerLayer = GLMapMarkerLayer(objects, style, 0.0, 4)
        this.markerLayer = markerLayer
        renderer.add(markerLayer)

        // Zoom to results
        if (!objects.isNullOrEmpty()) {
            // Calculate bbox
            val bbox = GLMapBBox()
            for (obj in objects) {
                bbox.addPoint(obj.point())
            }
            // Zoom to bbox
            renderer.mapCenter = bbox.center()
            renderer.mapZoom = renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight)
        }
    }

    // Example how to calculate zoom level for some bbox
    private fun zoomToBBox(bbox: GLMapBBox) {
        renderer.doWhenSurfaceCreated {
            renderer.mapCenter = bbox.center()
            renderer.mapZoom = renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight)
        }
    }

    private fun zoomToBBox() {
        // When surface will be created - getWidth and getHeight will have valid values
        val bbox = GLMapBBox()
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102)) // Berlin
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.9024, 27.5618)) // Minsk
        zoomToBBox(bbox = bbox)
    }

    private fun zoomToPoint() {
        // New York
        // MapPoint pt = new MapPoint(-74.0059700 , 40.7142700	);

        // Belarus
        // MapPoint pt = new MapPoint(27.56, 53.9);
        // ;

        // Move map to the Montenegro capital
        val pt = MapPoint.CreateFromGeoCoordinates(42.4341, 19.26)
        renderer.mapCenter = pt
        renderer.mapZoom = 16.0
    }

    private fun addPin(touchX: Float, touchY: Float) {
        var imageGroup = imageGroup
        if (imageGroup == null) {
            imageGroup = GLMapImageGroup(pins, 3)
            this.imageGroup = imageGroup
            renderer.add(imageGroup)
        }
        val pt = renderer.convertDisplayToInternal(MapPoint(touchX.toDouble(), touchY.toDouble()))
        val pin = Pin(pt, pins.size() % 3)
        pins.add(pin)
        imageGroup.setNeedsUpdate(false)
    }

    private fun deletePin(touchX: Float, touchY: Float) {
        val pin = pins.findPin(renderer, touchX, touchY)
        if (pin != null) {
            pins.remove(pin)
            imageGroup?.setNeedsUpdate(false)
        }
    }

    private fun deleteMarker(x: Double, y: Double) {
        val pt = renderer.convertDisplayToInternal(MapPoint(x, y))
        val markersToRemove = markerLayer?.objectsNearPoint(renderer, pt, 30.0)
        if (!markersToRemove.isNullOrEmpty()) {
            markerLayer?.modify(null, setOf(markersToRemove[0]), true) {
                Log.d("MarkerLayer", "Marker deleted")
            }
        }
    }

    private fun addMarker(x: Double, y: Double) {
        val newMarkers = arrayOfNulls<MapPoint>(1)
        newMarkers[0] = renderer.convertDisplayToInternal(MapPoint(x, y))
        markerLayer?.modify(newMarkers, null, true) {
            Log.d("MarkerLayer", "Marker added")
        }
    }

    fun addMarkerAsVectorObject(x: Double, y: Double) {
        val newMarkers = arrayOf(GLMapVectorObject.createPoint(renderer.convertDisplayToInternal(MapPoint(x, y))))
        markerLayer?.modify(newMarkers, null, true) {
            Log.d("MarkerLayer", "Marker added")
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun addMarkersWithMapcss() {
        val styleCollection = GLMapMarkerStyleCollection()
        for (i in unionColours.indices) {
            val scale = (0.2 + 0.1 * i).toFloat()
            val transform = SVGRender.transform((renderer.screenScale * scale).toDouble(), unionColours[i])
            val img = SVGRender.render(assets, "cluster.svg", transform)
            if (img != null) {
                val index = styleCollection.addStyle(GLMapMarkerImage("marker$scale", img))
                styleCollection.setStyleName(i, "uni$index")
            }
        }
        val style = GLMapVectorCascadeStyle.createStyle(
            """
node {
    icon-image:"uni0";
    text-priority: 100;
    text:eval(tag("name"));
    text-color:#2E2D2B;
    font-size:12;
    font-stroke-width:1pt;
    font-stroke-color:#FFFFFFEE;
}
node[count>=2]{
    icon-image:"uni1";
    text-priority: 101;
    text:eval(tag("count"));
}
node[count>=4]{
    icon-image:"uni2";
    text-priority: 102;
}
node[count>=8]{
    icon-image:"uni3";
    text-priority: 103;
}
node[count>=16]{
    icon-image:"uni4";
    text-priority: 104;
}
node[count>=32]{
    icon-image:"uni5";
    text-priority: 105;
}
node[count>=64]{
    icon-image:"uni6";
    text-priority: 106;
}
node[count>=128]{
    icon-image:"uni7";
    text-priority: 107;
}                  
            """
        )!!
        executor.execute {
            try {
                Log.w("GLMapView", "Start parsing")
                val objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(assets.open("cluster_data.json"))
                Log.w("GLMapView", "Finish parsing")
                Log.w("GLMapView", "Start creating layer")
                val layer = GLMapMarkerLayer(objects, style, styleCollection, 35.0, 3)
                val bbox = objects.bBox
                Log.w("GLMapView", "Finish creating layer")
                objects.dispose()
                handler.post {
                    markerLayer = layer
                    renderer.add(layer)
                    renderer.mapCenter = bbox.center()
                    renderer.mapZoom = renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight)
                }
            } catch (e: Exception) {
            }
        }
    }

    internal class MarkersStyle : GLMapMarkerStyleCollectionDataCallback() {
        private var textStyle: GLMapVectorStyle = GLMapVectorStyle.createStyle(
            "{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:#FFFFFFEE;}"
        )

        override fun getLocation(marker: Any) = when (marker) {
            is MapPoint -> marker
            is GLMapVectorObject -> marker.point()
            else -> MapPoint(0.0, 0.0)
        }

        override fun fillUnionData(markersCount: Int, nativeMarker: Long) {
            for (i in unionCounts.indices.reversed()) {
                if (markersCount > unionCounts[i]) {
                    GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, i)
                    break
                }
            }
            GLMapMarkerStyleCollection.setMarkerText(nativeMarker, markersCount.toString(), GLMapTextAlignment.Undefined, Point(0, 0), textStyle)
        }

        override fun fillData(marker: Any, nativeMarker: Long) {
            if (marker is MapPoint) {
                GLMapMarkerStyleCollection.setMarkerText(nativeMarker, "Test", GLMapTextAlignment.Undefined, Point(0, 0), textStyle)
            } else if (marker is GLMapVectorObject) {
                val name = marker.valueForKey("name")?.string
                if (name != null) {
                    GLMapMarkerStyleCollection.setMarkerText(nativeMarker, name, GLMapTextAlignment.Undefined, Point(0, 15 / 2), textStyle)
                }
            }
            GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)
        }

        companion object {
            var unionCounts = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun addMarkers() {
        executor.execute {
            try {
                val style = GLMapMarkerStyleCollection()
                for (i in MarkersStyle.unionCounts.indices) {
                    val scale = (0.2 + 0.1 * i).toFloat()
                    val transform = SVGRender.transform((renderer.screenScale * scale).toDouble(), unionColours[i])
                    val img = SVGRender.render(assets, "cluster.svg", transform)
                    if (img != null)
                        style.addStyle(GLMapMarkerImage("marker$scale", img))
                }
                style.setDataCallback(MarkersStyle())
                Log.w("GLMapView", "Start parsing")
                val objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(assets.open("cluster_data.json"))
                Log.w("GLMapView", "Finish parsing")
                Log.w("GLMapView", "Start creating layer")
                val layer = GLMapMarkerLayer(objects.toArray(), style, 35.0, 3)
                val bbox = objects.bBox
                Log.w("GLMapView", "Finish creating layer")
                objects.dispose()
                handler.post {
                    markerLayer = layer
                    renderer.add(layer)
                    renderer.mapCenter = bbox.center()
                    renderer.mapZoom = renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun addImage() {
        val bitmap = SVGRender.render(assets, "arrow-maphint.svg", null)

        val mapImage = GLMapImage(2)
        if (bitmap != null) {
            mapImage.setBitmap(bitmap)
            mapImage.setOffset(bitmap.width, bitmap.height / 2)
        }
        this.image = mapImage
        mapImage.isRotatesWithMap = true
        mapImage.angle = Math.random().toFloat() * 360
        mapImage.position = renderer.mapCenter
        renderer.add(mapImage)
        actionButton.text = "Move image"
        actionButton.setOnClickListener { moveImage() }
    }

    private fun moveImage() {
        image?.position = renderer.mapCenter
        actionButton.text = "Remove image"
        actionButton.setOnClickListener { delImage() }
    }

    private fun delImage() {
        val image = image
        if (image != null) {
            renderer.remove(image)
            image.dispose()
            this.image = null
        }
        actionButton.text = "Add image"
        actionButton.setOnClickListener { addImage() }
    }

    private fun addMultiline() {
        val line1 = arrayOf(
            MapPoint.CreateFromGeoCoordinates(53.8869, 27.7151), // Minsk
            MapPoint.CreateFromGeoCoordinates(50.4339, 30.5186), // Kiev
            MapPoint.CreateFromGeoCoordinates(52.2251, 21.0103), // Warsaw
            MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102), // Berlin
            MapPoint.CreateFromGeoCoordinates(48.8505, 2.3343) // Paris
        )

        val line2 = arrayOf(
            MapPoint.CreateFromGeoCoordinates(52.3690, 4.9021), // Amsterdam
            MapPoint.CreateFromGeoCoordinates(50.8263, 4.3458), // Brussel
            MapPoint.CreateFromGeoCoordinates(49.6072, 6.1296) // Luxembourg
        )
        val obj = GLMapVectorObject.createMultiline(arrayOf(line1, line2))
        // style applied to all lines added. Style is string with mapcss rules. Read more in manual.
        val drawable = GLMapVectorLayer()
        val style = GLMapVectorCascadeStyle.createStyle("line{width: 2pt;color:green;layer:100;}")!!
        drawable.setVectorObject(obj, style, null)
        renderer.add(drawable)
    }

    private fun addPolygon() {
        val pointCount = 25
        val outerRing = arrayOfNulls<MapGeoPoint>(pointCount)
        val innerRing = arrayOfNulls<MapGeoPoint>(pointCount)
        val rOuter = 10f
        val rInner = 5f
        val centerPoint = MapGeoPoint(53.0, 27.0)

        // let's display circle
        for (i in 0 until pointCount) {
            outerRing[i] = MapGeoPoint(
                centerPoint.lat + sin(2 * Math.PI / pointCount * i) * rOuter,
                centerPoint.lon + cos(2 * Math.PI / pointCount * i) * rOuter
            )
            innerRing[i] = MapGeoPoint(
                centerPoint.lat + sin(2 * Math.PI / pointCount * i) * rInner,
                centerPoint.lon + cos(2 * Math.PI / pointCount * i) * rInner
            )
        }
        val outerRings = arrayOf(outerRing)
        val innerRings = arrayOf(innerRing)
        val obj = GLMapVectorObject.createPolygonGeo(outerRings, innerRings)
        val drawable = GLMapVectorLayer()
        // #RRGGBBAA format
        val style = GLMapVectorCascadeStyle.createStyle(
            "area{fill-color:#10106050; fill-color:#10106050; width:4pt; color:green;}"
        )!!
        drawable.setVectorObject(obj, style, null)
        renderer.add(drawable)
        renderer.mapGeoCenter = centerPoint
    }

    private fun downloadInBBox() {
        val bbox = GLMapBBox()
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.0, 27.0))
        bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.5, 27.5))
        zoomToBBox(bbox = bbox)

        val cacheDir = cacheDir
        val mapPath = File(cacheDir, "test.vmtar")
        val navigationPath = File(cacheDir, "test.navtar")
        val elevationPath = File(cacheDir, "test.eletar")

        if (mapPath.exists()) {
            GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, bbox, mapPath.absolutePath, null, null)
        }
        if (navigationPath.exists()) {
            GLMapManager.AddDataSet(GLMapInfo.DataSet.NAVIGATION, bbox, navigationPath.absolutePath, null, null)
        }
        if (elevationPath.exists()) {
            GLMapManager.AddDataSet(GLMapInfo.DataSet.ELEVATION, bbox, elevationPath.absolutePath, null, null)
        }

        mapView.renderer.enableClipping(bbox, 9.0F, 16.0F)
        mapView.renderer.drawElevationLines = true
        mapView.renderer.drawHillshades = true
        mapView.renderer.reloadTiles()

        class ActionInfo(
            val title: String,
            @GLMapInfo.DataSet val dataSet: Int?,
            val file: File?
        )

        val action = when {
            !mapPath.exists() ->
                ActionInfo("Download map", GLMapInfo.DataSet.MAP, mapPath)
            !navigationPath.exists() ->
                ActionInfo("Download navigation", GLMapInfo.DataSet.NAVIGATION, navigationPath)
            !elevationPath.exists() ->
                ActionInfo("Download elevation", GLMapInfo.DataSet.ELEVATION, elevationPath)
            else ->
                ActionInfo("Calc route", null, null)
        }

        val btn = findViewById<Button>(R.id.button_action)
        btn.visibility = View.VISIBLE
        btn.text = action.title
        btn.setOnClickListener {
            if (action.file != null && action.dataSet != null) {
                GLMapManager.DownloadDataSet(
                    action.dataSet,
                    action.file.absolutePath,
                    bbox,
                    object : GLMapManager.DownloadCallback {
                        override fun onProgress(
                            totalSize: Long,
                            downloadedSize: Long,
                            downloadSpeed: Double
                        ) {
                            Log.i(
                                "BulkDownload",
                                String.format(
                                    "Download %d stats: %d, %f",
                                    action.dataSet,
                                    downloadedSize,
                                    downloadSpeed
                                )
                            )
                        }

                        override fun onFinished(error: GLMapError?) {
                            if (error == null) {
                                downloadInBBox()
                            } else {
                                Log.e("BulkDownload", error.message ?: "unknown")
                            }
                        }
                    }
                )
            } else {
                val request = GLRouteRequest()
                request.addPoint(GLRoutePoint(MapGeoPoint(53.2328, 27.2699), Double.NaN, true, true))
                request.addPoint(GLRoutePoint(MapGeoPoint(53.1533, 27.0909), Double.NaN, true, true))
                request.setAutoWithOptions(CostingOptions.Auto())
                request.locale = "en"
                request.setOfflineWithConfig(RoutingActivity.GetValhallaConfig(resources))

                request.start(object : GLRouteRequest.ResultsCallback {
                    override fun onResult(route: GLRoute) {
                        Log.i("Route", "Success")
                        val trackData = route.getTrackData(Color.RED)

                        var trk = track
                        if (trk == null) {
                            trk = GLMapTrack(5)
                            mapView.renderer.add(trk)
                        }
                        trk.setData(trackData, trackStyle, null)
                        track = trk
                    }

                    override fun onError(error: GLMapError) {
                        Log.i("Route", "Error: $error")
                    }
                })
            }
        }
    }

    private fun loadDarkTheme() {
        val parser = GLMapStyleParser(assets, "DefaultStyle.bundle")
        parser.setOptions(mapOf("Theme" to "Dark"), true)
        renderer.setStyle(parser.parseFromResources()!!)
    }

    private fun styleLiveReload() {
        val editText = findViewById<EditText>(R.id.edit_text)
        editText.visibility = View.VISIBLE
        actionButton.visibility = View.VISIBLE
        actionButton.text = "Reload"
        actionButton.setOnClickListener {
            val url = editText.text.toString()
            executor.execute {
                try {
                    val connection = URL(url).openConnection()
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val buffer = ByteArrayOutputStream()
                    var nRead: Int
                    val data = ByteArray(16384)
                    while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    buffer.flush()
                    val newStyleData = buffer.toByteArray()
                    buffer.close()
                    inputStream.close()
                    val parser = GLMapStyleParser { name: String ->
                        if (name == "Style.mapcss") {
                            newStyleData
                        } else {
                            var result: ByteArray?
                            try {
                                val stream = assets.open("DefaultStyle.bundle/$name")
                                result = ByteArray(stream.available())
                                if (stream.read(result) == result.size) {
                                    result = null
                                }
                                stream.close()
                            } catch (ignore: IOException) {
                                result = null
                            }
                            result
                        }
                    }
                    val style = parser.parseFromResources()
                    if (style != null) {
                        handler.post { renderer.setStyle(style) }
                    }
                } catch (ignore: Exception) {
                }
            }
        }
    }

    private fun colorForTrack(angle: Float): Int {
        val hsv = floatArrayOf((angle * 180f / Math.PI).toFloat() % 360, 1f, 0.5f)
        return Color.HSVToColor(hsv)
    }

    private fun recordTrack() {
        val rStart = 10f
        val rDelta = (Math.PI / 30).toFloat()
        val rDiff = 0.01f
        val clat = 30f
        val clon = 30f

        // Create trackData with initial data
        trackPointIndex = 100
        val trackData = GLMapTrackData(
            { index, nativePoint ->
                GLMapTrackData.setPointDataGeo(
                    nativePoint,
                    clat + sin(rDelta * index.toDouble()) * (rStart - rDiff * index),
                    clon + cos(rDelta * index.toDouble()) * (rStart - rDiff * index),
                    colorForTrack(rDelta * index)
                )
            },
            trackPointIndex
        )
        this.trackData = trackData

        var trk = track
        if (trk == null) {
            trk = GLMapTrack(2)
            renderer.add(trk)
        }
        trk.setData(trackData, trackStyle, null)
        this.track = trk
        renderer.mapCenter = MapPoint.CreateFromGeoCoordinates(clat.toDouble(), clon.toDouble())
        renderer.mapZoom = 4.0
        val trackRecordRunnable = Runnable {
            // Create new trackData with additional point
            val newData = this.trackData?.copyTrackAndAddGeoPoint(
                clat + sin(rDelta * trackPointIndex.toDouble()) * (rStart - rDiff * trackPointIndex),
                clon + cos(rDelta * trackPointIndex.toDouble()) * (rStart - rDiff * trackPointIndex),
                colorForTrack(rDelta * trackPointIndex),
                false
            ) ?: return@Runnable
            // Set data to track
            track?.setData(newData, trackStyle, null)
            this.trackData?.dispose() // Release native data before GC will occur
            this.trackData = newData
            trackPointIndex++
            val trackRecordRunnable = trackRecordRunnable ?: return@Runnable
            handler.postDelayed(trackRecordRunnable, 1000)
        }
        this.trackRecordRunnable = trackRecordRunnable
        // Let's one more point every second.
        handler.postDelayed(trackRecordRunnable, 1000)
    }

    private fun zoomToObjects(objects: GLMapVectorObjectList) {
        // Zoom to bbox
        val bbox = objects.bBox
        renderer.doWhenSurfaceCreated {
            renderer.mapCenter = bbox.center()
            renderer.mapZoom = renderer.mapZoomForBBox(bbox, renderer.surfaceWidth, renderer.surfaceHeight)
        }
    }

    private fun loadGeoJSONPostcode() {
        try {
            val objects = GLMapVectorObject.createFromGeoJSONStreamOrThrow(assets.open("uk_postcodes.geojson"))
            val style = GLMapVectorCascadeStyle.createStyle("area{fill-color:green; width:1pt; color:red;}")!!
            val drawable = GLMapVectorLayer()
            drawable.setVectorObjects(objects, style, null)
            renderer.add(drawable)
            zoomToObjects(objects)

            val gestureDetector = GestureDetector(
                this,
                object : SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        for (index in 0 until objects.size()) {
                            val obj = objects[index]
                            val mapPoint = renderer.convertDisplayToInternal(MapPoint(e.x.toDouble(), e.y.toDouble()))
                            // When checking polygons it will check if point is inside polygon.
                            // For lines and points it will check if distance is less then maxDistance.
                            if (obj.findNearestPoint(renderer, mapPoint, 10.0) != null) {
                                val message = "Tapped object: ${obj.asGeoJSON()}"
                                Toast.makeText(this@MapViewActivity, message, Toast.LENGTH_LONG).show()
                            }
                        }
                        return true
                    }
                    override fun onLongPress(e: MotionEvent) { }
                }
            )
            mapView.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadGeoJSONWithCSSStyle() {
        val objects = GLMapVectorObject.createFromGeoJSONOrThrow(
            """
[{"type": "Feature", "geometry": {"type": "Point", "coordinates": [30.5186, 50.4339]}, "properties": {"id": "1", "text": "test1"}},
{"type": "Feature", "geometry": {"type": "Point", "coordinates": [27.7151, 53.8869]}, "properties": {"id": "2", "text": "test2"}},
{"type":"LineString", "coordinates": [ [27.7151, 53.8869], [30.5186, 50.4339], [21.0103, 52.2251], [13.4102, 52.5037], [2.3343, 48.8505]]},
{"type":"Polygon", "coordinates":[[ [0.0, 10.0], [10.0, 10.0], [10.0, 20.0], [0.0, 20.0] ],[ [2.0, 12.0], [ 8.0, 12.0], [ 8.0, 18.0], [2.0, 18.0] ]]}]            
            """
        )
        val style = GLMapVectorCascadeStyle.createStyle(
            """
node[id=1] {
    icon-image:"bus.svg";
    icon-scale:0.5;
    icon-tint:green;
    text:eval(tag('text'));
    text-color:red;
    font-size:12;
    // add priority to this text over map objects
    text-priority: 20;
}
node|z-9[id=2] {
    icon-image:"bus.svg";
    icon-scale:0.7;
    icon-tint:blue;
    text:eval(tag('text'));
    text-color:red;
    font-size:12;
    // add priority to this text over map objects
    text-priority: 20;
}
line {
    linecap: round;
    width: 5pt;
    color:blue;
}
area {
    fill-color:green;
    width:1pt;
    color:red;
}            
            """
        )!!
        if (objects != null) {
            val drawable = GLMapVectorLayer()
            drawable.setVectorObjects(objects, style, null)
            renderer.add(drawable)
            zoomToObjects(objects)
        }
    }

    private fun loadGeoJSON() {
        // loadGeoJSONWithCSSStyle();
        loadGeoJSONPostcode()
    }

    private fun captureScreen() {
        renderer.captureFrameWhenFinish(this)
    }

    override fun screenCaptured(bmp: Bitmap?) {
        if (bmp == null) {
            return
        }
        runOnUiThread {
            val bytes = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            try {
                var fo = openFileOutput("screenCapture", Context.MODE_PRIVATE)
                fo.write(bytes.toByteArray())
                fo.close()
                val file = File(getExternalFilesDir(null), "Test.jpg")
                fo = FileOutputStream(file)
                fo.write(bytes.toByteArray())
                fo.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val intent = Intent(this@MapViewActivity, DisplayImageActivity::class.java)
            val b = Bundle()
            b.putString("imageName", "screenCapture")
            intent.putExtras(b)
            startActivity(intent)
        }
    }

    private fun updateMapDownloadButton() {
        when (renderer.centerTileState) {
            GLMapViewRenderer.GLMapTileState.NoData -> {
                if (btnDownloadMap.visibility == View.INVISIBLE) {
                    btnDownloadMap.visibility = View.VISIBLE
                    btnDownloadMap.parent.requestLayout()
                    updateMapDownloadButtonText()
                }
            }
            GLMapViewRenderer.GLMapTileState.Loaded -> {
                if (btnDownloadMap.visibility == View.VISIBLE) {
                    btnDownloadMap.visibility = View.INVISIBLE
                }
            }
            GLMapViewRenderer.GLMapTileState.Unknown -> {
            }
        }
    }

    companion object {
        private val unionColours = intArrayOf(
            Color.argb(255, 33, 0, 255),
            Color.argb(255, 68, 195, 255),
            Color.argb(255, 63, 237, 198),
            Color.argb(255, 15, 228, 36),
            Color.argb(255, 168, 238, 25),
            Color.argb(255, 214, 234, 25),
            Color.argb(255, 223, 180, 19),
            Color.argb(255, 255, 0, 0)
        )
    }
}
