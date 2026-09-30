package globus.demo.tour

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import globus.glmap.GLMapDrawable
import globus.glmap.GLMapError
import globus.glmap.GLMapImage
import globus.glmap.GLMapTrack
import globus.glmap.GLMapTrackData
import globus.glmap.GLMapVectorObject
import globus.glmap.GLMapVectorObjectList
import globus.glmap.GLMapVectorStyle
import globus.glmap.GLMapViewRenderer
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import globus.glsearch.GLSearchRequest
import globus.glsearch.GLSearchRequestType
import kotlin.math.ceil
import kotlin.math.roundToInt
import globus.demo.tour.DemoTourMotion as Motion

internal data class TourPlace(val point: MapPoint, val name: String)

/** The same Amalfi walk as iOS. Services finish before the single animation clock starts. */
internal class DemoTour(
    private val renderer: GLMapViewRenderer,
    private val overlay: DemoTourOverlay,
    private val onFailure: (String) -> Unit
) : AutoCloseable,
    Choreographer.FrameCallback {
    private val main = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val clock = DemoTourClock()
    private val artwork = DemoTourArtwork(renderer.screenScale)
    private val pins = mutableListOf<GLMapImage>()
    private var startDot: GLMapImage? = null
    private var finishRing: GLMapImage? = null
    internal var userDot: GLMapImage? = null
        private set
    private var userArrow: GLMapImage? = null
    private var routeTrack: GLMapTrack? = null
    private var routeCasing: GLMapTrack? = null
    private var shape = emptyList<MapPoint>()
    private var distances = doubleArrayOf()
    private var followHeadings = DoubleArray(101)
    private var midpoint = MapGeoPoint(40.635, 14.606)
    private var active = false
    private var resumed = false
    private var framePending = false
    private var ready = false
    private var generation = 0
    private var searchID = 0L
    private var routeID = 0L

    fun start() {
        if (active) return
        active = true
        generation++
        renderer.drawHillshades = true
        renderer.drawElevationLines = true
        renderer.altitudeScale = 1f
        renderer.setMapOrigin(0.5f, 0.5f)
        setCamera(opening)
        preparePlaces()
    }

    fun resume() {
        resumed = true
        requestFrame()
    }

    fun pause() {
        resumed = false
        choreographer.removeFrameCallback(this)
        framePending = false
        clock.pause()
    }

    override fun close() {
        active = false
        generation++
        pause()
        if (searchID != 0L) GLSearchRequest.cancel(searchID)
        if (routeID != 0L) GLRouteRequest.cancel(routeID)
        searchID = 0
        routeID = 0
        listOfNotNull(routeTrack, routeCasing).forEach {
            renderer.remove(it)
            it.dispose()
        }
        (pins + listOfNotNull(startDot, finishRing, userDot, userArrow)).forEach {
            renderer.remove(it)
            it.dispose()
        }
        pins.clear()
        routeTrack = null
        routeCasing = null
        startDot = null
        finishRing = null
        userDot = null
        userArrow = null
        shape = emptyList()
        ready = false
        renderer.cancelMapAnimations()
    }

    private fun preparePlaces() {
        val token = generation
        val request = GLSearchRequest(
            GLSearchRequestType.Search,
            "",
            ATRANI,
            60,
            arrayOf("en", "native"),
            arrayOf("restaurant")
        )
        searchID = request.startOnline(object : GLSearchRequest.ResultsCallback {
            override fun onResult(objects: GLMapVectorObjectList) {
                main.post {
                    objects.use {
                        if (!active || token != generation) return@post
                        searchID = 0
                        // Copy only values needed by the tour, then release the native search results.
                        val candidates = objects.toArray().map { obj ->
                            obj.use { TourPlace(it.point(), name(it)) }
                        }.filter { it.name.isNotBlank() && ATRANI.distanceToPoint(it.point) < 1500 }
                            .sortedBy { ATRANI.distanceToPoint(it.point) }
                        val selected = candidates.firstOrNull()
                            ?: return@post fail("No restaurants found near Atrani.")
                        val places = mutableListOf(selected)
                        for (place in candidates) {
                            if (places.size == 5) break
                            if (places.all { distance(it.point, place.point) > 75 }) places += place
                        }
                        prepareRoute(selected, places, token)
                    }
                }
            }
            override fun onError(error: GLMapError) {
                main.post {
                    if (!active || token != generation) return@post
                    searchID = 0
                    fail("Search failed: $error")
                }
            }
        })
    }

    private fun prepareRoute(destination: TourPlace, places: List<TourPlace>, token: Int) {
        val request = GLRouteRequest().apply {
            setPedestrianWithOptions(CostingOptions.Pedestrian())
            addPoint(GLRoutePoint(START, Double.NaN, GLRoutePoint.Type.BREAK))
            addPoint(GLRoutePoint(MapGeoPoint(destination.point), Double.NaN, GLRoutePoint.Type.BREAK))
        }
        routeID = request.startOnline(object : GLRouteRequest.ResultsCallback {
            override fun onResult(route: GLRoute) {
                main.post {
                    route.use {
                        if (!active || token != generation) return@post
                        routeID = 0
                        val coordinates = route.trackCoordinates
                        if (coordinates == null || coordinates.size < 4 || coordinates.size % 2 != 0 ||
                            !route.length.isFinite() || !route.duration.isFinite()
                        ) {
                            return@post fail("The route has invalid geometry or duration.")
                        }
                        val points = coordinates.asList().chunked(2).filter { it[0] >= 0 && it[1] >= 0 }
                            .map { MapPoint(it[0].toDouble(), it[1].toDouble()) }
                        val distanceText = if (route.length >= 1000) "%.1f km".format(route.length / 1000)
                        else "${(route.length / 10).roundToInt() * 10} m"
                        val summary = "${ceil(route.duration / 60).toInt()} min  ·  $distanceText"
                        route.getTrackData(DemoTourArtwork.ACCENT).use { data ->
                            route.getTrackData(Color.WHITE).use { casing ->
                                installRoute(points, data, casing, places, summary)
                            }
                        }
                    }
                }
            }
            override fun onError(error: GLMapError) {
                main.post {
                    if (!active || token != generation) return@post
                    routeID = 0
                    fail("Routing failed: $error")
                }
            }
        })
        request.dispose()
    }

    /** Install prepared SDK data. The tracks retain it; callers may close their data wrappers afterwards. */
    internal fun installRoute(
        points: List<MapPoint>,
        data: GLMapTrackData,
        casing: GLMapTrackData,
        places: List<TourPlace>,
        summary: String
    ) {
        if (points.size < 2 || places.isEmpty()) return fail("The route has no walking geometry.")
        val lengths = DoubleArray(points.size)
        for (index in 1 until points.size) {
            lengths[index] =
                lengths[index - 1] + distance(points[index - 1], points[index])
        }
        if (!lengths.last().isFinite() || lengths.last() <= 0) return fail("The route has no walking distance.")
        shape = points
        distances = lengths
        midpoint = MapGeoPoint(data.bBox.center())
        for (sample in 0..100) {
            val angle = bearing(sample / 100.0, 0.12)
            followHeadings[sample] =
                Motion.mixAngle(if (sample == 0) angle else followHeadings[sample - 1], angle, 0.25)
        }
        routeCasing = makeTrack(casing, 10, 5)
        routeTrack = makeTrack(data, 6, 6)
        val pin = artwork.pin()
        places.forEachIndexed { index, place ->
            pins += makeImage(
                pin,
                place.point,
                if (index == 0) 21 else 20,
                pin.width / 2,
                (3 * renderer.screenScale).roundToInt()
            )
        }
        startDot = makeImage(artwork.startDot(), shape.first(), 22)
        finishRing = makeImage(artwork.finishRing(), shape.last(), 23)
        userDot = makeImage(artwork.userDot(), shape.first(), 100)
        userArrow = makeImage(artwork.userArrow(), shape.first(), 101).apply { rotatesWithMap = true }
        overlay.showPlace(places.first().name, summary)
        ready = true
        requestFrame()
    }

    private fun makeTrack(data: GLMapTrackData, width: Int, order: Int) = GLMapTrack(order).apply {
        setProgressColor(Color.TRANSPARENT)
        setProgressIndex(0.0)
        setProgressEndIndex(0.0)
        isHidden = true
        GLMapVectorStyle.createStyle("{width:${width}pt;}")!!.use { setData(data, it, null) }
        renderer.add(this)
    }

    private fun makeImage(
        bitmap: Bitmap,
        point: MapPoint,
        order: Int,
        offsetX: Int = bitmap.width / 2,
        offsetY: Int = bitmap.height / 2
    ) = GLMapImage(order).apply {
        setBitmap(bitmap)
        position = point
        setOffset(offsetX, offsetY)
        // Match iOS: native, screen-facing markers positioned using SDK terrain elevation.
        drawTarget = GLMapDrawable.DrawTarget.Unbound
        isHidden = true
        renderer.add(this)
    }

    private fun opacity(image: GLMapImage?, value: Double) {
        val alpha = (value.coerceIn(0.0, 1.0) * 255).roundToInt()
        image?.isHidden = alpha == 0
        // Neutral overlay tint; zero alpha disables tint, so hide explicitly at that endpoint.
        image?.tint = Color.argb(alpha, 128, 128, 128)
    }

    private fun requestFrame() {
        if (active && resumed && ready && !framePending) {
            framePending = true
            choreographer.postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePending = false
        if (!active || !resumed || !ready) return
        render(clock.frame(frameTimeNanos))
        requestFrame()
    }

    internal fun render(time: Double) {
        if (!ready) return
        val walked = Motion.walkFraction(time)
        val current = position(walked)
        val outro = Motion.outroOpacity(time)
        renderer.setMapOrigin(0.5f, (0.5 - 0.15 * Motion.followWeight(time)).toFloat())
        setCamera(camera(time))
        overlay.update(time)
        val selected = Motion.ease((time - Motion.SELECTION_START) / 0.4)
        pins.forEachIndexed { index, pin ->
            val appear = Motion.ease((time - Motion.PINS_START - index * 0.16) / 0.35)
            val others = if (index == 0) 1.0 else
                (1 - selected * 0.65) * (1 - Motion.ease((time - Motion.ROUTE_START) / 0.6))
            opacity(pin, appear * others * outro)
            pin.scale = 0.65 + 0.17 * appear + if (index == 0) 0.16 * selected else 0.0
        }
        opacity(
            startDot,
            Motion.ease((time - Motion.ROUTE_START) / 0.4) *
                (1 - Motion.ease((time - Motion.FOLLOW_START) / 0.4))
        )
        val markerOpacity = Motion.ease((time - Motion.FOLLOW_START) / 0.4) * outro
        userDot?.position = current.point
        userArrow?.position = current.point
        opacity(userDot, markerOpacity)
        opacity(userArrow, markerOpacity * (1 - Motion.ease((time - Motion.ARRIVAL) / 0.3)))
        userArrow?.angle = -bearing(walked, 0.02).toFloat()
        val pulseStart = if (time < Motion.ARRIVAL) Motion.REVEAL_END else Motion.ARRIVAL
        val pulse = ((time - pulseStart) / 0.8).coerceIn(0.0, 1.0)
        finishRing?.scale = 0.7 + pulse * 1.3
        opacity(finishRing, if (time >= pulseStart && time < pulseStart + 0.8) 1 - pulse else 0.0)
        val revealing = time < Motion.REVEAL_END
        val reveal = Motion.ease((time - Motion.REVEAL_START) / (Motion.REVEAL_END - Motion.REVEAL_START))
        val endIndex = if (revealing) position(reveal).index else Double.POSITIVE_INFINITY
        val alpha = (255 * outro).roundToInt()
        routeTrack?.apply {
            isHidden = time < Motion.REVEAL_START || time >= Motion.OUTRO_END
            setProgressColor(if (revealing) Color.TRANSPARENT else Color.argb(alpha, 132, 141, 155))
            setProgressIndex(if (revealing) 0.0 else current.index)
            setProgressEndIndex(endIndex)
        }
        routeCasing?.apply {
            isHidden = time < Motion.REVEAL_START || time >= Motion.OUTRO_END
            setProgressColor(Color.argb(if (time >= Motion.OUTRO_START) alpha else 0, 255, 255, 255))
            setProgressIndex(if (time >= Motion.OUTRO_START) current.index else 0.0)
            setProgressEndIndex(endIndex)
        }
    }

    private data class Camera(val lat: Double, val lon: Double, val zoom: Double, val pitch: Double, val angle: Double)
    private val opening get() = Camera(40.6368, 14.6055, 15.1, 42.0, -18.0)
    private val searchCamera get() = Camera(40.6356, 14.6062, 15.8, 8.0, 0.0)
    private val overview get() = Camera(midpoint.lat + 0.0003, midpoint.lon, 15.95, 20.0, -4.0)

    private fun camera(time: Double): Camera = when {
        time < Motion.SEARCH_START -> mix(opening, searchCamera, Motion.ease(time / Motion.SEARCH_START))

        time < Motion.OVERVIEW_START -> searchCamera

        time < Motion.REVEAL_START -> mix(
            searchCamera,
            overview,
            Motion.ease((time - Motion.OVERVIEW_START) / (Motion.REVEAL_START - Motion.OVERVIEW_START))
        )

        time < Motion.FOLLOW_START -> overview

        time < Motion.WALK_START -> mix(overview, flightCamera(0.0), Motion.followWeight(time))

        time < Motion.RETURN_START -> flightCamera(Motion.walkFraction(time))

        time < Motion.FOLLOW_END -> mix(overview, flightCamera(1.0), Motion.followWeight(time))

        else -> mix(overview, opening, Motion.ease((time - Motion.FOLLOW_END) / (Motion.DURATION - Motion.FOLLOW_END)))
    }

    private fun flightCamera(fraction: Double): Camera {
        val points = doubleArrayOf(-0.025, 0.0, 0.0, 0.025).map { MapGeoPoint(position(fraction + it).point) }
        val sample = (fraction * 100).coerceIn(0.0, 100.0)
        val index = sample.toInt().coerceAtMost(99)
        return Camera(
            points.sumOf { it.lat } / 4,
            points.sumOf { it.lon } / 4,
            16.85,
            43.0,
            Motion.mixAngle(followHeadings[index], followHeadings[index + 1], sample - index)
        )
    }

    private data class Position(val point: MapPoint, val index: Double)
    private fun position(fraction: Double): Position {
        val index = Motion.pointIndex(fraction, distances)
        val segment = index.toInt().coerceAtMost(shape.size - 2)
        val part = index - segment
        val a = shape[segment]
        val b = shape[segment + 1]
        return Position(MapPoint(a.x + (b.x - a.x) * part, a.y + (b.y - a.y) * part), index)
    }

    private fun bearing(fraction: Double, radius: Double) = MapGeoPoint(position(fraction - radius).point)
        .bearingAngleToGeoPoint(MapGeoPoint(position(fraction + radius).point))

    private fun setCamera(camera: Camera) {
        renderer.mapGeoCenter = MapGeoPoint(camera.lat, camera.lon)
        renderer.mapZoom = camera.zoom
        renderer.mapPitch = camera.pitch.toFloat()
        renderer.mapAngle = camera.angle.toFloat()
    }

    private fun mix(a: Camera, b: Camera, t: Double) = Camera(
        a.lat + (b.lat - a.lat) * t,
        a.lon + (b.lon - a.lon) * t,
        a.zoom + (b.zoom - a.zoom) * t,
        a.pitch + (b.pitch - a.pitch) * t,
        Motion.mixAngle(a.angle, b.angle, t)
    )

    private fun name(obj: GLMapVectorObject) = obj.localizedName(renderer.localeSettings)?.use { it.string }.orEmpty()
    private fun distance(a: MapPoint, b: MapPoint) = MapGeoPoint(a).distanceToPoint(b)
    private fun fail(message: String) {
        overlay.showError(message)
        onFailure(message)
    }

    companion object {
        private val START = MapGeoPoint(40.63318, 14.60257)
        private val ATRANI = MapGeoPoint(40.63602, 14.60980)
    }
}
