package globus.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
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
    OFFLINE_DATA("Offline Data"),
}

private data class Demo(
    val title: String,
    val subtitle: String,
    val category: DemoCategory,
    val activity: Class<out Activity>,
)

class SampleSelectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "GLMap 2.0"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
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
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(8), dp(24), dp(8), dp(8))
    }

    private fun demoRow(demo: Demo) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
        setBackgroundResource(selectableItemBackground())
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(TextView(context).apply {
            text = demo.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = demo.subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            alpha = 0.65f
        })
        setOnClickListener { startActivity(Intent(context, demo.activity)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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
            Demo("Online Map", "Vector tiles, raster source, tap coordinates", DemoCategory.MAP_DISPLAY, OnlineMapActivity::class.java),
            Demo("Dark Theme", "Style options and tile reload", DemoCategory.MAP_DISPLAY, DarkThemeActivity::class.java),
            Demo("3D Terrain", "Elevation data, hillshades and pitch", DemoCategory.MAP_DISPLAY, TerrainActivity::class.java),
            Demo("Fly To", "Animated camera destinations", DemoCategory.CAMERA, FlyToActivity::class.java),
            Demo("Zoom to BBox", "Fit geometry into visible map insets", DemoCategory.CAMERA, ZoomToBBoxActivity::class.java),
            Demo("Image", "Bitmap drawable and animated position", DemoCategory.DRAW_OBJECTS, ImageActivity::class.java),
            Demo("Image Group", "Shared bitmaps for many interactive pins", DemoCategory.DRAW_OBJECTS, ImageGroupActivity::class.java),
            Demo("Markers & Clustering", "Marker layer with clustered GeoJSON", DemoCategory.DRAW_OBJECTS, MarkerClusteringActivity::class.java),
            Demo("Balloon", "Map-anchored text and background", DemoCategory.DRAW_OBJECTS, BalloonActivity::class.java),
            Demo("Track Arrows", "Route track with repeating arrow image", DemoCategory.DRAW_OBJECTS, TrackArrowsActivity::class.java),
            Demo("User Location", "Fused location rendered on the map", DemoCategory.DRAW_OBJECTS, UserLocationActivity::class.java),
            Demo("Lines & Polygons", "Programmatic vector geometry and MapCSS", DemoCategory.VECTOR_DATA, LinesPolygonsActivity::class.java),
            Demo("GeoJSON", "Background parsing, drawing and hit testing", DemoCategory.VECTOR_DATA, GeoJSONActivity::class.java),
            Demo("GPS Track", "Incremental colored track data", DemoCategory.VECTOR_DATA, GPSTrackActivity::class.java),
            Demo("Search", "The same request online or offline", DemoCategory.SEARCH, SearchActivity::class.java),
            Demo("POI Tap", "Find a rendered map object at a screen point", DemoCategory.SEARCH, POITapActivity::class.java),
            Demo("Route Building", "Online/offline auto, bike and walk routes", DemoCategory.ROUTING, RouteBuildingActivity::class.java),
            Demo("Turn-by-Turn", "Route tracker, maneuvers and progress", DemoCategory.ROUTING, TurnByTurnActivity::class.java),
            Demo("Download Maps", "Map list, progress, cancellation and deletion", DemoCategory.OFFLINE_DATA, DownloadMapsActivity::class.java),
            Demo("Download BBox", "Custom map, navigation and elevation data", DemoCategory.OFFLINE_DATA, DownloadBBoxActivity::class.java),
        )
    }
}
