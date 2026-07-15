package globus.demo.routing

import android.app.AlertDialog
import android.graphics.Color
import android.location.Location
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import globus.demo.base.MapDemoActivity
import globus.demo.draw.LocationMapActivity
import globus.demo.R
import globus.glmap.GLMapBBox
import globus.glmap.GLMapError
import globus.glmap.GLMapLocaleSettings
import globus.glmap.GLMapTrack
import globus.glmap.GLMapVectorStyle
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import globus.glroute.GLRouteTracker
import java.nio.charset.StandardCharsets

class RouteBuildingActivity : MapDemoActivity() {
    private var departure = MapGeoPoint(41.1457, -8.6107)
    private var destination = MapGeoPoint(41.1597, -8.6300)
    private var requestID = 0L
    private var generation = 0
    private var routeTrack: GLMapTrack? = null
    private lateinit var mode: Spinner
    private lateinit var offline: SwitchCompat

    override fun onMapReady() {
        title = "Tap map to set route points"
        mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@RouteBuildingActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Auto", "Bike", "Walk"))
        }
        offline = SwitchCompat(this).apply { text = "Offline" }
        addTopView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(0xEFFFFFFF.toInt())
            addView(mode, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(offline)
        })

        val bbox = GLMapBBox().apply {
            addPoint(MapPoint(departure))
            addPoint(MapPoint(destination))
        }
        fit(bbox)
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
        addButton("Build Route") { updateRoute() }
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
                track.setData(trackData, GLMapVectorStyle.createStyle("{width:7pt;fill-image:\"track-arrow.svg\";}")!!, null)
                fit(trackData.bBox)
                title = "%.1f km · %.0f min".format(route.length / 1_000, route.duration / 60)
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                showError(error.toString())
            }
        }
        requestID = if (offline.isChecked) {
            request.startOffline(resources.openRawResource(R.raw.valhalla).use { String(it.readBytes(), StandardCharsets.UTF_8) }, callback)
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
    private val departure = MapGeoPoint(37.335055, -122.026958)
    private val destination = MapGeoPoint(37.405054, -122.156626)
    private val routeStyle = GLMapVectorStyle.createStyle("{width:14pt;fill-image:\"track-arrow.svg\";}")!!
    private val routeInfo by lazy {
        TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xD9262626.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            text = "Building route..."
        }
    }
    private var routeTrack: GLMapTrack? = null
    private var routeTracker: GLRouteTracker? = null
    private var requestID = 0L
    private var generation = 0

    override fun setupDemo() {
        title = "Turn-by-Turn"
        renderer.setMapOrigin(0.5f, 0.25f)
        addTopView(routeInfo)
        buildRoute()
    }

    private fun buildRoute() {
        val currentGeneration = ++generation
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
                routeTrack = GLMapTrack(99).apply {
                    setProgressColor(0xC8808080.toInt())
                    setData(trackData, routeStyle, null)
                    renderer.add(this)
                }
                routeTracker = GLRouteTracker(route).apply { currentTargetPointIndex = 1 }
                fit(trackData.bBox)
                routeInfo.text = "Route ready · ${distance(route.length)} · ${duration(route.duration)}"
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                routeInfo.text = "Route failed"
                showError(error.toString())
            }
        })
    }

    override fun didUpdateLocation(location: Location) {
        val tracker = routeTracker ?: return
        val maneuver = tracker.updateLocation(
            location.latitude,
            location.longitude,
            if (location.hasBearing()) location.bearing else Float.NaN,
        )
        routeTrack?.setProgressIndex(tracker.progressIndex)
        routeInfo.text = buildString {
            append(distance(tracker.distanceToNextManeuver))
            if (maneuver != null) append(" · ${maneuver.shortInstruction}")
            append("\n${distance(tracker.remainingDistance)} remaining · ${duration(tracker.remainingDuration)}")
        }
    }

    private fun distance(meters: Double) =
        when {
            !meters.isFinite() || meters < 0 -> "—"
            meters < 1_000 -> "${(meters / 10).toInt() * 10} m"
            else -> "%.1f km".format(meters / 1_000)
        }

    private fun duration(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return "—"
        val minutes = (seconds / 60).toInt()
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    override fun onDestroy() {
        generation++
        if (requestID != 0L) GLRouteRequest.cancel(requestID)
        routeTracker?.dispose()
        super.onDestroy()
    }
}
