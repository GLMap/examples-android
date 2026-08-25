package globus.demo.routing

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.view.Gravity
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import globus.demo.base.MapDemoActivity
import globus.demo.draw.LocationMapActivity
import globus.demo.R
import globus.glmap.GLMapAnimation
import globus.glmap.GLMapBBox
import globus.glmap.GLMapError
import globus.glmap.GLMapLineArrow
import globus.glmap.GLMapLocaleSettings
import globus.glmap.GLMapTrack
import globus.glmap.GLMapVectorStyle
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import globus.glmap.SVGRender
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRouteManeuver
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import globus.glroute.GLRouteTracker
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

class RouteBuildingActivity : MapDemoActivity() {
    private var departure = MapGeoPoint(41.1457, -8.6107)
    private var destination = MapGeoPoint(41.1597, -8.6300)
    private var requestID = 0L
    private var generation = 0
    private var routeTrack: GLMapTrack? = null
    private lateinit var mode: Spinner
    private lateinit var offline: SwitchCompat

    override fun onMapReady() {
        title = "Route Building"
        supportActionBar?.subtitle = "Tap map to set departure and destination"
        mode = Spinner(this).apply {
            adapter =
                ArrayAdapter(
                    this@RouteBuildingActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("Auto", "Bike", "Walk")
                )
        }
        offline = SwitchCompat(this).apply { text = "Offline" }
        addTopView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setBackgroundColor(0xEFFFFFFF.toInt())
                addView(mode, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(offline)
            }
        )
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var selectedMode = mode.selectedItemPosition

            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position == selectedMode) return
                selectedMode = position
                updateRoute()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        offline.setOnCheckedChangeListener { _, _ -> updateRoute() }

        val bbox = GLMapBBox().apply {
            addPoint(MapPoint(departure))
            addPoint(MapPoint(destination))
        }
        renderer.doWhenSurfaceCreated {
            renderer.mapZoom = renderer.mapZoomForBBox(bbox) - 1
            centerMapOn(bbox.center())
        }
        setGestures(onTap = { touch ->
            val point = MapGeoPoint(renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble()))
            AlertDialog.Builder(this)
                .setTitle("Set route point")
                .setItems(arrayOf("Departure", "Destination")) { _, index ->
                    if (index == 0) departure = point else destination = point
                    updateRoute()
                }
                .show()
        })
        updateRoute()
    }

    private fun updateRoute() {
        cancelRequest()
        val currentGeneration = generation
        val request = GLRouteRequest().apply {
            when (this@RouteBuildingActivity.mode.selectedItemPosition) {
                1 -> setBicycleWithOptions(CostingOptions.Bicycle())
                2 -> setPedestrianWithOptions(CostingOptions.Pedestrian())
                else -> setAutoWithOptions(CostingOptions.Auto())
            }
            locale = "en"
            unitSystem = GLMapLocaleSettings.UnitSystem.International
            addPoint(GLRoutePoint(departure, Double.NaN, GLRoutePoint.Type.BREAK))
            addPoint(GLRoutePoint(destination, Double.NaN, GLRoutePoint.Type.BREAK))
        }
        val callback = object : GLRouteRequest.ResultsCallback {
            override fun onResult(route: GLRoute) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                val trackData = route.getTrackData(0xC832C800.toInt())
                val track = routeTrack ?: GLMapTrack(5).also {
                    routeTrack = it
                    renderer.add(it)
                }
                track.setData(
                    trackData,
                    GLMapVectorStyle.createStyle("{width:7pt;fill-image:\"track-arrow.svg\";}")!!,
                    null
                )
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                showError(error.toString())
            }
        }
        requestID = if (offline.isChecked) {
            request.startOffline(
                resources.openRawResource(R.raw.valhalla).use {
                    String(it.readBytes(), StandardCharsets.UTF_8)
                },
                callback
            )
        } else {
            request.startOnline(callback)
        }
    }

    private fun cancelRequest() {
        generation++
        if (requestID != 0L) GLRouteRequest.cancel(requestID)
        requestID = 0
    }

    override fun onDestroy() {
        cancelRequest()
        super.onDestroy()
    }
}

class TurnByTurnActivity : LocationMapActivity() {
    private val routeStyle = GLMapVectorStyle.createStyle("{width:14pt;fill-image:\"track-arrow.svg\";}")!!
    private val maneuverIcon by lazy {
        ImageView(this).apply {
            setImageBitmap(
                SVGRender.render(
                    assets,
                    "arrow-maphint.svg",
                    SVGRender.transform(renderer.screenScale.toDouble(), Color.WHITE)
                )
            )
            visibility = android.view.View.INVISIBLE
        }
    }
    private val maneuverDistance by lazy {
        TextView(this).apply {
            text = "--"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
    }
    private val maneuverStreet by lazy {
        TextView(this).apply {
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }
    }
    private val routeInfo by lazy {
        TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            text = "Waiting for location..."
        }
    }
    private var lastLocation: Location? = null
    private var routeTrack: GLMapTrack? = null
    private var routeTracker: GLRouteTracker? = null
    private var maneuverArrow: GLMapLineArrow? = null
    private var navigationAnimation: GLMapAnimation? = null
    private var requestID = 0L
    private var generation = 0

    override fun setupDemo() {
        title = "Waiting for location..."
        renderer.setMapOrigin(0.5f, 0.25f)
        val maneuver = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(maneuverIcon, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(
                maneuverDistance,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            )
        }
        addTopView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(0xF2262626.toInt())
                addView(maneuver)
                addView(maneuverStreet)
                addView(routeInfo)
            }
        )
        setupManeuverArrow()
        setGestures(onTap = { touch ->
            val location = lastLocation ?: return@setGestures
            val destination = MapGeoPoint(renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble()))
            buildRoute(MapGeoPoint(location.latitude, location.longitude), destination)
        })
    }

    private fun setupManeuverArrow() {
        val green = Color.rgb(50, 200, 0)
        val head = SVGRender.render(
            assets,
            "route-maneuver-head.svg",
            SVGRender.transform(renderer.screenScale.toDouble(), green)
        ) ?: return showError("Cannot render maneuver arrow SVG")
        val style = GLMapVectorStyle.createStyle(
            "{casing-width:2pt;casing-color:#32C800FF;width:14pt;color:white;linecap:round;}"
        )!!
        maneuverArrow = GLMapLineArrow(100).apply {
            setLineStyle(style, head)
            isHidden = true
            renderer.add(this)
        }
    }

    private fun buildRoute(departure: MapGeoPoint, destination: MapGeoPoint) {
        cancelRequest()
        val currentGeneration = generation
        routeTracker?.dispose()
        routeTracker = null
        maneuverArrow?.isHidden = true
        title = "Building route..."
        maneuverIcon.visibility = android.view.View.INVISIBLE
        maneuverDistance.text = "--"
        maneuverStreet.text = ""
        routeInfo.text = "Building route..."

        val request = GLRouteRequest().apply {
            setAutoWithOptions(CostingOptions.Auto())
            locale = "en-US"
            unitSystem = GLMapLocaleSettings.UnitSystem.International
            addPoint(GLRoutePoint(departure, Double.NaN, GLRoutePoint.Type.BREAK))
            addPoint(GLRoutePoint(destination, Double.NaN, GLRoutePoint.Type.BREAK))
        }
        requestID = request.startOnline(object : GLRouteRequest.ResultsCallback {
            override fun onResult(route: GLRoute) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                val trackData = route.getTrackData(0xC832C800.toInt())
                val track = routeTrack ?: GLMapTrack(99).also {
                    routeTrack = it
                    it.setProgressColor(0xC8808080.toInt())
                    renderer.add(it)
                }
                track.setProgressIndex(0.0)
                track.setData(trackData, routeStyle, null)
                routeTracker = GLRouteTracker(route).apply { currentTargetPointIndex = 1 }
                fit(trackData.bBox)
                title = "Turn-by-Turn Navigation"
                lastLocation?.let(::updateNavigation)
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                title = "Tap map to choose destination"
                routeInfo.text = "Route failed"
                showError(error.toString())
            }
        })
    }

    override fun didUpdateLocation(location: Location) {
        val firstLocation = lastLocation == null
        lastLocation = location
        if (routeTracker == null) {
            if (firstLocation) {
                title = "Tap map to choose destination"
                routeInfo.text = "Tap map to start navigation"
            }
            return
        }
        updateNavigation(location)
    }

    private fun updateNavigation(location: Location) {
        val tracker = routeTracker ?: return
        val maneuver = tracker.updateLocation(
            location.latitude,
            location.longitude,
            if (location.hasBearing()) location.bearing else Float.NaN
        )
        if (maneuver == null) {
            maneuverIcon.visibility = android.view.View.INVISIBLE
            maneuverArrow?.isHidden = true
        } else {
            maneuverIcon.visibility = android.view.View.VISIBLE
            maneuverIcon.rotation = maneuverRotation(maneuver.type)
            maneuverIcon.contentDescription = maneuver.shortInstruction
            maneuverDistance.text = distance(tracker.distanceToNextManeuver)
            maneuverStreet.text = maneuver.shortInstruction
            maneuverArrow?.setLine(maneuver.line, maneuver.lineStartIndex)
            maneuverArrow?.isHidden = false
        }
        routeInfo.text = "${distance(tracker.remainingDistance)} remaining · ${duration(tracker.remainingDuration)}"

        val userPoint = if (tracker.isOnRoute) tracker.locationOnRoute else MapPoint(
            MapGeoPoint(location.latitude, location.longitude)
        )
        navigationAnimation?.cancel(false)
        navigationAnimation = renderer.animate { animation ->
            animation.setDuration(1.0)
            animation.setTransition(GLMapAnimation.Linear)
            routeTrack?.setProgressIndex(tracker.progressIndex)
            renderer.mapCenter = userPoint
        }
    }

    private fun distance(meters: Double) = when {
        !meters.isFinite() || meters < 0 -> "—"
        meters < 1_000 -> "${(meters / 10).roundToInt() * 10} m"
        else -> "%.1f km".format(meters / 1_000)
    }

    private fun duration(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return "—"
        val minutes = (seconds / 60).toInt()
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    private fun maneuverRotation(type: Int): Float = when (type) {
        GLRouteManeuver.Type.StartRight,
        GLRouteManeuver.Type.SlightRight,
        GLRouteManeuver.Type.RampRight,
        GLRouteManeuver.Type.ExitRight,
        GLRouteManeuver.Type.StayRight
        -> -45f

        GLRouteManeuver.Type.Right,
        GLRouteManeuver.Type.SharpRight
        -> 0f

        GLRouteManeuver.Type.StartLeft,
        GLRouteManeuver.Type.SlightLeft,
        GLRouteManeuver.Type.RampLeft,
        GLRouteManeuver.Type.ExitLeft,
        GLRouteManeuver.Type.StayLeft
        -> -135f

        GLRouteManeuver.Type.Left,
        GLRouteManeuver.Type.SharpLeft
        -> 180f

        GLRouteManeuver.Type.UturnRight -> 90f

        GLRouteManeuver.Type.UturnLeft -> -270f

        else -> -90f
    }

    override fun onDestroy() {
        cancelRequest()
        navigationAnimation?.cancel(false)
        routeTracker?.dispose()
        super.onDestroy()
    }

    private fun cancelRequest() {
        generation++
        if (requestID != 0L) GLRouteRequest.cancel(requestID)
        requestID = 0
    }
}
