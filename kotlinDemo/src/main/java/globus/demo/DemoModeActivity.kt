package globus.demo

import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import globus.glmap.GLMapAnimation
import globus.glmap.GLMapDrawObject
import globus.glmap.GLMapError
import globus.glmap.GLMapMarkerImage
import globus.glmap.GLMapMarkerLayer
import globus.glmap.GLMapMarkerStyleCollection
import globus.glmap.GLMapMarkerStyleCollectionDataCallback
import globus.glmap.GLMapStyleParser
import globus.glmap.GLMapTrack
import globus.glmap.GLMapVectorObject
import globus.glmap.GLMapVectorObjectList
import globus.glmap.GLMapVectorStyle
import globus.glmap.GLMapView
import globus.glmap.MapGeoPoint
import globus.glmap.SVGRender
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import globus.glsearch.GLSearchRequest
import globus.glsearch.GLSearchRequestType
import kotlin.concurrent.thread
import kotlin.math.ln

class DemoModeActivity : AppCompatActivity() {
    private lateinit var mapView: GLMapView
    private val renderer get() = mapView.renderer
    private lateinit var titleLabel: TextView
    private lateinit var subtitleLabel: TextView
    private lateinit var captionLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val overlays = mutableListOf<GLMapDrawObject>()
    private var scene = 0
    private var running = true
    private var contentGeneration = 0
    private var routeRequestID = 0L
    private var searchRequestID = 0L
    private var searchObjects = emptyArray<GLMapVectorObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())

        val root = FrameLayout(this)
        mapView = GLMapView(this)
        root.addView(mapView)
        root.addView(createOverlay())
        setContentView(root)

        loadStyle(emptyMap())
        renderer.doWhenSurfaceCreated { runOnUiThread(::nextScene) }
    }

    private fun createOverlay() = FrameLayout(this).apply {
        isClickable = true
        setOnClickListener { finish() }

        val titleGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        titleLabel = TextView(context).apply {
            alpha = 0f
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = 48f
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            titleLabel,
            24,
            48,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        subtitleLabel = TextView(context).apply {
            alpha = 0f
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(0xCC000000.toInt())
        }
        titleGroup.addView(
            titleLabel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        titleGroup.addView(
            subtitleLabel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        addView(
            titleGroup,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(dp(32), 0, dp(32), 0)
            }
        )

        captionLabel = TextView(context).apply {
            alpha = 0f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(CHROME_COLOR)
                cornerRadius = dp(16).toFloat()
            }
        }
        addView(
            captionLabel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            }
        )

        addView(
            TextView(context).apply {
                text = "✕"
                gravity = Gravity.CENTER
                textSize = 28f
                setTextColor(CHROME_COLOR)
                setTypeface(typeface, Typeface.BOLD)
                setOnClickListener { finish() }
            },
            FrameLayout.LayoutParams(dp(40), dp(40)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(16)
                marginEnd = dp(16)
            }
        )
    }

    private fun nextScene() {
        if (!running) return
        if (scene == SCENE_COUNT) {
            scene = 0
            clearOverlays()
            hideTitle()
            showCaption(null)
        }

        when (scene++) {
            0 -> {
                showTitle("GLMap 2.0", "SDK Demo")
                handler.postDelayed({
                    hideTitle()
                    nextScene()
                }, 3_000)
            }

            1 -> {
                renderer.animate {
                    it.flyToMode = GLMapAnimation.FlyToMode.Enabled
                    it.setDuration(4.5)
                    renderer.mapGeoCenter = SCENIC_CENTER
                    renderer.mapZoom = 12.0
                }
                nextAfter(5_000)
            }

            2 -> {
                showCaption("3D Terrain")
                renderer.animate {
                    it.setDuration(5.5)
                    renderer.mapPitch = 45f
                    renderer.altitudeScale = 1.5f
                }
                renderer.drawHillshades = true
                renderer.drawElevationLines = true
                nextAfter(6_000)
            }

            3 -> {
                showCaption("3D Terrain")
                renderer.animate {
                    it.setDuration(9.5)
                    it.setTransition(GLMapAnimation.Linear)
                    renderer.mapGeoCenter = ROUTE_END
                    renderer.mapAngle = 45f
                }
                nextAfter(10_000)
            }

            4 -> {
                showCaption("Dark Theme")
                loadStyle(mapOf("Theme" to "Dark"))
                nextAfter(4_000)
            }

            5 -> {
                showCaption("Dark Theme")
                renderer.animate {
                    it.setDuration(7.5)
                    it.setTransition(GLMapAnimation.Linear)
                    renderer.mapGeoCenter = ROUTE_START
                    renderer.mapAngle = -45f
                }
                nextAfter(8_000)
            }

            6 -> {
                showCaption(null)
                loadStyle(emptyMap())
                renderer.animate {
                    it.setDuration(2.5)
                    renderer.mapPitch = 0f
                    renderer.altitudeScale = 0f
                    renderer.mapAngle = 0f
                }
                renderer.drawHillshades = false
                renderer.drawElevationLines = false
                nextAfter(3_000)
            }

            7 -> {
                showCaption("Marker Clustering")
                showMarkerClusters()
                handler.postDelayed({
                    if (!running) return@postDelayed
                    renderer.animate {
                        it.flyToMode = GLMapAnimation.FlyToMode.Enabled
                        it.setDuration(3.5)
                        renderer.mapZoom = 8.0
                    }
                }, 2_000)
                nextAfter(6_000)
            }

            8 -> {
                showCaption("Routing")
                clearOverlays()
                renderer.drawHillshades = true
                renderer.drawElevationLines = true
                renderer.animate {
                    it.flyToMode = GLMapAnimation.FlyToMode.Enabled
                    it.setDuration(2.0)
                    renderer.mapGeoCenter = MapGeoPoint(40.640, 14.610)
                    renderer.mapZoom = 12.0
                    renderer.mapPitch = 45f
                    renderer.altitudeScale = 1.5f
                }
                handler.postDelayed({ if (running) buildDemoRoute() }, 2_500)
                nextAfter(8_000)
            }

            9 -> {
                showCaption("Routing")
                renderer.animate {
                    it.setDuration(2.0)
                    renderer.mapPitch = 45f
                    renderer.mapZoom = 14.0
                }
                handler.postDelayed({
                    if (!running) return@postDelayed
                    renderer.animate {
                        it.setDuration(12.0)
                        it.setTransition(GLMapAnimation.Linear)
                        renderer.mapGeoCenter = ROUTE_END
                    }
                }, 2_500)
                nextAfter(15_000)
            }

            10 -> {
                showCaption("Search")
                clearOverlays()
                renderer.animate {
                    it.setDuration(2.0)
                    renderer.mapPitch = 0f
                }
                showOnlineSearchResults()
                nextAfter(6_000)
            }

            11 -> {
                showCaption(null)
                clearOverlays()
                renderer.animate {
                    it.flyToMode = GLMapAnimation.FlyToMode.Enabled
                    it.setDuration(3.0)
                    renderer.mapZoom = 2.0
                    renderer.mapGeoCenter = MapGeoPoint(30.0, 10.0)
                }
                handler.postDelayed({ if (running) showTitle("globus.software", null) }, 1_000)
                nextAfter(5_000)
            }
        }
    }

    private fun showMarkerClusters() {
        val colors = intArrayOf(0xFF2100FF.toInt(), 0xFF44C3FF.toInt(), 0xFF3FEDC6.toInt(), 0xFF0FE424.toInt())
        val styles = GLMapMarkerStyleCollection()
        var maxWidth = 0
        colors.forEachIndexed { index, color ->
            val image = SVGRender.render(
                assets,
                "cluster.svg",
                SVGRender.transform(renderer.screenScale * (0.2 + index * 0.1), color)
            ) ?: return
            maxWidth = maxOf(maxWidth, image.width)
            styles.addStyle(GLMapMarkerImage("demo-cluster-$index", image))
        }
        styles.setDataCallback(object : GLMapMarkerStyleCollectionDataCallback() {
            private val textStyle = GLMapVectorStyle.createStyle(
                "{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:#FFFFFFEE;}"
            )!!

            override fun getLocation(marker: Any) = (marker as GLMapVectorObject).point()
            override fun fillData(marker: Any, nativeMarker: Long) =
                GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)

            override fun fillUnionData(markersCount: Int, nativeMarker: Long) {
                val style = (ln(markersCount.toDouble()) / ln(2.0)).toInt().coerceIn(0, colors.lastIndex)
                GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, style)
                GLMapMarkerStyleCollection.setMarkerText(
                    nativeMarker,
                    markersCount.toString(),
                    globus.glmap.GLMapTextAlignment.Undefined,
                    Point(),
                    textStyle
                )
            }
        })

        val generation = contentGeneration
        thread(name = "GLMap demo mode markers") {
            try {
                val objects = assets.open("cluster_data.json").use(GLMapVectorObject::createFromGeoJSONStreamOrThrow)
                val bbox = objects.bBox
                val layer = GLMapMarkerLayer(
                    objects.toArray(),
                    styles,
                    maxWidth.toDouble() / renderer.screenScale / 2,
                    2
                )
                objects.dispose()
                runOnUiThread {
                    if (!running || generation != contentGeneration) {
                        layer.dispose()
                        return@runOnUiThread
                    }
                    renderer.add(layer)
                    overlays += layer
                    renderer.mapCenter = bbox.center()
                    renderer.mapZoom = renderer.mapZoomForBBox(bbox)
                }
            } catch (error: Exception) {
                Log.e("GLMapDemo", "Cannot load demo markers", error)
            }
        }
    }

    private fun buildDemoRoute() {
        val generation = contentGeneration
        val request = GLRouteRequest().apply {
            val options = CostingOptions.Auto()
            options.road.useHighways = 0f // Keep this showcase route on the scenic coastal roads.
            setAutoWithOptions(options)
            addPoint(GLRoutePoint(ROUTE_START, Double.NaN, GLRoutePoint.Type.BREAK))
            addPoint(GLRoutePoint(ROUTE_END, Double.NaN, GLRoutePoint.Type.BREAK))
        }
        routeRequestID = request.startOnline(object : GLRouteRequest.ResultsCallback {
            override fun onResult(route: GLRoute) = runOnUiThread {
                if (!running || generation != contentGeneration) return@runOnUiThread
                routeRequestID = 0
                val track = GLMapTrack(5).apply {
                    setData(
                        route.getTrackData(0xC832C800.toInt()),
                        GLMapVectorStyle.createStyle("{width:7pt;fill-image:\"track-arrow.svg\";}")!!,
                        null
                    )
                    renderer.add(this)
                }
                overlays += track
            }

            override fun onError(error: globus.glmap.GLMapError) {
                Log.e("GLMapDemo", "Demo route failed: $error")
            }
        })
    }

    private fun showOnlineSearchResults() {
        val generation = contentGeneration
        val request = GLSearchRequest(
            GLSearchRequestType.Search,
            "",
            SCENIC_CENTER,
            30,
            arrayOf("en", "native"),
            arrayOf("restaurant")
        )
        searchRequestID = request.startOnline(object : GLSearchRequest.ResultsCallback {
            override fun onResult(objects: GLMapVectorObjectList) = runOnUiThread {
                if (!running || generation != contentGeneration) {
                    objects.dispose()
                    return@runOnUiThread
                }
                searchRequestID = 0
                val bbox = objects.bBox
                val results = objects.toArray()
                objects.dispose()
                if (results.isEmpty()) {
                    showCaption("Search unavailable")
                    return@runOnUiThread
                }

                val bitmap = SVGRender.render(
                    assets,
                    "cluster.svg",
                    SVGRender.transform(renderer.screenScale * 0.2, Color.rgb(0, 102, 204))
                )
                if (bitmap == null) {
                    results.forEach(GLMapVectorObject::dispose)
                    showCaption("Search unavailable")
                    return@runOnUiThread
                }

                val styles = GLMapMarkerStyleCollection().apply {
                    addStyle(GLMapMarkerImage("search-result", bitmap))
                    setDataCallback(object : GLMapMarkerStyleCollectionDataCallback() {
                        override fun getLocation(marker: Any) = (marker as GLMapVectorObject).point()
                        override fun fillUnionData(markersCount: Int, nativeMarker: Long) = Unit
                        override fun fillData(marker: Any, nativeMarker: Long) =
                            GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)
                    })
                }
                val layer = GLMapMarkerLayer(results, styles, 0.0, 3)
                searchObjects = results
                renderer.add(layer)
                overlays += layer
                showCaption("Search · ${results.size} results")
                renderer.animate {
                    it.setDuration(1.5)
                    renderer.mapCenter = bbox.center()
                    renderer.mapZoom = renderer.mapZoomForBBox(bbox)
                }
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (!running || generation != contentGeneration) return@runOnUiThread
                searchRequestID = 0
                Log.e("GLMapDemo", "Demo search failed: $error")
                showCaption("Search unavailable")
            }
        })
    }

    private fun clearOverlays() {
        contentGeneration++
        if (routeRequestID != 0L) GLRouteRequest.cancel(routeRequestID)
        routeRequestID = 0
        if (searchRequestID != 0L) GLSearchRequest.cancel(searchRequestID)
        searchRequestID = 0
        overlays.forEach {
            renderer.remove(it)
            it.dispose()
        }
        overlays.clear()
        searchObjects.forEach(GLMapVectorObject::dispose)
        searchObjects = emptyArray()
    }

    private fun loadStyle(options: Map<String, String>) {
        val parser = GLMapStyleParser(assets, "DefaultStyle.bundle")
        parser.setOptions(options, true)
        val style = parser.parseFromResources()
        if (style == null) {
            Log.e("GLMapDemo", "Cannot parse map style with options $options")
            return
        }
        renderer.setStyle(style)
        renderer.reloadTiles()
    }

    private fun showTitle(title: String, subtitle: String?) {
        titleLabel.text = title
        subtitleLabel.text = subtitle
        titleLabel.animate().alpha(1f).setDuration(800).start()
        subtitleLabel.animate().alpha(if (subtitle == null) 0f else 1f).setDuration(800).start()
    }

    private fun hideTitle() {
        titleLabel.animate().alpha(0f).setDuration(500).start()
        subtitleLabel.animate().alpha(0f).setDuration(500).start()
    }

    private fun showCaption(text: String?) {
        if (text == null) {
            captionLabel.animate().alpha(0f).setDuration(400).start()
            return
        }
        if (captionLabel.text == text && captionLabel.alpha == 1f) return
        captionLabel.text = text
        captionLabel.animate().alpha(1f).setDuration(400).start()
    }

    private fun nextAfter(delay: Long) {
        handler.postDelayed(::nextScene, delay)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        clearOverlays()
        mapView.dispose()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHROME_COLOR = 0x99000000.toInt()
        private const val SCENE_COUNT = 12
        private val SCENIC_CENTER = MapGeoPoint(40.633, 14.602)
        private val ROUTE_START = MapGeoPoint(40.633, 14.502)
        private val ROUTE_END = MapGeoPoint(40.650, 14.720)
    }
}
