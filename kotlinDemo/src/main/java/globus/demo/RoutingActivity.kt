package globus.demo

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import globus.glmap.*
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import java.io.IOException
import java.nio.charset.Charset

class RoutingActivity : MapViewActivity() {
    private enum class NetworkMode {
        Online, Offline
    }

    private var quickAction: QuickAction? = null
    private var routingMode = GLRoute.Mode.DRIVE
    private var networkMode = NetworkMode.Online
    private var departure = MapGeoPoint(53.844720, 27.482352)
    private var destination = MapGeoPoint(53.931935, 27.583995)
    private var track: GLMapTrack? = null

    private inline val onlineOfflineSwitch: TabLayout
        get() = findViewById(R.id.tab_layout_left)

    private inline val routeTypeSwitch: TabLayout
        get() = findViewById(R.id.tab_layout_right)

    override fun getLayoutID() = R.layout.routing

    private var valhallaConfig: String = ""
        get() {
            if (field.isEmpty()) {
                var raw: ByteArray? = null
                try {
                    // Read prepared categories
                    val stream = resources.openRawResource(R.raw.valhalla)
                    raw = ByteArray(stream.available())
                    stream.read(raw)
                    stream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                // Construct categories
                field = String(raw!!, Charset.defaultCharset())
            }
            return field
        }

    @SuppressLint("ClickableViewAccessibility")
    override fun run(test: Samples) {
        val gestureDetector = GestureDetector(
            this,
            object : SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    showDefaultPopupMenu(e.x, e.y)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {}
            }
        )
        mapView.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
        mapView.doWhenSurfaceCreated {
            val bbox = GLMapBBox()
            bbox.addPoint(MapPoint(departure))
            bbox.addPoint(MapPoint(destination))
            mapView.mapCenter = bbox.center()
            mapView.mapZoom = mapView.mapZoomForBBox(bbox, mapView.width, mapView.height) - 1
        }
        updateRoute()

        onlineOfflineSwitch.addOnTabSelectedListener(
            object : OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    when (tab.position) {
                        0 -> networkMode = NetworkMode.Online
                        1 -> networkMode = NetworkMode.Offline
                    }
                    updateRoute()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })

        routeTypeSwitch.addOnTabSelectedListener(
            object : OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    when (tab.position) {
                        0 -> routingMode = GLRoute.Mode.DRIVE
                        1 -> routingMode = GLRoute.Mode.CYCLE
                        2 -> routingMode = GLRoute.Mode.WALK
                    }
                    updateRoute()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
    }

    override fun onResume() {
        super.onResume()
        setSwitchesValues()
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {}

    private fun updateRoute() {
        val request = GLRouteRequest()
        request.addPoint(GLRoutePoint(departure, Double.NaN, true, true))
        request.addPoint(GLRoutePoint(destination, Double.NaN, true, true))
        request.locale = "en"
        request.unitSystem = GLMapView.GLUnitSystem.International
        request.mode = routingMode
        if (networkMode == NetworkMode.Offline)
            request.setOfflineWithConfig(valhallaConfig)
        request.start(object : GLRouteRequest.ResultsCallback {
            override fun onResult(route: GLRoute) {
                val trackData = route.getTrackData(Color.RED)
                var track = track
                if (track != null) {
                    track.setData(trackData)
                } else {
                    track = GLMapTrack(trackData, 5)
                    this@RoutingActivity.track = track
                    mapView.add(track)
                }
            }

            override fun onError(error: GLMapError) {
                Toast.makeText(this@RoutingActivity, error.message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setSwitchesValues() {
        onlineOfflineSwitch.getTabAt(networkMode.ordinal)?.select()
        routeTypeSwitch.getTabAt(routingMode)?.select()
    }

    private fun showDefaultPopupMenu(x: Float, y: Float) {
        quickAction?.dismiss()
        val quickAction = QuickAction(this) { actionId ->
            val mapPoint = MapPoint(x.toDouble(), y.toDouble())
            when (actionId) {
                ID_DEPARTURE -> departure = MapGeoPoint(mapView.convertDisplayToInternal(mapPoint))
                ID_DESTINATION -> destination = MapGeoPoint(mapView.convertDisplayToInternal(mapPoint))
            }
            updateRoute()
        }
        this.quickAction = quickAction
        quickAction.addActionItem("Departure", ID_DEPARTURE)
        quickAction.addActionItem("Destination", ID_DESTINATION)
        quickAction.show(mapView, x, y)
    }

    companion object {
        private const val ID_DEPARTURE = 0
        private const val ID_DESTINATION = 1
    }
}
