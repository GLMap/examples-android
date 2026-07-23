package globus.demo.map

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapBalloon
import globus.glmap.GLMapBBox
import globus.glmap.GLMapFileStorage
import globus.glmap.GLMapInfo
import globus.glmap.GLMapRasterTileSource
import globus.glmap.GLMapStorageFile
import globus.glmap.GLMapStyleParser
import globus.glmap.GLMapVectorTileSource
import globus.glmap.GLMapVectorStyle
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint

class OnlineMapActivity : MapDemoActivity() {
    private var rasterSource: OSMTileSource? = null
    private var balloon: GLMapBalloon? = null

    override fun onMapReady() {
        title = "Online Map"
        renderer.mapGeoCenter = MapGeoPoint(46.5369, 12.1356)
        renderer.mapZoom = 13.0
        renderer.drawElevationLines = true
        renderer.drawHillshades = true
        val parser = GLMapStyleParser(assets, "DefaultStyle.bundle")
        parser.setOptions(mapOf("Style" to "Outdoor", "SubStyle" to "Ski"), true)
        parser.parseFromResources()?.let {
            renderer.setStyle(it)
            renderer.reloadTiles()
        } ?: showError("Cannot parse outdoor map style")

        val button = addButton("OSM Raster") {}
        button.setOnClickListener { toggleSource(button) }
        setGestures(onTap = { touch ->
            val position = renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble())
            val point = MapGeoPoint(position)
            val current = balloon ?: GLMapBalloon(10).also {
                it.setBackgroundBitmap(createBalloonBackground(), Rect(dp(20), dp(20), dp(20), dp(20)))
                renderer.add(it)
                balloon = it
            }
            current.setText(
                "%.4f, %.4f".format(point.lat, point.lon),
                GLMapVectorStyle.createStyle("{text-color:#2C3E50;font-size:14;}")!!,
                Rect(dp(12), dp(8), dp(12), dp(8)),
                null,
            )
            current.position = position
        })
    }

    private fun toggleSource(button: Button) {
        if (rasterSource == null) {
            rasterSource = OSMTileSource(this)
            renderer.setBase(rasterSource!!)
            renderer.drawElevationLines = false
            renderer.drawHillshades = false
            button.text = "GLMap Vector"
        } else {
            renderer.setBase(GLMapVectorTileSource())
            renderer.drawElevationLines = true
            renderer.drawHillshades = true
            rasterSource = null
            button.text = "OSM Raster"
        }
    }
}

class DarkThemeActivity : MapDemoActivity() {
    private var dark = true

    override fun onMapReady() {
        title = "Dark Theme"
        renderer.mapGeoCenter = MapGeoPoint(45.4371, 12.3326)
        renderer.mapZoom = 14.0
        applyTheme()

        val button = addButton("Light") {}
        button.setOnClickListener {
            dark = !dark
            applyTheme()
            button.text = if (dark) "Light" else "Dark"
        }
    }

    private fun applyTheme() {
        val parser = GLMapStyleParser(assets, "DefaultStyle.bundle")
        parser.setOptions(if (dark) mapOf("Theme" to "Dark") else emptyMap(), true)
        val style = parser.parseFromResources() ?: return showError("Cannot parse default map style")
        renderer.setStyle(style)
        renderer.reloadTiles()
    }
}

class TerrainActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "3D Terrain"
        val bbox = GLMapBBox().apply {
            addPoint(MapPoint.CreateFromGeoCoordinates(45.85, 6.75))
            addPoint(MapPoint.CreateFromGeoCoordinates(46.05, 7.05))
        }
        setVisibleMapInsets(0, 0, 0, 0)
        renderer.doWhenSurfaceCreated {
            renderer.mapCenter = bbox.center()
            renderer.mapZoom = renderer.mapZoomForBBox(bbox) + 1 // Same as doubling mapScale on iOS.
        }
        renderer.mapPitch = 45f
        renderer.altitudeScale = 1f
        renderer.drawHillshades = true
        renderer.drawElevationLines = true
        setupControls()

        downloadBBoxData(
            bbox,
            listOf(
                GLMapInfo.DataSet.MAP to "terrain_map.vmtar",
                GLMapInfo.DataSet.ELEVATION to "terrain_ele.eletar",
            ),
        ) { error ->
            if (error != null) showError(error) else renderer.reloadTiles()
        }
    }

    private fun setupControls() {
        val altitudeLabel = TextView(this).apply { text = "Altitude Scale: 1.0" }
        val altitudeSlider = SeekBar(this).apply {
            max = 30
            progress = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val scale = progress / 10f
                    renderer.altitudeScale = scale
                    altitudeLabel.text = "Altitude Scale: %.1f".format(scale)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }

        val hillshades = Button(this).apply {
            text = "Hillshades: ON"
            isAllCaps = false
            setOnClickListener {
                renderer.drawHillshades = !renderer.drawHillshades
                text = "Hillshades: ${if (renderer.drawHillshades) "ON" else "OFF"}"
            }
        }
        val elevation = Button(this).apply {
            text = "Elevation Lines: ON"
            isAllCaps = false
            setOnClickListener {
                renderer.drawElevationLines = !renderer.drawElevationLines
                text = "Elevation Lines: ${if (renderer.drawElevationLines) "ON" else "OFF"}"
            }
        }
        val slopes = Button(this).apply {
            text = "Slopes: OFF"
            isAllCaps = false
            setOnClickListener {
                renderer.drawSlopes = !renderer.drawSlopes
                text = "Slopes: ${if (renderer.drawSlopes) "ON" else "OFF"}"
            }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hillshades, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(elevation, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(slopes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 255, 255, 255))
                cornerRadius = dp(12).toFloat()
            }
            addView(altitudeLabel)
            addView(altitudeSlider)
            addView(buttons)
        }
        container.addView(
            controls,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(16), 0, dp(16), dp(16))
            },
        )
    }
}

private class OSMTileSource(activity: Activity) : GLMapRasterTileSource(cacheStorage(activity)) {
    private val mirrors = arrayOf(
        "https://a.tile.openstreetmap.org/%d/%d/%d.png",
        "https://b.tile.openstreetmap.org/%d/%d/%d.png",
        "https://c.tile.openstreetmap.org/%d/%d/%d.png",
    )

    init {
        setValidZoomMask((1 shl 20) - 1)
        setAttributionText("© OpenStreetMap contributors")
    }

    override fun urlForTilePos(x: Int, y: Int, z: Int): String =
        mirrors[Math.floorMod(x + y, mirrors.size)].format(z, x, y)

    companion object {
        private fun cacheStorage(activity: Activity): GLMapStorageFile? =
            GLMapFileStorage(activity.filesDir).findStorage("RasterCache", true)?.findFile("osm.db", true)
    }
}
