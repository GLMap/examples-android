package globus.demo

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import globus.demo.base.applyContentInsets
import globus.demo.camera.FlyToActivity
import globus.demo.camera.ZoomToBBoxActivity
import globus.demo.draw.BalloonActivity
import globus.demo.draw.GPSTrackActivity
import globus.demo.draw.ImageActivity
import globus.demo.draw.ImageGroupActivity
import globus.demo.draw.MarkerClusteringActivity
import globus.demo.draw.TrackArrowsActivity
import globus.demo.draw.UserLocationActivity
import globus.demo.map.DarkThemeActivity
import globus.demo.map.OnlineMapActivity
import globus.demo.map.TerrainActivity
import globus.demo.offline.DownloadBBoxActivity
import globus.demo.offline.DownloadMapsActivity
import globus.demo.routing.RouteBuildingActivity
import globus.demo.routing.TurnByTurnActivity
import globus.demo.search.POITapActivity
import globus.demo.search.SearchActivity
import globus.demo.vector.GeoJSONActivity
import globus.demo.vector.LinesPolygonsActivity

private enum class DemoCategory(val title: String) {
    MAP_DISPLAY("Map Display"),
    CAMERA("Camera"),
    DRAW_OBJECTS("Draw Objects"),
    VECTOR_DATA("Vector Data"),
    SEARCH("Search"),
    ROUTING("Routing"),
    OFFLINE_DATA("Offline Data")
}

private data class Demo(
    val title: String,
    val subtitle: String,
    val category: DemoCategory,
    val activity: Class<out Activity>,
    val isNew: Boolean = false
)

class SampleSelectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "GLMap 2.0"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        content.addView(demoModeButton())
        DemoCategory.entries.forEach { category ->
            content.addView(categoryHeader(category.title))
            demos.filter { it.category == category }.forEach { content.addView(demoRow(it)) }
        }
        val scrollView = ScrollView(this).apply {
            isFocusableInTouchMode = true
            addView(content)
            requestFocus()
        }
        setContentView(scrollView)
        applyContentInsets(scrollView)
    }

    private fun categoryHeader(title: String) = TextView(this).apply {
        text = title.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Color.GRAY)
        setPadding(dp(8), dp(24), dp(8), dp(8))
    }

    private fun demoModeButton() = Button(this).apply {
        text = "▶  Demo Mode"
        isAllCaps = false
        textSize = 17f
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(33, 140, 245))
        setOnClickListener { startActivity(Intent(context, DemoModeActivity::class.java)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            setMargins(0, dp(4), 0, dp(4))
        }
    }

    private fun demoRow(demo: Demo) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
        setBackgroundResource(selectableItemBackground())
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(context).apply {
                        text = demo.title
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                if (demo.isNew) addView(
                    TextView(context).apply {
                        text = "NEW"
                        textSize = 11f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.rgb(255, 139, 35))
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                    }
                )
            }
        )
        addView(
            TextView(context).apply {
                text = demo.subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                alpha = 0.65f
            }
        )
        setOnClickListener { startActivity(Intent(context, demo.activity)) }
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(1)
            }
    }

    private fun selectableItemBackground(): Int {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val demos = listOf(
            Demo(
                "Online Map",
                "Vector tiles, custom raster source, tap interaction",
                DemoCategory.MAP_DISPLAY,
                OnlineMapActivity::class.java
            ),
            Demo(
                "Dark Theme",
                "GLMapStyleParser with theme options",
                DemoCategory.MAP_DISPLAY,
                DarkThemeActivity::class.java
            ),
            Demo(
                "3D Terrain",
                "Altitude scale, pitch, hillshades, elevation lines",
                DemoCategory.MAP_DISPLAY,
                TerrainActivity::class.java,
                true
            ),
            Demo("Fly To", "GLMapAnimation fly-to mode", DemoCategory.CAMERA, FlyToActivity::class.java),
            Demo(
                "Zoom to BBox",
                "Calculate zoom and animate to fit",
                DemoCategory.CAMERA,
                ZoomToBBoxActivity::class.java
            ),
            Demo(
                "Image",
                "GLMapImage — tap to place and move a pin",
                DemoCategory.DRAW_OBJECTS,
                ImageActivity::class.java
            ),
            Demo(
                "Image Group",
                "GLMapImageGroup — many pins, shared images",
                DemoCategory.DRAW_OBJECTS,
                ImageGroupActivity::class.java
            ),
            Demo(
                "Markers & Clustering",
                "GLMapMarkerLayer with clustering",
                DemoCategory.DRAW_OBJECTS,
                MarkerClusteringActivity::class.java
            ),
            Demo(
                "Balloon",
                "GLMapBalloon — text callout on tap",
                DemoCategory.DRAW_OBJECTS,
                BalloonActivity::class.java
            ),
            Demo(
                "Track Arrows",
                "GLMapTrack fill image and GLMapLineArrow",
                DemoCategory.DRAW_OBJECTS,
                TrackArrowsActivity::class.java,
                true
            ),
            Demo(
                "User Location",
                "Animated fused location on the map",
                DemoCategory.DRAW_OBJECTS,
                UserLocationActivity::class.java,
                true
            ),
            Demo(
                "Lines & Polygons",
                "GLMapVectorLayer with line and polygon",
                DemoCategory.VECTOR_DATA,
                LinesPolygonsActivity::class.java
            ),
            Demo(
                "GeoJSON",
                "Load file, display, tap to identify",
                DemoCategory.VECTOR_DATA,
                GeoJSONActivity::class.java
            ),
            Demo(
                "GPS Track",
                "GLMapTrack recording live GPS data",
                DemoCategory.VECTOR_DATA,
                GPSTrackActivity::class.java
            ),
            Demo("Search", "Online and Offline requests", DemoCategory.SEARCH, SearchActivity::class.java, true),
            Demo("POI Tap", "Tap map labels to identify objects", DemoCategory.SEARCH, POITapActivity::class.java),
            Demo(
                "Route Building",
                "GLRouteRequest online/offline",
                DemoCategory.ROUTING,
                RouteBuildingActivity::class.java
            ),
            Demo(
                "Turn-by-Turn Navigation",
                "Live location, GLRouteTracker, maneuvers",
                DemoCategory.ROUTING,
                TurnByTurnActivity::class.java
            ),
            Demo(
                "Download Maps",
                "Browse, search, and manage offline maps",
                DemoCategory.OFFLINE_DATA,
                DownloadMapsActivity::class.java
            ),
            Demo(
                "Download BBox",
                "Download map + nav + elevation for area",
                DemoCategory.OFFLINE_DATA,
                DownloadBBoxActivity::class.java
            )
        )
    }
}
