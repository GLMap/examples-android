package globus.demo.draw

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapBalloon
import globus.glmap.GLMapImage
import globus.glmap.GLMapImageGroup
import globus.glmap.GLMapImageGroupCallback
import globus.glmap.GLMapLineArrow
import globus.glmap.GLMapMarkerImage
import globus.glmap.GLMapMarkerLayer
import globus.glmap.GLMapMarkerStyleCollection
import globus.glmap.GLMapMarkerStyleCollectionDataCallback
import globus.glmap.GLMapTextAlignment
import globus.glmap.GLMapTrack
import globus.glmap.GLMapVectorObject
import globus.glmap.GLMapVectorStyle
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import globus.glmap.SVGRender
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.ln

class ImageActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Tap map to move the image"
        renderer.mapGeoCenter = MapGeoPoint(48.8566, 2.3522)
        renderer.mapZoom = 7.0

        val bitmap = SVGRender.render(
            assets,
            "pin.svg",
            SVGRender.transform(renderer.screenScale * 1.6, Color.rgb(230, 60, 60)),
        )
            ?: return showError("Cannot render marker SVG")
        val image = GLMapImage(3).apply {
            setBitmap(bitmap)
            setOffset(bitmap.width / 2, 0)
            position = MapPoint(renderer.mapGeoCenter)
        }
        renderer.add(image)

        setGestures(onTap = { touch ->
            val point = renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble())
            renderer.animate { animation ->
                animation.setDuration(0.3)
                animation.setPosition(image, point)
            }
        })
    }
}

private data class GroupPin(val position: MapPoint, val variant: Int)

private class PinGroup(private val variants: List<Bitmap>) : GLMapImageGroupCallback {
    private val lock = ReentrantLock()
    private val pins = mutableListOf<GroupPin>()

    override fun getImageVariantsCount() = variants.size
    override fun getImageVariantBitmap(i: Int) = variants[i]
    override fun getImageVariantOffset(i: Int) = MapPoint(variants[i].width / 2.0, 0.0)
    override fun getImagesCount() = pins.size
    override fun getImageIndex(i: Int) = pins[i].variant
    override fun getImagePos(i: Int) = pins[i].position
    override fun updateStarted() = lock.lock()
    override fun updateFinished() = lock.unlock()

    fun add(position: MapPoint) = lock.withLock { pins += GroupPin(position, pins.size % variants.size) }

    fun removeNear(renderer: globus.glmap.GLMapViewRenderer, x: Float, y: Float): Boolean = lock.withLock {
        val index = pins.indexOfFirst {
            val point = renderer.convertInternalToDisplay(it.position)
            val dx = point.x - x
            val dy = point.y - y
            dx * dx + dy * dy < 40 * 40
        }
        if (index < 0) false else {
            pins.removeAt(index)
            true
        }
    }
}

class ImageGroupActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Long press to add, tap to remove"
        renderer.mapGeoCenter = MapGeoPoint(48.8566, 2.3522)
        renderer.mapZoom = 13.0

        val variants = listOf(Color.rgb(230, 60, 60), Color.rgb(60, 120, 230), Color.rgb(40, 180, 90)).mapNotNull {
            SVGRender.render(assets, "pin.svg", SVGRender.transform(renderer.screenScale * 1.6, it))
        }
        if (variants.isEmpty()) return showError("Cannot render marker SVGs")

        val pins = PinGroup(variants)
        listOf(
            48.8584 to 2.2945,
            48.8606 to 2.3376,
            48.8530 to 2.3499,
            48.8867 to 2.3431,
            48.8738 to 2.2950,
            48.8462 to 2.3464,
            48.8600 to 2.3266,
            48.8619 to 2.2870,
        ).forEach { (lat, lon) -> pins.add(MapPoint.CreateFromGeoCoordinates(lat, lon)) }

        val group = GLMapImageGroup(pins, 3)
        renderer.add(group)
        setGestures(
            onTap = { if (pins.removeNear(renderer, it.x, it.y)) group.setNeedsUpdate(false) },
            onLongPress = {
                pins.add(renderer.convertDisplayToInternal(it.x.toDouble(), it.y.toDouble()))
                group.setNeedsUpdate(false)
            },
        )
    }
}

class MarkerClusteringActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Markers & Clustering"
        val (styles, clusteringRadius) = markerStyles() ?: return

        thread(name = "GLMap demo GeoJSON") {
            try {
                val objects = assets.open("cluster_data.json").use(GLMapVectorObject::createFromGeoJSONStreamOrThrow)
                val bbox = objects.bBox
                val layer = GLMapMarkerLayer(objects.toArray(), styles, clusteringRadius, 3)
                objects.dispose()
                runOnUiThread {
                    if (isDestroyed) {
                        layer.dispose()
                        return@runOnUiThread
                    }
                    renderer.add(layer)
                    fit(bbox)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed) showError(error.message ?: "Cannot load markers")
                }
            }
        }
    }

    private fun markerStyles(): Pair<GLMapMarkerStyleCollection, Double>? {
        val colors = intArrayOf(
            0xFF2100FF.toInt(), 0xFF44C3FF.toInt(), 0xFF3FEDC6.toInt(), 0xFF0FE424.toInt(),
            0xFFA8EE19.toInt(), 0xFFD6EA19.toInt(), 0xFFDFB413.toInt(), 0xFFFF0000.toInt(),
        )
        val styles = GLMapMarkerStyleCollection()
        var maxWidth = 0
        colors.forEachIndexed { index, color ->
            val bitmap = SVGRender.render(
                assets,
                "cluster.svg",
                SVGRender.transform(renderer.screenScale * (0.2 + index * 0.1), color),
            )
            if (bitmap == null) {
                styles.dispose()
                return null
            }
            maxWidth = maxOf(maxWidth, bitmap.width)
            styles.addStyle(GLMapMarkerImage("cluster$index", bitmap))
        }
        styles.setDataCallback(ClusterStyle(colors.size))
        return styles to maxWidth.toDouble() / renderer.screenScale / 2
    }
}

private class ClusterStyle(private val styleCount: Int) : GLMapMarkerStyleCollectionDataCallback() {
    private val textStyle = GLMapVectorStyle.createStyle(
        "{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:#FFFFFFEE;}",
    )!!

    override fun getLocation(marker: Any) = (marker as GLMapVectorObject).point()

    override fun fillUnionData(markersCount: Int, nativeMarker: Long) {
        val style = (ln(markersCount.toDouble()) / ln(2.0)).toInt().coerceIn(0, styleCount - 1)
        GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, style)
        GLMapMarkerStyleCollection.setMarkerText(
            nativeMarker,
            markersCount.toString(),
            GLMapTextAlignment.Undefined,
            Point(0, 0),
            textStyle,
        )
    }

    override fun fillData(marker: Any, nativeMarker: Long) {
        GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)
        (marker as GLMapVectorObject).valueForKey("name")?.use { name ->
            name.string?.let {
                GLMapMarkerStyleCollection.setMarkerText(
                    nativeMarker,
                    it,
                    GLMapTextAlignment.Undefined,
                    Point(0, 8),
                    textStyle,
                )
            }
        }
    }
}

class BalloonActivity : MapDemoActivity() {
    private val landmarks = listOf(
        "Eiffel Tower" to MapPoint.CreateFromGeoCoordinates(48.8584, 2.2945),
        "Colosseum" to MapPoint.CreateFromGeoCoordinates(41.8902, 12.4922),
        "Big Ben" to MapPoint.CreateFromGeoCoordinates(51.5007, -0.1246),
        "Brandenburg Gate" to MapPoint.CreateFromGeoCoordinates(52.5163, 13.3777),
    )
    private var balloon: GLMapBalloon? = null
    private lateinit var background: Bitmap
    private val textStyle = GLMapVectorStyle.createStyle("{text-color:#2C3E50;font-size:16;}")!!

    override fun onMapReady() {
        title = "Tap a pin to see balloon"
        renderer.mapGeoCenter = MapGeoPoint(48.0, 8.0)
        renderer.mapZoom = 5.0

        val pinBitmap = SVGRender.render(
            assets,
            "pin.svg",
            SVGRender.transform(renderer.screenScale * 1.6, Color.rgb(230, 60, 60)),
        )
            ?: return showError("Cannot render marker SVG")
        background = createBalloonBackground()
        landmarks.forEach { (_, position) ->
            renderer.add(GLMapImage(3).apply {
                setBitmap(pinBitmap)
                setOffset(pinBitmap.width / 2, 0)
                this.position = position
            })
        }
        setGestures(onTap = { touch ->
            val landmark = landmarks.minByOrNull {
                val display = renderer.convertInternalToDisplay(it.second)
                val dx = display.x - touch.x
                val dy = display.y - touch.y
                dx * dx + dy * dy
            } ?: return@setGestures
            val display = renderer.convertInternalToDisplay(landmark.second)
            val dx = display.x - touch.x
            val dy = display.y - touch.y
            if (dx * dx + dy * dy > 40 * 40) return@setGestures
            showBalloon(landmark.first, landmark.second)
        })
    }

    private fun showBalloon(text: String, position: MapPoint) {
        balloon?.let {
            renderer.remove(it)
            it.dispose()
        }
        balloon = GLMapBalloon(10).apply {
            setBackgroundBitmap(background, Rect(dp(20), dp(20), dp(20), dp(20)))
            setText(
                text,
                textStyle,
                Rect(dp(14), dp(10), dp(14), dp(10)),
                null,
            )
            this.position = position
            renderer.add(this)
        }
    }
}

class TrackArrowsActivity : MapDemoActivity() {
    private var requestID = 0L
    private var generation = 0

    override fun onMapReady() {
        title = "Building route..."
        renderer.mapGeoCenter = MapGeoPoint(40.640, 14.610)
        renderer.mapZoom = 12.0

        val blue = Color.rgb(66, 133, 244)
        val head = SVGRender.render(
            assets,
            "route-maneuver-head.svg",
            SVGRender.transform(renderer.screenScale.toDouble(), blue),
        ) ?: return showError("Cannot render maneuver arrow SVG")
        val arrowStyle = GLMapVectorStyle.createStyle(
            "{casing-width:2pt;casing-color:#4285F4FF;width:14pt;color:white;linecap:round;}",
        )!!
        val maneuverArrow = GLMapLineArrow(6).apply {
            setLineStyle(arrowStyle, head)
            isHidden = true
            renderer.add(this)
        }

        val currentGeneration = ++generation
        val request = GLRouteRequest().apply {
            setAutoWithOptions(CostingOptions.Auto())
            addPoint(GLRoutePoint(MapGeoPoint(40.633, 14.502), Double.NaN, GLRoutePoint.Type.BREAK))
            addPoint(GLRoutePoint(MapGeoPoint(40.650, 14.720), Double.NaN, GLRoutePoint.Type.BREAK))
        }
        requestID = request.startOnline(object : GLRouteRequest.ResultsCallback {
            override fun onResult(route: GLRoute) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                // fill-image repeats small arrows over the entire route track.
                val style = GLMapVectorStyle.createStyle("{width:14pt; fill-image:\"track-arrow.svg\";}")!!
                val trackData = route.getTrackData(0xDC4285F4.toInt())
                renderer.add(GLMapTrack(5).apply { setData(trackData, style, null) })

                // GLMapLineArrow highlights one concrete maneuver independently of the track fill.
                val maneuver = route.maneuvers.getOrNull(1) ?: return@runOnUiThread
                maneuverArrow.setLine(maneuver.line, maneuver.lineStartIndex)
                maneuverArrow.isHidden = false
                title = "Track Arrows"
                renderer.animate { animation ->
                    animation.flyToMode = globus.glmap.GLMapAnimation.FlyToMode.Enabled
                    animation.setDuration(1.5)
                    renderer.mapCenter = maneuver.startPoint
                    renderer.mapZoom = 17.0
                }
            }

            override fun onError(error: globus.glmap.GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                title = "Route failed — check network"
                showError(error.toString())
            }
        })
    }

    override fun onDestroy() {
        generation++
        if (requestID != 0L) GLRouteRequest.cancel(requestID)
        super.onDestroy()
    }
}
